package com.situationpuzzle.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.situationpuzzle.dto.ApiResponse;
import com.situationpuzzle.exception.ApiException;
import com.situationpuzzle.service.game.GameFlowService;
import com.situationpuzzle.service.game.GameStateService;
import com.situationpuzzle.service.game.GameState;
import com.situationpuzzle.service.game.GameViewService;
import com.situationpuzzle.service.state.StateEnvelopeBuilder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stories")
public class StoryController {
    private final GameStateService stateService;
    private final GameFlowService flowService;
    private final GameViewService viewService;
    private final ObjectMapper objectMapper;
    private final StateEnvelopeBuilder stateEnvelopeBuilder;

    public StoryController(
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

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        GameState state = stateService.require();
        return ApiResponse.ok(Map.of("menu", viewService.toView(state).get("menu")));
    }

    @PostMapping("/select")
    public ApiResponse<Map<String, Object>> select(@RequestBody Map<String, Object> body) {
        int order = ((Number) body.get("storyOrder")).intValue();
        GameState state = stateService.require();
        flowService.selectStory(state, order);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @GetMapping("/current/content")
    public ApiResponse<Map<String, Object>> content() {
        GameState state = stateService.require();
        return ApiResponse.ok(viewService.toView(state));
    }

    @PostMapping("/current/begin-questions")
    public ApiResponse<Map<String, Object>> begin() {
        GameState state = stateService.require();
        flowService.beginQuestions(state);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @GetMapping("/current/options")
    public ApiResponse<Map<String, Object>> options() {
        GameState state = stateService.require();
        return ApiResponse.ok(viewService.toView(state));
    }

    @PostMapping("/current/answer")
    public ApiResponse<Map<String, Object>> answer(@RequestBody Map<String, Object> body) {
        long optionId = ((Number) body.get("optionId")).longValue();
        GameState state = stateService.require();
        flowService.answer(state, optionId);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    /**
     * 館長回答 SSE 串流（直接寫 response 並 flush，避免緩衝）。
     * 事件 JSON：type=meta|token|done|error
     */
    @PostMapping(value = "/current/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void answerStream(
            @RequestBody Map<String, Object> body,
            HttpServletResponse response) throws Exception {
        long optionId = ((Number) body.get("optionId")).longValue();
        SseSupport.prepare(response);
        GameState state = stateService.require();

        try {
            GameFlowService.AnswerPrep prep = flowService.prepareAnswer(state, optionId);
            Map<String, Object> meta = SseSupport.event("meta");
            meta.put("role", "npc");
            meta.put("optionId", prep.optionId());
            meta.put("scoreDelta", prep.delta());
            meta.put("storyScore", prep.storyScore());
            meta.put("currentRound", state.getCurrentRound());
            meta.put("maxRounds", state.getMaxRounds());
            SseSupport.send(response, objectMapper, meta);

            StringBuilder full = new StringBuilder();
            String source = flowService.streamNpcAnswer(state, prep, token -> {
                full.append(token);
                try {
                    Map<String, Object> t = SseSupport.event("token");
                    t.put("text", token);
                    SseSupport.send(response, objectMapper, t);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            flowService.completeAnswer(state, prep, full.toString().trim(), source);
            stateService.markDirty();

            Map<String, Object> done = SseSupport.event("done");
            done.put("source", source);
            done.put("fullText", full.toString().trim());
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

    @PostMapping("/current/continue")
    public ApiResponse<Map<String, Object>> continueAfterReply() {
        GameState state = stateService.require();
        flowService.continueAfterReply(state);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @PostMapping("/current/finalize")
    public ApiResponse<Map<String, Object>> finalizeStory() {
        GameState state = stateService.require();
        flowService.finalizeStory(state);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @PostMapping("/current/back-to-menu")
    public ApiResponse<Map<String, Object>> backToMenu() {
        GameState state = stateService.require();
        flowService.backToMenu(state);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @GetMapping("/{storyOrder}/real-case")
    public ApiResponse<Map<String, Object>> realCase(@PathVariable int storyOrder) {
        GameState state = stateService.require();
        GameFlowService.RealCaseInfo info = flowService.realCase(state, storyOrder);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("storyOrder", storyOrder);
        payload.put("text", info.text());
        if (info.url() != null) {
            payload.put("url", info.url());
            payload.put("label", info.label() != null ? info.label() : "延伸閱讀");
        }
        return ApiResponse.ok(payload);
    }
}
