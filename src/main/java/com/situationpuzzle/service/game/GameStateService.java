package com.situationpuzzle.service.game;

import com.situationpuzzle.config.GameProperties;
import com.situationpuzzle.config.HelperProperties;
import com.situationpuzzle.service.state.GameContext;
import org.springframework.stereotype.Service;

/**
 * 遊戲狀態存取 — 無狀態版。
 *
 * <p>狀態由 client 持有（cookie 進度核心 + sessionStorage 對話紀錄），
 * {@link StateReconstructionFilter} 於請求最早期重建進 {@link GameContext}。
 * 本服務是對 {@link GameContext} 的薄封裝，並負責產生空白進度。
 */
@Service
public class GameStateService {
    private final GameContext gameContext;
    private final GameProperties gameProperties;
    private final HelperProperties helperProperties;

    public GameStateService(GameContext gameContext,
                            GameProperties gameProperties,
                            HelperProperties helperProperties) {
        this.gameContext = gameContext;
        this.gameProperties = gameProperties;
        this.helperProperties = helperProperties;
    }

    /** 有進度則回傳；否則建空白進度並採納。 */
    public GameState getOrCreate() {
        if (gameContext.isPresent()) {
            return gameContext.state();
        }
        GameState created = newBlank();
        gameContext.adopt(created);
        return created;
    }

    /** 不 present 丟 NO_PROGRESS。 */
    public GameState require() {
        return gameContext.require();
    }

    /** 重置為空白進度。 */
    public GameState reset() {
        GameState created = newBlank();
        gameContext.adopt(created);
        return created;
    }

    /** 標記狀態已變更（bumpVersion + dirty）。 */
    public void markDirty() {
        gameContext.markDirty();
    }

    private GameState newBlank() {
        GameState s = new GameState();
        s.setPhase(GamePhase.TITLE);
        s.setMaxRounds(gameProperties.getMaxRounds());
        s.setHelperHintLevel(helperProperties.getDefaultHintLevel());
        s.setEndingType(EndingType.NONE);
        return s;
    }
}
