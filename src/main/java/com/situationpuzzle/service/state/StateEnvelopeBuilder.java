package com.situationpuzzle.service.state;

import com.situationpuzzle.dto.StateEnvelope;
import com.situationpuzzle.service.game.GameState;
import org.springframework.stereotype.Component;

/**
 * 將 {@link GameState} 打包成 {@link StateEnvelope}：core（簽章加密）+ 兩條對話紀錄。
 *
 * <p>出站 JSON（{@code StateEnvelopeAdvice}）與 SSE {@code done} event 共用同一 builder，
 * 兩條路徑產出同形的 envelope。
 */
@Component
public class StateEnvelopeBuilder {
    private final GameStateCodec codec;

    public StateEnvelopeBuilder(GameStateCodec codec) {
        this.codec = codec;
    }

    public StateEnvelope build(GameState state) {
        String core = codec.encode(ProgressCore.from(state));
        return new StateEnvelope(core, new StateEnvelope.History(
                state.getNpcChatHistory(),
                state.getHelperChatHistory()));
    }
}
