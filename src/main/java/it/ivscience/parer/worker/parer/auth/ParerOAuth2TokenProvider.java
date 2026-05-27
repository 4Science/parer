package it.ivscience.parer.worker.parer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Provides a valid OAuth2 Bearer token for ParER SOAP calls.
 *
 * Flow:
 *  1. Reads client_id and client_secret from AWS Secrets Manager (JSON secret).
 *  2. POSTs to tokenEndpointUrl with grant_type=client_credentials.
 *  3. Caches the token and refreshes it 60 seconds before expiry.
 *
 * Wiring XML: parer-client-services.xml (profile "!mock").
 */
public class ParerOAuth2TokenProvider {

    private static final Logger log = LogManager.getLogger(ParerOAuth2TokenProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int EXPIRY_BUFFER_SECONDS = 60;

    private SecretsManagerClient secretsManagerClient;
    private String secretName;
    private String tokenEndpointUrl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // cached token state
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public void setSecretsManagerClient(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public void setTokenEndpointUrl(String tokenEndpointUrl) {
        this.tokenEndpointUrl = tokenEndpointUrl;
    }

    // -------------------------------------------------------------------------

    /**
     * Returns a valid Bearer token. Refreshes automatically if near expiry.
     */
    public String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS))) {
            return cachedToken;
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        // double-checked: another thread may have refreshed while we waited
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS))) {
            return cachedToken;
        }
        log.info("Refreshing ParER OAuth2 token from secretName={}", secretName);

        String[] credentials = readCredentials();
        String clientId     = credentials[0];
        String clientSecret = credentials[1];

        try {
            String body = "grant_type=client_credentials"
                    + "&client_id="     + URLEncoder.encode(clientId,     StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpointUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < HttpStatus.SC_OK || resp.statusCode() >= HttpStatus.SC_MULTIPLE_CHOICES) {
                throw new TokenRefreshException(
                        "Token endpoint HTTP " + resp.statusCode() + " from " + tokenEndpointUrl);
            }

            JsonNode tokenJson = JSON.readTree(resp.body());
            String token = tokenJson.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new TokenRefreshException("Token endpoint response missing access_token: " + resp.body());
            }

            int expiresIn = tokenJson.path("expires_in").asInt(3600);
            cachedToken     = token;
            tokenExpiresAt  = Instant.now().plusSeconds(expiresIn);

            log.info("ParER OAuth2 token refreshed, expires in {}s", expiresIn);
            return cachedToken;

        } catch (TokenRefreshException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenRefreshException("Failed to refresh ParER OAuth2 token: " + e.getMessage(), e);
        }
    }

    private String[] readCredentials() {
        try {
            String secretJson = secretsManagerClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretName).build()
            ).secretString();
            JsonNode node = JSON.readTree(secretJson);
            String clientId     = node.path("client_id").asText(null);
            String clientSecret = node.path("client_secret").asText(null);
            if (clientId == null || clientSecret == null) {
                throw new TokenRefreshException(
                        "Secret '" + secretName + "' is missing client_id or client_secret fields");
            }
            return new String[]{ clientId, clientSecret };
        } catch (TokenRefreshException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenRefreshException(
                    "Failed to read credentials from Secrets Manager secret=" + secretName + ": " + e.getMessage(), e);
        }
    }
}
