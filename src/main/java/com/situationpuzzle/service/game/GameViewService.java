package com.situationpuzzle.service.game;

import com.situationpuzzle.config.LlmProperties;
import com.situationpuzzle.domain.Story;
import com.situationpuzzle.domain.StoryOption;
import com.situationpuzzle.service.ai.LlmClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GameViewService {
    private final GameFlowService flowService;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;

    public GameViewService(GameFlowService flowService, LlmClient llmClient, LlmProperties llmProperties) {
        this.flowService = flowService;
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
    }

    public Map<String, Object> toView(GameState state) {
        state.recomputeTotalScore();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phase", state.getPhase().name());
        data.put("currentStoryOrder", state.getCurrentStoryOrder());
        data.put("currentRound", state.getCurrentRound());
        data.put("maxRounds", state.getMaxRounds());
        data.put("storyScore", state.currentStoryScore());
        // 僅顯示用加總，結局不依此判斷
        data.put("totalScore", state.getTotalScore());
        data.put("completedCount", state.getCompletedStoryIds().size());
        data.put("truthRevealedCount", state.getTruthRevealedStoryIds().size());
        data.put("endingType", state.getEndingType() == null ? null : state.getEndingType().name());
        data.put("unlockedRealCases", state.isUnlockedRealCases());
        data.put("version", state.getVersion());
        data.put("llmConfigured", llmClient.isAvailable());
        data.put("llmEnabled", llmProperties.isEnabled());

        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("npcText", state.getLastNpcText());
        ui.put("summaryText", state.getLastSummaryText());
        ui.put("truthRevealed", state.isTruthRevealedForCurrentStory());
        if (state.getLastReply() != null) {
            Map<String, Object> lr = new LinkedHashMap<>();
            lr.put("optionId", state.getLastReply().getOptionId());
            lr.put("replyText", state.getLastReply().getReplyText());
            lr.put("scoreDelta", state.getLastReply().getScoreDelta());
            lr.put("storyScore", state.getLastReply().getStoryScore());
            lr.put("source", state.getLastReply().getSource());
            ui.put("lastReply", lr);
            ui.put("npcTextSource", state.getLastReply().getSource());
        }
        ui.put("canFinalize", state.getPhase() == GamePhase.OPTION_REPLY
                && state.getCurrentRound() >= state.getMaxRounds());
        ui.put("endingType", state.getEndingType() == null ? null : state.getEndingType().name());

        Map<String, Object> helper = new LinkedHashMap<>();
        helper.put("hintLevel", state.getHelperHintLevel().name());
        helper.put("lastBubble", state.getLastHelperBubble());
        ui.put("helper", helper);

        if (state.getCurrentStoryId() != null) {
            flowService.allStories().stream()
                    .filter(s -> s.getId().equals(state.getCurrentStoryId()))
                    .findFirst()
                    .ifPresent(s -> {
                        ui.put("title", s.getTitle());
                        ui.put("truthThreshold", s.getTruthScoreThreshold());
                    });
        }

        if (state.getPhase() == GamePhase.SELECT_OPTION || state.getPhase() == GamePhase.ASK_PROMPT) {
            List<Map<String, Object>> options = new ArrayList<>();
            for (StoryOption o : flowService.availableOptions(state)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", o.getId());
                m.put("text", o.getOptionText());
                options.add(m);
            }
            ui.put("options", options);
        }

        // 結局：各則故事說明＋真實事件簡介／連結（聆聽結局時顯示）
        if (state.getPhase() == GamePhase.ENDING || state.getPhase() == GamePhase.FINISHED) {
            List<Map<String, Object>> dossier = new ArrayList<>();
            for (Story s : flowService.allStories()) {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("order", s.getStoryOrder());
                card.put("title", s.getTitle());
                boolean revealed = state.getTruthRevealedStoryIds().contains(s.getId());
                card.put("truthRevealed", revealed);
                card.put("score", state.getStoryScores().getOrDefault(s.getId(), 0));
                card.put("completed", state.getCompletedStoryIds().contains(s.getId()));
                String caseText = s.getRealCaseText();
                if (caseText == null || caseText.isBlank()) {
                    caseText = "（此則真實事件說明尚未登錄）";
                }
                card.put("realCaseText", caseText);
                if (s.getRealCaseUrl() != null && !s.getRealCaseUrl().isBlank()) {
                    card.put("realCaseUrl", s.getRealCaseUrl());
                    card.put("realCaseLabel",
                            s.getRealCaseLabel() != null && !s.getRealCaseLabel().isBlank()
                                    ? s.getRealCaseLabel()
                                    : "延伸閱讀");
                }
                dossier.add(card);
            }
            ui.put("endingDossier", dossier);
            ui.put("endingType", state.getEndingType() == null ? null : state.getEndingType().name());
        }

        data.put("ui", ui);

        List<Map<String, Object>> menu = new ArrayList<>();
        for (Story s : flowService.allStories()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("order", s.getStoryOrder());
            item.put("title", s.getTitle());
            item.put("locked", !flowService.isUnlocked(state, s));
            item.put("completed", state.getCompletedStoryIds().contains(s.getId()));
            item.put("score", state.getStoryScores().getOrDefault(s.getId(), 0));
            item.put("truthRevealed", state.getTruthRevealedStoryIds().contains(s.getId()));
            item.put("truthThreshold", s.getTruthScoreThreshold());
            // 本則玩過即可看該則真實事件；真結局後全部可看
            item.put("realCaseUnlocked",
                    state.getCompletedStoryIds().contains(s.getId()) || state.isUnlockedRealCases());
            menu.add(item);
        }
        data.put("menu", menu);
        return data;
    }
}
