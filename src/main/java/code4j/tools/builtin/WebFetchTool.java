package code4j.tools.builtin;

import code4j.tools.api.Tool;
import code4j.tools.api.ToolContext;
import code4j.tools.api.ValidationResult;
import code4j.tools.metadata.ToolCapability;
import code4j.tools.metadata.ToolMetadata;
import code4j.tools.metadata.ToolOrigin;
import code4j.tools.metadata.ToolStatus;
import code4j.tools.result.ToolResult;
import code4j.tools.validation.ToolInputValidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

public final class WebFetchTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata(
            "web_fetch",
            "Fetch content from a URL and return it as plain text.",
            INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.READ), ToolStatus.AVAILABLE);
    private static final int DEFAULT_MAX_CHARS = 10_000;
    private static final int MAX_MAX_CHARS = 100_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;

    public WebFetchTool() {
        this(HttpClient.newHttpClient());
    }

    public WebFetchTool(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override
    public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input)
                .requiredString("url")
                .optionalInteger("maxChars", 1, MAX_MAX_CHARS)
                .custom((rawInput, builder) -> {
                    JsonNode urlNode = rawInput != null && rawInput.isObject() ? rawInput.get("url") : null;
                    if (urlNode != null && urlNode.isTextual()) {
                        String url = urlNode.asText().trim();
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            builder.addError("url must start with http:// or https://");
                        }
                    }
                })
                .build();
    }

    @Override
    public ToolResult run(JsonNode normalizedInput, ToolContext toolContext) {
        String url = normalizedInput.get("url").asText().trim();
        int maxChars = normalizedInput.has("maxChars") ? normalizedInput.get("maxChars").asInt() : DEFAULT_MAX_CHARS;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Code4j/0.1")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ToolResult.error("Fetch returned HTTP " + response.statusCode() + " for URL: " + url);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.contains("text/html") || contentType.contains("text/plain") || contentType.isEmpty()) {
                String text = htmlToText(response.body());
                if (text.length() > maxChars) {
                    text = text.substring(0, maxChars) + "\n\n... (truncated, " + (text.length() - maxChars) + " more chars)";
                }
                return ToolResult.ok("URL: " + url + "\nCHARS: " + Math.min(text.length(), maxChars) + "\n\n" + text);
            }
            String body = response.body();
            if (body.length() > maxChars) {
                body = body.substring(0, maxChars) + "\n\n... (truncated)";
            }
            return ToolResult.ok("URL: " + url + "\nCONTENT-TYPE: " + contentType + "\nCHARS: " + Math.min(body.length(), maxChars) + "\n\n" + body);
        } catch (IOException e) {
            return ToolResult.error("Fetch failed for " + url + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Fetch interrupted");
        }
    }

    static String htmlToText(String html) {
        String text = html;
        text = text.replaceAll("(?is)<head[^>]*>.*?</head>", "");
        text = text.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        text = text.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        text = text.replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ");
        text = text.replaceAll("<br\\s*/?>", "\n");
        text = text.replaceAll("</?p[^>]*>", "\n");
        text = text.replaceAll("</?div[^>]*>", "\n");
        text = text.replaceAll("</?li[^>]*>", "\n");
        text = text.replaceAll("<[^>]+>", "");
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").replace("&nbsp;", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.replaceAll(" {2,}", " ");
        return text.trim();
    }

    private static ObjectNode createSchema() {
        ObjectNode s = JSON.objectNode();
        s.put("type", "object");
        var p = s.putObject("properties");
        p.putObject("url").put("type", "string").put("description", "URL to fetch (must start with http:// or https://).");
        p.putObject("maxChars").put("type", "integer").put("minimum", 1).put("maximum", MAX_MAX_CHARS)
                .put("description", "Max characters to return (default " + DEFAULT_MAX_CHARS + ").");
        s.putArray("required").add("url");
        return s;
    }
}
