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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebSearchTool implements Tool {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final ObjectNode INPUT_SCHEMA = createSchema();
    private static final ToolMetadata METADATA = new ToolMetadata(
            "web_search",
            "Search the web and return formatted results with titles, snippets, and URLs.",
            INPUT_SCHEMA, ToolOrigin.BUILTIN, Set.of(ToolCapability.READ), ToolStatus.AVAILABLE);
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern RESULT_PATTERN = Pattern.compile(
            "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>.*?<a[^>]*class=\"result__snippet\"[^>]*>([^<]+)</a>",
            Pattern.DOTALL);

    private final HttpClient httpClient;

    public WebSearchTool() {
        this(HttpClient.newHttpClient());
    }

    public WebSearchTool(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override public ToolMetadata metadata() { return METADATA; }
    @Override public JsonNode inputSchema() { return INPUT_SCHEMA; }

    @Override
    public ValidationResult validateInput(JsonNode input) {
        return ToolInputValidation.object(input)
                .requiredString("query")
                .optionalInteger("limit", 1, MAX_LIMIT)
                .build();
    }

    @Override
    public ToolResult run(JsonNode normalizedInput, ToolContext toolContext) {
        String query = normalizedInput.get("query").asText();
        int limit = normalizedInput.has("limit") ? normalizedInput.get("limit").asInt() : DEFAULT_LIMIT;
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create("https://html.duckduckgo.com/html/?q=" + encoded);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Code4j/0.1")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ToolResult.error("Search returned HTTP " + response.statusCode());
            }
            List<SearchResult> results = parseResults(response.body(), limit);
            if (results.isEmpty()) {
                return ToolResult.ok("No results found for: " + query);
            }
            StringBuilder sb = new StringBuilder("Search results for: ").append(query).append("\n\n");
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                sb.append(i + 1).append(". ").append(r.title).append("\n");
                sb.append("   URL: ").append(r.url).append("\n");
                sb.append("   ").append(r.snippet).append("\n\n");
            }
            return ToolResult.ok(sb.toString().trim());
        } catch (IOException e) {
            return ToolResult.error("Search failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Search interrupted");
        }
    }

    private List<SearchResult> parseResults(String html, int limit) {
        List<SearchResult> results = new ArrayList<>();
        Matcher m = RESULT_PATTERN.matcher(html);
        while (m.find() && results.size() < limit) {
            String url = decodeHtml(m.group(1).trim());
            String title = decodeHtml(m.group(2).trim());
            String snippet = decodeHtml(m.group(3).trim());
            if (!title.isEmpty() && !url.isEmpty()) {
                results.add(new SearchResult(title, url, snippet));
            }
        }
        if (results.isEmpty()) {
            Matcher linkMatcher = Pattern.compile(
                    "<a[^>]*href=\"(https?://[^\"]+)\"[^>]*class=\"[^\"]*result[^\"]*\"[^>]*>([^<]+)</a>",
                    Pattern.DOTALL).matcher(html);
            while (linkMatcher.find() && results.size() < limit) {
                String url = decodeHtml(linkMatcher.group(1).trim());
                String title = decodeHtml(linkMatcher.group(2).trim());
                if (!title.isEmpty() && !url.isEmpty() && !url.contains("duckduckgo.com")) {
                    results.add(new SearchResult(title, url, ""));
                }
            }
        }
        return results;
    }

    private static String decodeHtml(String text) {
        return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").replace("&nbsp;", " ")
                .replaceAll("<[^>]+>", "").trim();
    }

    private record SearchResult(String title, String url, String snippet) {}

    private static ObjectNode createSchema() {
        ObjectNode s = JSON.objectNode();
        s.put("type", "object");
        var p = s.putObject("properties");
        p.putObject("query").put("type", "string").put("description", "The search query.");
        p.putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT)
                .put("description", "Max results (default " + DEFAULT_LIMIT + ").");
        s.putArray("required").add("query");
        return s;
    }
}
