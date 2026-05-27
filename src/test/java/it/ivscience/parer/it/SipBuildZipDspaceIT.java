package it.ivscience.parer.it;

import com.sun.net.httpserver.HttpServer;
import it.ivscience.parer.MainApplication;
import it.ivscience.parer.content.service.SipFileService;
import it.ivscience.parer.content.service.SipPackageService;
import it.ivscience.parer.worker.BatchRunner;
import it.ivscience.parer.worker.metadata.MetadataResolver;
import it.ivscience.parer.worker.metadata.impl.MetadataResolverDspaceRest;
import it.ivscience.parer.worker.s3.S3Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: SipBuildStep (with real MetadataResolverDspaceRest) + ZipToS3Step.
 *
 * Stubs DSpace REST API with an embedded JDK HttpServer so we can verify that
 * every outbound request carries:
 *   - Authorization: Bearer <configured token>
 *   - x-xsrf-token:    random UUID
 *   - x-correlation-id: random UUID (distinct from x-xsrf-token)
 *
 * S3, ZIP, and DB are mocked via Mockito (@MockBean) and H2 in-memory.
 *
 * The real MetadataResolverDspaceRest bean is registered via @TestConfiguration
 * with @Primary so it overrides the @MockBean MetadataResolver.
 */
@SpringBootTest(classes = MainApplication.class)
@ActiveProfiles("test")
@Import(SipBuildZipDspaceIT.DspaceTestConfig.class)
class SipBuildZipDspaceIT {

    // ── DSpace stub (started before Spring context via static initializer) ──────

    static final String DSPACE_TOKEN = "test-dspace-bearer-token-xyz";

    static final CopyOnWriteArrayList<com.sun.net.httpserver.Headers> CAPTURED_ITEM_HEADERS =
            new CopyOnWriteArrayList<>();

    static final HttpServer  DSPACE_STUB;
    static final int         DSPACE_PORT;
    static final String      DSPACE_COLL_JSON;

    static {
        try {
            DSPACE_STUB = HttpServer.create(new InetSocketAddress(0), 0);
            DSPACE_STUB.setExecutor(Executors.newCachedThreadPool());
            DSPACE_STUB.start();
            DSPACE_PORT = DSPACE_STUB.getAddress().getPort();

            DSPACE_COLL_JSON = "{\"uuid\":\"coll-01\","
                    + "\"metadata\":{\"dc.title\":[{\"value\":\"Test Collection\"}]}}";

            String itemJson = "{\"handle\":\"123456789/1000\","
                    + "\"metadata\":{"
                    + "\"dc.title\":[{\"value\":\"Test Thesis on Topic A\"}],"
                    + "\"dc.date.issued\":[{\"value\":\"2023-05-01\"}],"
                    + "\"dc.contributor.author\":[{\"value\":\"Rossi, Mario\"}],"
                    + "\"dc.type\":[{\"value\":\"Thesis\"}],"
                    + "\"dc.type.coar\":[{\"value\":\"Resource Types::text::thesis\"}]},"
                    + "\"_links\":{\"owningCollection\":{\"href\":"
                    + "\"http://localhost:" + DSPACE_PORT + "/server/api/core/collections/coll-01\"}}}";

            // pid/find endpoint — resolves handle to item; captures headers for assertion.
            // Returns 200 directly (equivalent to the 302→200 production flow, since the
            // HttpClient is configured with followRedirects=NORMAL and the stub skips the redirect).
            DSPACE_STUB.createContext("/server/api/pid/find", exchange -> {
                CAPTURED_ITEM_HEADERS.add(exchange.getRequestHeaders());
                byte[] body = itemJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });

            // Collection endpoint (called for _links.owningCollection.href)
            DSPACE_STUB.createContext("/server/api/core/collections/", exchange -> {
                byte[] body = DSPACE_COLL_JSON.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });

        } catch (IOException e) {
            throw new RuntimeException("Cannot start DSpace stub server", e);
        }
    }

    // ── TestConfiguration: real MetadataResolverDspaceRest pointing at stub ────

    @TestConfiguration
    static class DspaceTestConfig {
        @Bean
        @Primary
        public MetadataResolver dspaceRestResolver() {
            MetadataResolverDspaceRest r = new MetadataResolverDspaceRest();
            r.setDspaceBaseUrl("http://localhost:" + DSPACE_PORT);
            r.setApiToken(DSPACE_TOKEN);
            r.setSourceSystem("dspace");
            r.setRegistryType("AMS_HISTORICA");
            return r;
        }
    }

    // ── Spring beans ──────────────────────────────────────────────────────────

    @MockBean protected S3Client             s3Client;
    @MockBean protected SecretsManagerClient secretsManagerClient;
    @SpyBean  protected S3Service s3Service;

    @Autowired protected BatchRunner        batchRunner;
    @Autowired protected SipPackageService  sipPackageService;
    @Autowired protected SipFileService     sipFileService;

    @BeforeEach
    void clearCaptured() {
        CAPTURED_ITEM_HEADERS.clear();
    }

    @AfterAll
    static void stopDspaceStub() {
        DSPACE_STUB.stop(0);
    }

    // ── Test ──────────────────────────────────────────────────────────────────

    /**
     * Full pipeline run: SipBuildStep resolves metadata via real DSpaceRest call
     * to the stub server, then ZipToS3Step uploads the ZIP (mocked).
     *
     * Assertions:
     *  1. Pipeline reaches SUCCESS
     *  2. DSpace /items/ endpoint was called
     *  3. Authorization header carries the configured Bearer token
     *  4. x-xsrf-token is a valid UUID
     *  5. x-correlation-id is a valid UUID
     *  6. x-xsrf-token != x-correlation-id (both independently random)
     */
    @Test
    void sipBuildZipWithDspaceMetadata_verifiesHeadersAndReachesSuccess() {
        String sipId = "SIP-DSPACE-" + UUID.randomUUID();

        int exit = batchRunner.runBatch(new DefaultApplicationArguments(
                "--s3-bucket=test-bucket",
                "--s3-key=parer/esecuzioni/test/" + sipId + ".process"));

        // ── pipeline outcome ──────────────────────────────────────────────────
        assertThat(exit).as("exit code").isEqualTo(0);

        var pkg = sipPackageService.findBySipId(sipId);
        assertThat(pkg).as("SipPackage must exist").isPresent();
        assertThat(pkg.get().getStatus())
                .as("pipeline must reach SUCCESS")
                .isEqualTo("SUCCESS");

        assertThat(sipFileService.findBySipPackage(pkg.get()))
                .as("SipFile records must be persisted")
                .isNotEmpty();

        // ── DSpace HTTP call headers ──────────────────────────────────────────
        assertThat(CAPTURED_ITEM_HEADERS)
                .as("DSpace /items/ endpoint must be called at least once")
                .isNotEmpty();

        com.sun.net.httpserver.Headers h = CAPTURED_ITEM_HEADERS.get(0);

        assertThat(h.getFirst("Authorization"))
                .as("Authorization header must carry the configured Bearer token")
                .isEqualTo("Bearer " + DSPACE_TOKEN);

        String xsrfToken      = h.getFirst("x-xsrf-token");
        String correlationId  = h.getFirst("x-correlation-id");
        String uuidPattern    = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

        assertThat(xsrfToken)
                .as("x-xsrf-token must be a valid UUID v4")
                .matches(uuidPattern);

        assertThat(correlationId)
                .as("x-correlation-id must be a valid UUID v4")
                .matches(uuidPattern);

        assertThat(xsrfToken)
                .as("x-xsrf-token and x-correlation-id must be independently random (distinct)")
                .isNotEqualTo(correlationId);
    }
}
