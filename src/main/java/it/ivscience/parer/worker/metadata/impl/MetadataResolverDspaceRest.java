package it.ivscience.parer.worker.metadata.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.ivscience.parer.worker.metadata.MetadataResolutionException;
import it.ivscience.parer.worker.metadata.MetadataResolver;
import it.ivscience.parer.worker.sip.Metadata;
import it.ivscience.parer.worker.sip.SipIndexContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real implementation of {@link MetadataResolver} via DSpace REST API v7.
 * Active with Spring profile "dspace".
 *
 * Primary call:
 *   GET {dspaceBaseUrl}/server/api/pid/find?id={handle-or-id}
 *   → 302 redirect → item endpoint → 200 with item JSON (HttpClient follows redirect automatically)
 *   → 404 if identifier not found in DSpace (caller catches MetadataResolutionException)
 *
 * Secondary call (owning collection):
 *   GET {_links.owningCollection.href}
 *
 * Wiring XML: metadata-services.xml.
 */
public class MetadataResolverDspaceRest implements MetadataResolver {

    private static final Logger log = LogManager.getLogger(MetadataResolverDspaceRest.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private String dspaceBaseUrl;
    private String apiToken;
    private String access;
    private String sourceSystem;
    private String registryType;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public void setDspaceBaseUrl(String v)       { this.dspaceBaseUrl = v; }
    public void setApiToken(String v)            { this.apiToken = v; }
    public void setAccess(String v)              { this.access = v; }
    public void setSourceSystem(String v)        { this.sourceSystem = v; }
    public void setRegistryType(String v)        { this.registryType = v; }

    // -------------------------------------------------------------------------

    @Override
    public SipIndexContext resolve(String sourceObjectId) {
        return fetchFromDSpace(sourceObjectId);
    }

    // -------------------------------------------------------------------------

    private SipIndexContext fetchFromDSpace(String sourceObjectId) {
        // S3 keys encode the handle slash as underscore: 20.500.14008_80992 → 20.500.14008/80992
        String handle = sourceObjectId.replace('_', '/');
        log.info("Resolving DSpace REST metadata for handle={}", handle);
        log.debug("Fetching DSpace item id={} via pid/find from {}", handle, dspaceBaseUrl);
        try {
            String encoded = URLEncoder.encode(handle, StandardCharsets.UTF_8);
            JsonNode item  = get(dspaceBaseUrl + "/server/api/pid/find?id=" + encoded);
            JsonNode meta = item.path("metadata");

            SipIndexContext ctx = new SipIndexContext();
            ctx.add(new Metadata("sourceSystem",        sourceSystem));
            ctx.add(new Metadata("registryType",        registryType));

            ctx.add(new Metadata("handle", handle));
            ctx.add(new Metadata("number", sourceObjectId));

            // Title / Subject
            String title = firstValue(meta, "dc.title");
            ctx.add(new Metadata("title",   title != null ? title : ""));
            ctx.add(new Metadata("subject", title != null ? title : ""));

            // Date issued — DSpace formats: "2023", "2023-06", "2023-06-15"
            String date = firstValue(meta, "dc.date.issued");
            ctx.add(new Metadata("date", adaptDate(date)));
            // <Chiave>/<Anno> is a source-system code: 2000 = DSpace, 2001 = non-DSpace
            ctx.add(new Metadata("year", "2000"));

            // Document type (prefer COAR vocabulary)
            String docType = firstValue(meta, "coar.type");
            if (docType == null) docType = firstValue(meta, "dc.type");
            ctx.add(new Metadata("documentType", docType != null ? docType : ""));

            // COAR resource type voice (es. "Resource Types::text::conference output::...")
            String coar = firstValue(meta, "dc.type.coar");
            ctx.add(new Metadata("coar", coar != null ? coar : ""));

            // Owning collection
            String collHref = item.path("_links").path("owningCollection").path("href").asText(null);
            if (collHref != null) {
                try {
                    // GET collection metadata
                    JsonNode coll = get(collHref);
                    ctx.add(new Metadata("collectionId", coll.path("handle").asText("")));
                    String collName = firstValue(coll.path("metadata"), "dc.title");
                    ctx.add(new Metadata("collectionDescription", collName != null ? collName : ""));
                } catch (Exception e) {
                    log.warn("Could not fetch owningCollection for item={}: {}", handle, e.getMessage());
                    ctx.add(new Metadata("collectionId",          ""));
                    ctx.add(new Metadata("collectionDescription", ""));
                }
            } else {
                ctx.add(new Metadata("collectionId",          ""));
                ctx.add(new Metadata("collectionDescription", ""));
            }

            String dataProvider = firstValue(meta, "edm.dataprovider");
            ctx.add(new Metadata("dataProvider", dataProvider));

            ctx.setMetadataXml(buildMetadataXml(meta));

            log.debug("Resolved metadata for item with title='{}' handle={}", title, handle);
            return ctx;

        } catch (MetadataResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MetadataResolutionException(
                    "DSpace REST call failed for item=" + handle + ": " + e.getMessage(), e);
        }
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.ACCEPT, "application/json")
                .header("X-XSRF-TOKEN", UUID.randomUUID().toString())
                .header("X-CORRELATION-ID", UUID.randomUUID().toString());
        if (StringUtils.isNotBlank(apiToken)) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
        }
        if (StringUtils.isNotBlank(access)) {
            req.header("Access", access);
        }
        HttpResponse<String> resp = http.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < HttpStatus.SC_OK || resp.statusCode() >= HttpStatus.SC_MULTIPLE_CHOICES) {
            throw new MetadataResolutionException(
                    "DSpace HTTP " + resp.statusCode() + " GET " + url);
        }
        return JSON.readTree(resp.body());
    }

    /**
     * Converts the DSpace metadata JSON node to Dublin Core XML (docs/metadata.xml format).
     * Keys: {schema}.{element}[.{qualifier}]
     * Groups entries by schema into separate <dublin_core> blocks.
     */
    private static String buildMetadataXml(JsonNode metadata) {
        Map<String, List<String[]>> bySchema = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = metadata.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            int firstDot = key.indexOf('.');
            if (firstDot < 0) continue;
            String schema    = key.substring(0, firstDot);
            String rest      = key.substring(firstDot + 1);
            int secondDot    = rest.indexOf('.');
            String element   = secondDot > 0 ? rest.substring(0, secondDot) : rest;
            String qualifier = secondDot > 0 ? rest.substring(secondDot + 1) : "none";
            JsonNode values  = entry.getValue();
            if (!values.isArray()) continue;
            for (JsonNode v : values) {
                String value = v.path("value").asText("");
                String lang  = v.path("language").isNull() ? null : v.path("language").asText(null);
                bySchema.computeIfAbsent(schema, k -> new ArrayList<>())
                        .add(new String[]{element, qualifier, value, lang});
            }
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElement("metadata");
            doc.appendChild(root);

            bySchema.forEach((schema, entries) -> {
                Element dublinCore = doc.createElement("dublin_core");
                dublinCore.setAttribute("schema", schema);
                for (String[] e : entries) {
                    Element dcvalue = doc.createElement("dcvalue");
                    dcvalue.setAttribute("element",   e[0]);
                    dcvalue.setAttribute("qualifier", e[1]);
                    if (e[3] != null) dcvalue.setAttribute("language", e[3]);
                    dcvalue.setTextContent(e[2]);
                    dublinCore.appendChild(dcvalue);
                }
                root.appendChild(dublinCore);
            });

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter sw = new StringWriter();
            tf.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build metadata XML", e);
        }
    }

    private static String firstValue(JsonNode metadata, String key) {
        JsonNode arr = metadata.path(key);
        if (arr.isArray() && !arr.isEmpty()) {
            return arr.get(0).path("value").asText(null);
        }
        return null;
    }

    private static String adaptDate(String date) {
        if (StringUtils.isBlank(date)) {
            return date;
        }

        if (date.length() == 4) {
            return date + "-01-01";
        }
        if (date.length() == 7) {
            return date + "-01";
        }

        return date;
    }

}
