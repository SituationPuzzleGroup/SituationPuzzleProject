package com.situationpuzzle.service.state;

import com.situationpuzzle.service.game.EndingType;
import com.situationpuzzle.service.game.GamePhase;
import com.situationpuzzle.service.game.GameState;
import com.situationpuzzle.service.game.HintLevel;
import com.situationpuzzle.service.game.LastReply;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 遊戲進度核心 — 序列化進 cookie 的部分。
 *
 * <p>刻意排除：
 * <ul>
 *   <li>兩條 AI 對話紀錄（{@code npcChatHistory}/{@code helperChatHistory}）— 會爆量，
 *       改由前端 sessionStorage 持有並隨請求回送。</li>
 *   <li>僅顯示用的長文字（{@code lastNpcText}/{@code lastSummaryText}/{@code lastHelperBubble}
 *       與 {@code lastReply.replyText}）— 單向（僅寫入與顯示，流程不讀回），
 *       存前端 sessionStorage 供重新整理時顯示。</li>
 * </ul>
 * 核心只放計分/階段/進度等「防竄改關鍵」欄位，飽和約 400B，遠低於 cookie 4KB 上限。
 */
public record ProgressCore(
        GamePhase phase,
        Integer currentStoryOrder,
        Long currentStoryId,
        int currentRound,
        int maxRounds,
        Set<Long> selectedOptionIds,
        Map<Long, Integer> storyScores,
        Set<Long> completedStoryIds,
        Set<Long> truthRevealedStoryIds,
        int totalScore,
        EndingType endingType,
        boolean unlockedRealCases,
        HintLevel helperHintLevel,
        boolean truthRevealedForCurrentStory,
        int version,
        LastReplyCore lastReply
) {
    /** {@link LastReply} 去掉 {@code replyText}（長文，存前端 sessionStorage）。 */
    public record LastReplyCore(Long optionId, int scoreDelta, int storyScore, String source) {
        static LastReplyCore from(LastReply lr) {
            if (lr == null) return null;
            return new LastReplyCore(lr.getOptionId(), lr.getScoreDelta(), lr.getStoryScore(), lr.getSource());
        }

        LastReply toLastReply(String replyText) {
            return new LastReply(optionId, replyText, scoreDelta, storyScore, source);
        }
    }

    /** 從完整 {@link GameState} 抽取核心（深拷貝集合，避免持有 server 端可變引用）。 */
    public static ProgressCore from(GameState s) {
        return new ProgressCore(
                s.getPhase(),
                s.getCurrentStoryOrder(),
                s.getCurrentStoryId(),
                s.getCurrentRound(),
                s.getMaxRounds(),
                copySet(s.getSelectedOptionIds()),
                copyMap(s.getStoryScores()),
                copySet(s.getCompletedStoryIds()),
                copySet(s.getTruthRevealedStoryIds()),
                s.getTotalScore(),
                s.getEndingType(),
                s.isUnlockedRealCases(),
                s.getHelperHintLevel(),
                s.isTruthRevealedForCurrentStory(),
                s.getVersion(),
                LastReplyCore.from(s.getLastReply())
        );
    }

    /** 將核心欄位寫回 {@link GameState}（不碰對話紀錄與顯示文字，由 caller 另行處理）。 */
    public void mergeInto(GameState s) {
        s.setPhase(phase != null ? phase : GamePhase.TITLE);
        s.setCurrentStoryOrder(currentStoryOrder);
        s.setCurrentStoryId(currentStoryId);
        s.setCurrentRound(currentRound);
        s.setMaxRounds(maxRounds);
        s.setSelectedOptionIds(copySet(selectedOptionIds));
        s.setStoryScores(copyMap(storyScores));
        s.setCompletedStoryIds(copySet(completedStoryIds));
        s.setTruthRevealedStoryIds(copySet(truthRevealedStoryIds));
        s.setTotalScore(totalScore);
        s.setEndingType(endingType != null ? endingType : EndingType.NONE);
        s.setUnlockedRealCases(unlockedRealCases);
        s.setHelperHintLevel(helperHintLevel != null ? helperHintLevel : HintLevel.LOW);
        s.setTruthRevealedForCurrentStory(truthRevealedForCurrentStory);
        s.setVersion(version);
        // lastReply.replyText 不在 core；重建時給 null（顯示文字由前端 sessionStorage 提供）
        s.setLastReply(lastReply == null ? null : lastReply.toLastReply(null));
    }

    private static <T> Set<T> copySet(Set<T> src) {
        return src == null ? new HashSet<>() : new HashSet<>(src);
    }

    private static <K, V> Map<K, V> copyMap(Map<K, V> src) {
        return src == null ? new HashMap<>() : new HashMap<>(src);
    }
}
