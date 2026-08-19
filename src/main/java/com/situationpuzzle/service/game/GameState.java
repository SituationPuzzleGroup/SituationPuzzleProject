package com.situationpuzzle.service.game;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameState implements Serializable {
    public static final String SESSION_KEY = "GAME_STATE";

    private GamePhase phase = GamePhase.TITLE;
    private Integer currentStoryOrder;
    private Long currentStoryId;
    private int currentRound;
    private int maxRounds = 4;
    private Set<Long> selectedOptionIds = new HashSet<>();
    /** 各則故事獨立得分（key = story.id） */
    private Map<Long, Integer> storyScores = new HashMap<>();
    private Set<Long> completedStoryIds = new HashSet<>();
    /** 各則是否達揭謎門檻（key = story.id） */
    private Set<Long> truthRevealedStoryIds = new HashSet<>();
    /** 僅顯示用：各則得分加總，不作為結局門檻 */
    private int totalScore;
    private LastReply lastReply;
    private EndingType endingType = EndingType.NONE;
    private boolean unlockedRealCases;
    private HintLevel helperHintLevel = HintLevel.LOW;
    private List<ChatTurn> npcChatHistory = new ArrayList<>();
    private List<ChatTurn> helperChatHistory = new ArrayList<>();
    private String lastNpcText;
    private String lastHelperBubble;
    private String lastSummaryText;
    private boolean truthRevealedForCurrentStory;
    private int version = 1;

    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public Integer getCurrentStoryOrder() { return currentStoryOrder; }
    public void setCurrentStoryOrder(Integer currentStoryOrder) { this.currentStoryOrder = currentStoryOrder; }
    public Long getCurrentStoryId() { return currentStoryId; }
    public void setCurrentStoryId(Long currentStoryId) { this.currentStoryId = currentStoryId; }
    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }
    public Set<Long> getSelectedOptionIds() { return selectedOptionIds; }
    public void setSelectedOptionIds(Set<Long> selectedOptionIds) { this.selectedOptionIds = selectedOptionIds; }
    public Map<Long, Integer> getStoryScores() { return storyScores; }
    public void setStoryScores(Map<Long, Integer> storyScores) { this.storyScores = storyScores; }
    public Set<Long> getCompletedStoryIds() { return completedStoryIds; }
    public void setCompletedStoryIds(Set<Long> completedStoryIds) { this.completedStoryIds = completedStoryIds; }
    public Set<Long> getTruthRevealedStoryIds() { return truthRevealedStoryIds; }
    public void setTruthRevealedStoryIds(Set<Long> truthRevealedStoryIds) {
        this.truthRevealedStoryIds = truthRevealedStoryIds;
    }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    /** 依各則 storyScores 重算加總（顯示用） */
    public void recomputeTotalScore() {
        int sum = 0;
        if (storyScores != null) {
            for (int v : storyScores.values()) {
                sum += v;
            }
        }
        this.totalScore = sum;
    }
    public LastReply getLastReply() { return lastReply; }
    public void setLastReply(LastReply lastReply) { this.lastReply = lastReply; }
    public EndingType getEndingType() { return endingType; }
    public void setEndingType(EndingType endingType) { this.endingType = endingType; }
    public boolean isUnlockedRealCases() { return unlockedRealCases; }
    public void setUnlockedRealCases(boolean unlockedRealCases) { this.unlockedRealCases = unlockedRealCases; }
    public HintLevel getHelperHintLevel() { return helperHintLevel; }
    public void setHelperHintLevel(HintLevel helperHintLevel) { this.helperHintLevel = helperHintLevel; }
    public List<ChatTurn> getNpcChatHistory() { return npcChatHistory; }
    public void setNpcChatHistory(List<ChatTurn> npcChatHistory) { this.npcChatHistory = npcChatHistory; }
    public List<ChatTurn> getHelperChatHistory() { return helperChatHistory; }
    public void setHelperChatHistory(List<ChatTurn> helperChatHistory) { this.helperChatHistory = helperChatHistory; }
    public String getLastNpcText() { return lastNpcText; }
    public void setLastNpcText(String lastNpcText) { this.lastNpcText = lastNpcText; }
    public String getLastHelperBubble() { return lastHelperBubble; }
    public void setLastHelperBubble(String lastHelperBubble) { this.lastHelperBubble = lastHelperBubble; }
    public String getLastSummaryText() { return lastSummaryText; }
    public void setLastSummaryText(String lastSummaryText) { this.lastSummaryText = lastSummaryText; }
    public boolean isTruthRevealedForCurrentStory() { return truthRevealedForCurrentStory; }
    public void setTruthRevealedForCurrentStory(boolean truthRevealedForCurrentStory) {
        this.truthRevealedForCurrentStory = truthRevealedForCurrentStory;
    }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public void bumpVersion() { this.version++; }

    public int currentStoryScore() {
        if (currentStoryId == null) return 0;
        return storyScores.getOrDefault(currentStoryId, 0);
    }
}
