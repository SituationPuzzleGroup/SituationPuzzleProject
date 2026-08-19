package com.situationpuzzle.dto;

import com.situationpuzzle.service.game.ChatTurn;

import java.util.List;

/**
 * 回應中 {@code state} 欄位的形狀：前端據此寫回 cookie 與 sessionStorage。
 *
 * <p>{@code core} 是簽章加密的進度核心（寫入 {@code sp_core} cookie）；
 * {@code history} 是兩條 AI 對話紀錄（寫入 sessionStorage {@code sp_history}）。
 */
public record StateEnvelope(String core, History history) {
    public record History(List<ChatTurn> npcChat, List<ChatTurn> helperChat) {}
}
