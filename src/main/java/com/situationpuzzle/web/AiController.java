package com.situationpuzzle.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.situationpuzzle.dto.ApiResponse;
import com.situationpuzzle.exception.ApiException;
import com.situationpuzzle.service.ai.HelperAiService;
import com.situationpuzzle.service.game.GameFlowService;
import com.situationpuzzle.service.game.GameStateService;
import com.situationpuzzle.service.game.GameState;
import com.situationpuzzle.service.game.GameViewService;
import com.situationpuzzle.service.game.HintLevel;
import com.situationpuzzle.service.state.StateEnvelopeBuilder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final GameStateService stateService;
    private final GameFlowService flowService;
    private final GameViewService viewService;
    private final ObjectMapper objectMapper;
    private final StateEnvelopeBuilder stateEnvelopeBuilder;

    public AiController(
            GameStateService stateService,
            GameFlowService flowService,
            GameViewService viewService,
            ObjectMapper objectMapper,
            StateEnvelopeBuilder stateEnvelopeBuilder) {
        this.stateService = stateService;
        this.flowService = flowService;
        this.viewService = viewService;
        this.objectMapper = objectMapper;
        this.stateEnvelopeBuilder = stateEnvelopeBuilder;
    }

    @GetMapping("/helper/settings")
    public ApiResponse<Map<String, Object>> getSettings() {
        GameState state = stateService.getOrCreate(); // 標題頁也能聊：無進度時建立空白 TITLE 狀態
        return ApiResponse.ok(Map.of("hintLevel", state.getHelperHintLevel().name()));
    }

    @PostMapping("/helper/settings")
    public ApiResponse<Map<String, Object>> setSettings(@RequestBody Map<String, Object> body) {
        GameState state = stateService.getOrCreate(); // 標題頁也能聊：無進度時建立空白 TITLE 狀態
        HintLevel level = HintLevel.valueOf(String.valueOf(body.get("hintLevel")).toUpperCase());
        flowService.setHelperLevel(state, level);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @PostMapping("/helper/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        GameState state = stateService.getOrCreate(); // 標題頁也能聊：無進度時建立空白 TITLE 狀態
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message"));
        HintLevel level = parseLevel(body.get("hintLevel"));
        String reply = flowService.helperChat(state, message, level);
        stateService.markDirty();
        Map<String, Object> view = viewService.toView(state);
        view.put("helperReply", reply);
        return ApiResponse.ok(view);
    }

    @PostMapping("/helper/hint")
    public ApiResponse<Map<String, Object>> hint(@RequestBody(required = false) Map<String, Object> body) {
        GameState state = stateService.getOrCreate(); // 標題頁也能聊：無進度時建立空白 TITLE 狀態
        HintLevel level = body == null ? null : parseLevel(body.get("hintLevel"));
        String reply = flowService.helperHint(state, level);
        stateService.markDirty();
        Map<String, Object> view = viewService.toView(state);
        view.put("helperReply", reply);
        return ApiResponse.ok(view);
    }

    @PostMapping(value = "/helper/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chatStream(
            @RequestBody Map<String, Object> body,
            HttpServletResponse response) throws Exception {
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message"));
        HintLevel level = parseLevel(body.get("hintLevel"));
        helperStream(response, message, level, false);
    }

    @PostMapping(value = "/helper/hint/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void hintStream(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletResponse response) throws Exception {
        HintLevel level = body == null ? null : parseLevel(body.get("hintLevel"));
        helperStream(response, null, level, true);
    }

    private void helperStream(
            HttpServletResponse response,
            String message,
            HintLevel level,
            boolean hintOnly) throws Exception {
        SseSupport.prepare(response);
        GameState state = stateService.getOrCreate(); // 標題頁也能聊：無進度時建立空白 TITLE 狀態
        try {
            Map<String, Object> meta = SseSupport.event("meta");
            meta.put("role", "helper");
            meta.put("hintLevel", (level != null ? level : state.getHelperHintLevel()).name());
            SseSupport.send(response, objectMapper, meta);

            HelperAiService.StreamResult result;
            if (hintOnly) {
                result = flowService.streamHelperHint(state, level, token -> {
                    try {
                        Map<String, Object> t = SseSupport.event("token");
                        t.put("text", token);
                        SseSupport.send(response, objectMapper, t);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                result = flowService.streamHelperChat(state, message, level, token -> {
                    try {
                        Map<String, Object> t = SseSupport.event("token");
                        t.put("text", token);
                        SseSupport.send(response, objectMapper, t);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            stateService.markDirty();
            Map<String, Object> done = SseSupport.event("done");
            done.put("source", result.source());
            done.put("fullText", result.text());
            done.put("helperReply", result.text());
            done.put("data", viewService.toView(state));
            // 無狀態化：最終進度隨 done event 回前端（SSE 無法寫 Set-Cookie）
            done.put("state", stateEnvelopeBuilder.build(state));
            SseSupport.send(response, objectMapper, done);
        } catch (ApiException ex) {
            Map<String, Object> err = SseSupport.event("error");
            err.put("code", ex.getCode());
            err.put("message", ex.getMessage());
            SseSupport.send(response, objectMapper, err);
        } catch (Exception ex) {
            Map<String, Object> err = SseSupport.event("error");
            err.put("code", "STREAM");
            err.put("message", ex.getMessage() != null ? ex.getMessage() : "串流失敗");
            SseSupport.send(response, objectMapper, err);
        }
    }

    private HintLevel parseLevel(Object raw) {
        if (raw == null) return null;
        return HintLevel.valueOf(String.valueOf(raw).toUpperCase());
    }
}
