package com.situationpuzzle.service.state;

import com.situationpuzzle.exception.ApiException;
import com.situationpuzzle.service.game.GameState;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 每個請求的遊戲狀態載體（request-scoped）。
 *
 * <p>{@link StateReconstructionFilter} 在請求最早期從 cookie + body._history 重建
 * {@link GameState} 並 {@link #adopt(GameState)} 進來；後續 controller / service /
 * {@code StateEnvelopeAdvice} 透過注入的同一 proxy 實例讀寫。
 */
@Component
@RequestScope
public class GameContext {
    private GameState state;
    private boolean present;
    private boolean dirty;

    /** 目前狀態（可能 null）。 */
    public GameState state() { return state; }

    /** 是否帶有有效進度（cookie 解碼成功 或 本請求內新建過）。 */
    public boolean isPresent() { return present; }

    /** 本請求是否變更過狀態（出站時據此決定是否回傳新 envelope）。 */
    public boolean isDirty() { return dirty; }

    /** 採納一個重建或新建的狀態，標記為 present（dirty 由後續 {@link #markDirty()} 標記）。 */
    public void adopt(GameState state) {
        this.state = state;
        this.present = true;
    }

    /** 不 present 時拋 NO_PROGRESS（尚無遊戲進度）。 */
    public GameState require() {
        if (!present || state == null) {
            throw ApiException.badRequest("NO_PROGRESS", "尚無遊戲進度，請先 POST /api/game/start");
        }
        return state;
    }

    /** 標記狀態已變更：bumpVersion + dirty。 */
    public void markDirty() {
        if (state != null) {
            state.bumpVersion();
            dirty = true;
        }
    }
}
