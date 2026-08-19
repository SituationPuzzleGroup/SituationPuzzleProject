package com.situationpuzzle.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.situationpuzzle.config.LlmProperties;
import com.situationpuzzle.service.game.ChatTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Component
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final String NO_THINK_HINT =
            "\n\n請直接用繁體中文回答，不要輸出思考過程、推理步驟或 <think> 標籤。";

    private final LlmProperties props;
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private final HttpClient httpClient;

    public LlmClient(LlmProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(trimSlash(props.getBaseUrl()))
                .requestFactory(factory)
                .build();
    }

    public boolean isAvailable() {
        return props.isConfigured();
    }

    public Optional<String> chat(String systemPrompt, List<ChatTurn> history, String userMessage) {
        StringBuilder sb = new StringBuilder();
        boolean ok = streamChat(systemPrompt, history, userMessage, sb::append);
        if (!ok || sb.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sb.toString().trim());
    }

    public boolean streamChat(
            String systemPrompt,
            List<ChatTurn> history,
            String userMessage,
            Consumer<String> onToken) {
        if (!isAvailable()) {
            return false;
        }
        LlmProperties.Profile profile = props.resolveProfile(props.getDefaultProfile());
        if (profile == null || profile.getModel() == null || profile.getModel().isBlank()) {
            log.warn("LLM profile missing model");
            return false;
        }

        try {
            ObjectNode body = buildRequestBody(profile, systemPrompt, history, userMessage, true);
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            applySiteHeaders(req);

            HttpResponse<InputStream> response =
                    httpClient.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("LLM stream HTTP {}: {}", response.statusCode(), truncate(err, 300));
                return false;
            }

            boolean any = false;
            boolean[] insideThink = {false};
            StringBuilder tagBuf = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    try {
                        JsonNode root = mapper.readTree(data);
                        JsonNode content = root.path("choices").path(0).path("delta").path("content");
                        if (content.isMissingNode() || content.isNull()) {
                            continue;
                        }
                        String token = content.asText();
                        if (token == null || token.isEmpty()) {
                            continue;
                        }
                        String visible = stripThinkingTags(token, insideThink, tagBuf);
                        if (!visible.isEmpty()) {
                            any = true;
                            onToken.accept(visible);
                        }
                    } catch (Exception parseEx) {
                        log.debug("Skip stream chunk: {}", parseEx.getMessage());
                    }
                }
            }
            return any;
        } catch (Exception e) {
            log.warn("LLM stream failed: {}", e.getMessage());
            return false;
        }
    }

    public Optional<String> chatBlocking(String systemPrompt, List<ChatTurn> history, String userMessage) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        LlmProperties.Profile profile = props.resolveProfile(props.getDefaultProfile());
        if (profile == null || profile.getModel() == null || profile.getModel().isBlank()) {
            return Optional.empty();
        }
        try {
            ObjectNode body = buildRequestBody(profile, systemPrompt, history, userMessage, false);
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.setBearerAuth(props.getApiKey());
                        if (props.getSiteUrl() != null && !props.getSiteUrl().isBlank()) {
                            h.set("HTTP-Referer", props.getSiteUrl());
                        }
                        if (props.getSiteName() != null && !props.getSiteName().isBlank()) {
                            h.set("X-Title", props.getSiteName());
                        }
                    })
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                return Optional.empty();
            }
            String text = content.asText().trim();
            text = text.replaceAll("(?s)<think>.*?</think>", "").trim();
            text = text.replaceAll("(?s)<thinking>.*?</thinking>", "").trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.warn("LLM call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private ObjectNode buildRequestBody(
            LlmProperties.Profile profile,
            String systemPrompt,
            List<ChatTurn> history,
            String userMessage,
            boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", profile.getModel());
        body.put("temperature", profile.getTemperature());
        body.put("max_tokens", profile.getMaxTokens());
        body.put("stream", stream);
        ArrayNode messages = body.putArray("messages");
        appendMessages(messages, systemPrompt, history, userMessage);
        applyThinkingMode(body);
        return body;
    }

    /**
     * Qwen3.7 Flash 等 hybrid thinking 預設會思考；關閉以加快首 token、省 token。
     */
    private void applyThinkingMode(ObjectNode body) {
        boolean thinking = props.isEnableThinking();
        body.put("enable_thinking", thinking);
        ObjectNode chatTemplateKwargs = body.putObject("chat_template_kwargs");
        chatTemplateKwargs.put("enable_thinking", thinking);
        if (!thinking) {
            ObjectNode reasoning = body.putObject("reasoning");
            reasoning.put("exclude", true);
            reasoning.put("effort", "none");
        }
    }

    private void applySiteHeaders(HttpRequest.Builder req) {
        if (props.getSiteUrl() != null && !props.getSiteUrl().isBlank()) {
            req.header("HTTP-Referer", props.getSiteUrl());
        }
        if (props.getSiteName() != null && !props.getSiteName().isBlank()) {
            req.header("X-Title", props.getSiteName());
        }
    }

    private void appendMessages(
            ArrayNode messages, String systemPrompt, List<ChatTurn> history, String userMessage) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            String content = systemPrompt;
            if (!props.isEnableThinking() && !content.contains("不要輸出思考")) {
                content = content + NO_THINK_HINT;
            }
            sys.put("content", content);
        }
        if (history != null) {
            for (ChatTurn t : history) {
                if (t.getRole() == null || t.getContent() == null) {
                    continue;
                }
                ObjectNode m = messages.addObject();
                m.put("role", t.getRole());
                m.put("content", t.getContent());
            }
        }
        if (userMessage != null && !userMessage.isBlank()) {
            ObjectNode u = messages.addObject();
            u.put("role", "user");
            String content = userMessage;
            if (!props.isEnableThinking() && !content.contains("/no_think")) {
                content = content + "\n/no_think";
            }
            u.put("content", content);
        }
    }

    /**
     * 串流過濾 &lt;think&gt;…&lt;/think&gt;（可能跨 chunk）。
     */
    static String stripThinkingTags(String token, boolean[] insideThink, StringBuilder tagBuf) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!insideThink[0]) {
                if (tagBuf.length() > 0 || c == '<') {
                    tagBuf.append(c);
                    String t = tagBuf.toString().toLowerCase();
                    if ("<think>".equals(t) || "<thinking>".equals(t)) {
                        insideThink[0] = true;
                        tagBuf.setLength(0);
                    } else if (!("<think>".startsWith(t) || "<thinking>".startsWith(t))) {
                        out.append(tagBuf);
                        tagBuf.setLength(0);
                    }
                } else {
                    out.append(c);
                }
            } else {
                tagBuf.append(c);
                String t = tagBuf.toString().toLowerCase();
                if (t.endsWith("</think>") || t.endsWith("</thinking>")) {
                    insideThink[0] = false;
                    tagBuf.setLength(0);
                } else if (tagBuf.length() > 24) {
                    String tail = tagBuf.substring(Math.max(0, tagBuf.length() - 12));
                    tagBuf.setLength(0);
                    tagBuf.append(tail);
                }
            }
        }
        return out.toString();
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
