package it.ivscience.parer.worker;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.model.WorkUnitDescriptor;
import it.ivscience.parer.worker.s3.S3Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Batch one-shot runner driven by CLI arguments.
 *
 * Invocation:
 *   java -jar parer-worker.jar --s3-bucket=&lt;bucket&gt; --s3-key=parer/esecuzioni/YYYY/MM/DD/&lt;id&gt;.process
 *
 * The .process file is a trigger — its content is ignored. The SIP key is derived
 * by stripping the prefix "parer/esecuzioni/" and the suffix ".process" from --s3-key.
 * The objectId is the last path segment of the derived SIP key.
 *
 * Exit codes:
 *   0 — success or non-recoverable functional error (no retry needed)
 *   1 — transient error (caller must retry)
 *
 * Wiring XML: worker-services.xml.
 */
public class BatchRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LogManager.getLogger(BatchRunner.class);
    private static final DateTimeFormatter LOG_TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

    @Value("${parer.batch.key-prefix:parer/esecuzioni/}")
    private String keyPrefix;

    @Value("${parer.batch.key-suffix:.process}")
    private String keySuffix;

    @Value("${parer.batch.log-prefix:parer/log/}")
    private String logPrefix;

    @Value("${LOG_FILE:/tmp/parer.log}")
    private String logFile;

    @Autowired
    private SipPipeline sipPipeline;

    @Autowired
    private S3Service s3Service;

    private volatile int exitCode = 0;

    // ── ApplicationRunner ─────────────────────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
        exitCode = runBatch(args);
    }

    // ── ExitCodeGenerator ─────────────────────────────────────────────────────

    @Override
    public int getExitCode() {
        return exitCode;
    }

    // ── visible for tests ─────────────────────────────────────────────────────

    public int runBatch(ApplicationArguments args) {
        String bucket = singleOption(args, "s3-bucket");
        String key    = singleOption(args, "s3-key");

        if (bucket == null || bucket.isBlank() || key == null || key.isBlank()) {
            log.error("Missing required CLI args --s3-bucket and --s3-key");
            return 0;
        }

        String decodedKey;
        try {
            decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.error("Invalid URL-encoded --s3-key: {}", key, e);
            return 0;
        }

        if (!decodedKey.startsWith(keyPrefix) || !decodedKey.endsWith(keySuffix)) {
            log.error("--s3-key must start with '{}' and end with '{}', got: {}", keyPrefix, keySuffix, decodedKey);
            return 0;
        }

        String sipKey  = decodedKey.substring(keyPrefix.length(), decodedKey.length() - keySuffix.length());
        // Last path segment, no dot-stripping (extension already removed above).
        String objectId = sipKey.contains("/") ? sipKey.substring(sipKey.lastIndexOf('/') + 1) : sipKey;
        WorkUnitDescriptor descriptor = new WorkUnitDescriptor(objectId, bucket, sipKey);
        log.info("Starting batch: {}", descriptor);

        try {
            sipPipeline.process(descriptor);
            log.info("Batch completed with SUCCESS for key={}", decodedKey);
            return 0;
        } catch (FunctionalException e) {
            log.error("Functional error — no retry: {}", e.getMessage(), e);
            return 0;
        } catch (TransientException e) {
            log.error("Transient error — retry required: {}", e.getMessage(), e);
            return 1;
        } catch (Exception e) {
            log.error("Unexpected error — treating as functional: {}", e.getMessage(), e);
            return 0;
        } finally {
            // Flush appenders and upload the full execution log to S3.
            LogManager.shutdown();
            s3Service.writeFile(bucket, buildLogKey(objectId), logFile);
        }
    }

    static String baseName(String key) {
        String s = key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
        String name = s.contains("/") ? s.substring(s.lastIndexOf('/') + 1) : s;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String singleOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) return null;
        if (values.size() > 1) {
            log.warn("Multiple values for --{}, using first", name);
        }
        return values.getFirst();
    }

    private String buildLogKey(String objectId) {
        String timestamp = LOG_TS_FORMAT.format(Instant.now());
        return logPrefix + objectId + "-" + timestamp + ".log";
    }
}
