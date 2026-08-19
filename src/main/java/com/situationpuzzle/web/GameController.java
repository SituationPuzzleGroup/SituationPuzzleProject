package com.situationpuzzle.web;

import com.situationpuzzle.dto.ApiResponse;
import com.situationpuzzle.exception.ApiException;
import com.situationpuzzle.service.game.GameFlowService;
import com.situationpuzzle.service.game.GameStateService;
import com.situationpuzzle.service.game.GameState;
import com.situationpuzzle.service.game.GameViewService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GameController {

    /** 隱藏密碼（demo／測試用捷径；大小寫不分）：
     *  rose=故事1、perfume=故事2、perfect=故事3、phantom=故事4、masterkey=直接通關 */
    private static final Map<String, Integer> CHEAT_STORY_CODES = Map.of(
            "rose", 1,
            "perfume", 2,
            "perfect", 3,
            "phantom", 4);
    private static final String CHEAT_CLEAR_ALL = "masterkey";

    private final GameStateService stateService;
    private final GameFlowService flowService;
    private final GameViewService viewService;

    public GameController(
            GameStateService stateService,
            GameFlowService flowService,
            GameViewService viewService) {
        this.stateService = stateService;
        this.flowService = flowService;
        this.viewService = viewService;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "app", "situation-puzzle"
        ));
    }

    @PostMapping("/game/start")
    public ApiResponse<Map<String, Object>> start(
            @RequestBody(required = false) Map<String, Object> body) {
        boolean reset = body != null && Boolean.TRUE.equals(body.get("reset"));
        GameState state = stateService.getOrCreate();
        flowService.start(state, reset);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @GetMapping("/game/state")
    public ApiResponse<Map<String, Object>> state() {
        GameState state = stateService.require();
        return ApiResponse.ok(viewService.toView(state));
    }

    @PostMapping("/game/intro/continue")
    public ApiResponse<Map<String, Object>> introContinue() {
        GameState state = stateService.require();
        flowService.advanceIntro(state);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @PostMapping("/game/finish")
    public ApiResponse<Map<String, Object>> finish() {
        GameState state = stateService.require();
        flowService.finish(state);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    @PostMapping("/game/ending/done")
    public ApiResponse<Map<String, Object>> endingDone() {
        GameState state = stateService.require();
        flowService.goFinished(state);
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }

    /** 隱藏密碼：捷徑完成故事／直接通關（F9 密碼框用） */
    @PostMapping("/game/cheat")
    public ApiResponse<Map<String, Object>> cheat(@RequestBody Map<String, Object> body) {
        String code = body.get("code") == null ? "" : String.valueOf(body.get("code")).trim().toLowerCase();
        GameState state = stateService.require();
        if (CHEAT_CLEAR_ALL.equals(code)) {
            flowService.cheatClearAll(state);
        } else {
            Integer order = CHEAT_STORY_CODES.get(code);
            if (order == null) {
                throw ApiException.badRequest("CHEAT_BAD_CODE", "密碼不正確");
            }
            flowService.cheatCompleteStory(state, order);
        }
        stateService.markDirty();
        return ApiResponse.ok(viewService.toView(state));
    }
}
