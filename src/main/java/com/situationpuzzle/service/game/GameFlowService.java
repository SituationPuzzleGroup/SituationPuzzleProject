package com.situationpuzzle.service.game;

import com.situationpuzzle.config.GameProperties;
import com.situationpuzzle.domain.DialogueScript;
import com.situationpuzzle.domain.Story;
import com.situationpuzzle.domain.StoryOption;
import com.situationpuzzle.exception.ApiException;
import com.situationpuzzle.repository.DialogueScriptRepository;
import com.situationpuzzle.repository.StoryOptionRepository;
import com.situationpuzzle.repository.StoryRepository;
import com.situationpuzzle.service.ai.HelperAiService;
import com.situationpuzzle.service.ai.NpcDialogueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
// HintLevel used by stream helper methods

@Service
public class GameFlowService {
    private final StoryRepository storyRepository;
    private final StoryOptionRepository optionRepository;
    private final DialogueScriptRepository dialogueScriptRepository;
    private final GameProperties gameProperties;
    private final NpcDialogueService npcDialogueService;
    private final HelperAiService helperAiService;

    public GameFlowService(
            StoryRepository storyRepository,
            StoryOptionRepository optionRepository,
            DialogueScriptRepository dialogueScriptRepository,
            GameProperties gameProperties,
            NpcDialogueService npcDialogueService,
            HelperAiService helperAiService) {
        this.storyRepository = storyRepository;
        this.optionRepository = optionRepository;
        this.dialogueScriptRepository = dialogueScriptRepository;
        this.gameProperties = gameProperties;
        this.npcDialogueService = npcDialogueService;
        this.helperAiService = helperAiService;
    }

    public GameState start(GameState state, boolean reset) {
        if (reset || state.getPhase() == GamePhase.TITLE || state.getPhase() == GamePhase.FINISHED) {
            if (reset || state.getPhase() == GamePhase.FINISHED || state.getPhase() == GamePhase.TITLE) {
                state.setStoryScores(new java.util.HashMap<>());
                state.setCompletedStoryIds(new HashSet<>());
                state.setTruthRevealedStoryIds(new HashSet<>());
                state.setTotalScore(0);
                state.setEndingType(EndingType.NONE);
                state.setUnlockedRealCases(false);
                state.getNpcChatHistory().clear();
                state.getHelperChatHistory().clear();
                state.setLastHelperBubble(null);
            }
            state.setPhase(GamePhase.INTRO);
            state.setCurrentStoryOrder(null);
            state.setCurrentStoryId(null);
            state.setCurrentRound(0);
            state.setSelectedOptionIds(new HashSet<>());
            state.setLastReply(null);
            state.setLastSummaryText(null);
            state.setTruthRevealedForCurrentStory(false);
            state.setLastNpcText(script("INTRO",
                    "你好呀，來訪者。\n\n看起來你不是逛進來的——你是掉進這個世界的人。"
                            + "\n\n想回到現實，就必須走完四則故事遊戲。準備好的話，往下走。"));
            state.setMaxRounds(gameProperties.getMaxRounds());
        }
        return state;
    }

    public GameState advanceIntro(GameState state) {
        requirePhase(state, GamePhase.INTRO, GamePhase.TITLE);
        state.setPhase(GamePhase.STORY_MENU);
        state.setLastNpcText(script("RULES",
                "四則倒敘故事猜謎：我先講結果，你用四次提問拼因果。"
                        + "\n\n四則都結束，回去的路才會開。右側選一則開始吧。"));
        return state;
    }

    @Transactional(readOnly = true)
    public GameState selectStory(GameState state, int storyOrder) {
        requirePhase(state, GamePhase.STORY_MENU);
        Story story = storyRepository.findByStoryOrder(storyOrder)
                .filter(Story::isEnabled)
                .orElseThrow(() -> ApiException.notFound("STORY_NOT_FOUND", "找不到故事 " + storyOrder));

        if (state.getCompletedStoryIds().contains(story.getId())) {
            throw ApiException.conflict("STORY_DONE", "此故事已完成，首期不可重玩");
        }
        if (!isUnlocked(state, story)) {
            throw ApiException.forbidden("STORY_LOCKED", "故事尚未解鎖");
        }

        state.setCurrentStoryOrder(story.getStoryOrder());
        state.setCurrentStoryId(story.getId());
        state.setCurrentRound(0);
        state.setSelectedOptionIds(new HashSet<>());
        state.setStoryScores(state.getStoryScores());
        state.getStoryScores().putIfAbsent(story.getId(), 0);
        state.setLastReply(null);
        state.setLastSummaryText(null);
        state.setTruthRevealedForCurrentStory(false);
        state.getNpcChatHistory().clear();
        state.setPhase(GamePhase.STORY_RESULT);
        state.setLastNpcText(story.getResultText());
        return state;
    }

    public GameState beginQuestions(GameState state) {
        requirePhase(state, GamePhase.STORY_RESULT);
        Story story = requireCurrentStory(state);
        state.setCurrentRound(1);
        state.setPhase(GamePhase.SELECT_OPTION);
        String prompt = story.getAskPromptText();
        if (prompt == null || prompt.isBlank()) {
            prompt = "好了，你有 " + state.getMaxRounds() + " 次機會向我提問。";
        }
        state.setLastNpcText(prompt.replace("{{remaining}}", String.valueOf(state.getMaxRounds())));
        return state;
    }

    public record AnswerPrep(Story story, StoryOption option, int delta, int storyScore, long optionId) {}

    /**
     * 計分並進入 OPTION_REPLY（對話文字稍後由串流寫入）。
     */
    @Transactional(readOnly = true)
    public AnswerPrep prepareAnswer(GameState state, long optionId) {
        requirePhase(state, GamePhase.SELECT_OPTION, GamePhase.ASK_PROMPT);
        if (state.getCurrentRound() < 1 || state.getCurrentRound() > state.getMaxRounds()) {
            throw ApiException.conflict("ROUND_INVALID", "目前不在有效提問輪次");
        }
        if (state.getSelectedOptionIds().contains(optionId)) {
            throw ApiException.conflict("OPTION_USED", "此選項已選過");
        }

        Story story = requireCurrentStory(state);
        StoryOption option = optionRepository.findByIdAndStoryIdAndEnabledTrue(optionId, story.getId())
                .orElseThrow(() -> ApiException.badRequest("OPTION_INVALID", "選項不屬於當前故事"));

        int delta = option.isCorrect() ? option.getScoreValue() : 0;
        int storyScore = state.getStoryScores().getOrDefault(story.getId(), 0) + delta;
        state.getStoryScores().put(story.getId(), storyScore);
        state.getSelectedOptionIds().add(optionId);
        state.setPhase(GamePhase.OPTION_REPLY);
        state.setLastNpcText(""); // 串流中
        return new AnswerPrep(story, option, delta, storyScore, optionId);
    }

    public void completeAnswer(GameState state, AnswerPrep prep, String text, String source) {
        state.setLastReply(new LastReply(prep.optionId(), text, prep.delta(), prep.storyScore(), source));
        state.setLastNpcText(text);
        state.setPhase(GamePhase.OPTION_REPLY);
    }

    @Transactional(readOnly = true)
    public GameState answer(GameState state, long optionId) {
        AnswerPrep prep = prepareAnswer(state, optionId);
        boolean canReveal = false;
        NpcDialogueService.GeneratedLine line =
                npcDialogueService.replyToOption(state, prep.story(), prep.option(), canReveal);
        completeAnswer(state, prep, line.text(), line.source());
        return state;
    }

    public String streamNpcAnswer(GameState state, AnswerPrep prep, java.util.function.Consumer<String> onToken) {
        return npcDialogueService.streamReplyToOption(state, prep.story(), prep.option(), false, onToken);
    }

    public HelperAiService.StreamResult streamHelperChat(
            GameState state, String message, HintLevel level, java.util.function.Consumer<String> onToken) {
        return helperAiService.streamChat(state, message, level, onToken);
    }

    public HelperAiService.StreamResult streamHelperHint(
            GameState state, HintLevel level, java.util.function.Consumer<String> onToken) {
        return helperAiService.streamHint(state, level, onToken);
    }

    public GameState continueAfterReply(GameState state) {
        requirePhase(state, GamePhase.OPTION_REPLY);
        if (state.getCurrentRound() >= state.getMaxRounds()) {
            return finalizeStory(state);
        }
        state.setCurrentRound(state.getCurrentRound() + 1);
        state.setPhase(GamePhase.SELECT_OPTION);
        int remaining = state.getMaxRounds() - state.getCurrentRound() + 1;
        state.setLastNpcText("你還有 " + remaining + " 次提問機會。");
        return state;
    }

    @Transactional(readOnly = true)
    public GameState finalizeStory(GameState state) {
        Story story = requireCurrentStory(state);
        int storyScore = state.getStoryScores().getOrDefault(story.getId(), 0);
        int threshold = story.getTruthScoreThreshold() != null
                ? story.getTruthScoreThreshold()
                : gameProperties.getDefaultTruthThreshold();

        // 揭謎門檻：本則獨立（預設 60），與其他故事分數無關
        boolean reveal = storyScore >= threshold;
        state.setTruthRevealedForCurrentStory(reveal);
        if (reveal) {
            state.getTruthRevealedStoryIds().add(story.getId());
        } else {
            state.getTruthRevealedStoryIds().remove(story.getId());
        }
        String summary = reveal ? story.getHighScoreSummary() : story.getLowScoreSummary();
        state.setLastSummaryText(summary);
        state.setLastNpcText(summary);

        state.getCompletedStoryIds().add(story.getId());
        // 各則分數已在 storyScores；totalScore 僅顯示用加總
        state.recomputeTotalScore();

        state.setPhase(GamePhase.STORY_SUMMARY);
        return state;
    }

    public GameState backToMenu(GameState state) {
        requirePhase(state, GamePhase.STORY_SUMMARY);
        Story story = requireCurrentStory(state);
        String finish = story.getFinishText();
        if (finish == null || finish.isBlank()) {
            finish = "這個故事先到這裡。回選單看看下一段吧。";
        }
        boolean revealed = state.getTruthRevealedStoryIds().contains(story.getId());
        int sc = state.getStoryScores().getOrDefault(story.getId(), 0);
        state.setLastNpcText(finish + "\n（本則得分 " + sc + "，"
                + (revealed ? "已揭曉真相" : "未達揭謎門檻") + "）");
        state.setCurrentStoryId(null);
        state.setCurrentStoryOrder(null);
        state.setCurrentRound(0);
        state.setSelectedOptionIds(new HashSet<>());
        state.setLastReply(null);

        List<Story> enabled = storyRepository.findByEnabledTrueOrderByStoryOrderAsc();
        int totalStories = enabled.size();
        if (totalStories > 0 && state.getCompletedStoryIds().size() >= totalStories) {
            state.setPhase(GamePhase.LEAVE_HINT);
            int revealedCount = 0;
            for (Story s : enabled) {
                if (state.getTruthRevealedStoryIds().contains(s.getId())) {
                    revealedCount++;
                }
            }
            state.setLastNpcText(script("LEAVE_HINT",
                    "四則故事，你都聽完了。\n\n門縫裡好像透進一點「外面」的光。"
                            + "你已經具備離開這個世界的資格。\n\n準備好的話，就來聽結局吧。")
                    + "\n\n（已完成 " + totalStories + " 則，其中 " + revealedCount + " 則揭曉真相）");
        } else {
            state.setPhase(GamePhase.STORY_MENU);
        }
        return state;
    }

    /**
     * 密碼捷径：立即完成指定故事（標記完成＋揭謎、給 80 分），回選單。
     * 全數完成時直接進入 LEAVE_HINT（可聆聽結局）。
     */
    public GameState cheatCompleteStory(GameState state, int storyOrder) {
        Story story = storyRepository.findByStoryOrder(storyOrder)
                .orElseThrow(() -> ApiException.notFound("STORY_NOT_FOUND", "找不到故事 " + storyOrder));
        state.getCompletedStoryIds().add(story.getId());
        state.getTruthRevealedStoryIds().add(story.getId());
        state.getStoryScores().put(story.getId(), 80);
        resetCurrentStory(state);
        List<Story> enabled = storyRepository.findByEnabledTrueOrderByStoryOrderAsc();
        boolean all = enabled.stream().allMatch(s -> state.getCompletedStoryIds().contains(s.getId()));
        state.recomputeTotalScore();
        if (all) {
            state.setPhase(GamePhase.LEAVE_HINT);
            state.setLastNpcText("（捷径生效：全部故事已完成。隨時可以「聆聽結局」。）");
        } else {
            state.setPhase(GamePhase.STORY_MENU);
            state.setLastNpcText("（捷径生效：故事 " + storyOrder + "〈" + story.getTitle() + "〉已完成。）");
        }
        return state;
    }

    /** 密碼捷径：直接通關——所有啟用故事完成＋揭謎，進入 LEAVE_HINT。 */
    public GameState cheatClearAll(GameState state) {
        List<Story> enabled = storyRepository.findByEnabledTrueOrderByStoryOrderAsc();
        if (enabled.isEmpty()) {
            throw ApiException.conflict("NO_STORIES", "沒有可結算的故事");
        }
        for (Story s : enabled) {
            state.getCompletedStoryIds().add(s.getId());
            state.getTruthRevealedStoryIds().add(s.getId());
            state.getStoryScores().put(s.getId(), 80);
        }
        resetCurrentStory(state);
        state.recomputeTotalScore();
        state.setPhase(GamePhase.LEAVE_HINT);
        state.setLastNpcText("（捷径生效：全數通關。深呼吸，去聽結局吧。）");
        return state;
    }

    private void resetCurrentStory(GameState state) {
        state.setCurrentStoryId(null);
        state.setCurrentStoryOrder(null);
        state.setCurrentRound(0);
        state.setSelectedOptionIds(new HashSet<>());
        state.setLastReply(null);
    }

    /**
     * 結局判定：各則故事獨立計分；真結局 = 所有已啟用故事皆達該則揭謎門檻。
     * 不再使用「四則加總 ≥ 208/256」的全域分數門檻。
     */
    public GameState finish(GameState state) {
        List<Story> enabled = storyRepository.findByEnabledTrueOrderByStoryOrderAsc();
        int totalStories = enabled.size();
        if (totalStories == 0) {
            throw ApiException.conflict("NO_STORIES", "沒有可結算的故事");
        }
        if (state.getPhase() != GamePhase.LEAVE_HINT && state.getPhase() != GamePhase.STORY_MENU
                && state.getPhase() != GamePhase.ENDING) {
            if (state.getCompletedStoryIds().size() < totalStories) {
                throw ApiException.conflict("NOT_READY",
                        "尚未完成全部故事（" + state.getCompletedStoryIds().size() + "/" + totalStories + "）");
            }
        }
        if (state.getCompletedStoryIds().size() < totalStories) {
            throw ApiException.conflict("NOT_READY",
                    "尚未完成全部故事（" + state.getCompletedStoryIds().size() + "/" + totalStories + "）");
        }

        state.recomputeTotalScore();
        int revealedCount = 0;
        for (Story s : enabled) {
            if (state.getTruthRevealedStoryIds().contains(s.getId())) {
                revealedCount++;
            }
        }
        boolean allTruth = revealedCount >= totalStories;

        if (allTruth) {
            state.setEndingType(EndingType.TRUE);
            state.setUnlockedRealCases(true);
            state.setLastNpcText(script("ENDING_TRUE",
                    "路開了。而且開得很乾淨。\n\n你看穿了這些故事的因果，也通過了四則遊戲。"
                            + "現在，你可以回現實世界了。\n\n請讀「故事與真實事件」檔案。")
                    + "\n\n（各則皆達揭謎門檻）");
        } else {
            state.setEndingType(EndingType.NORMAL);
            // 結局頁仍展示各則事件簡介與連結；完整「真實案件」選單入口仍以真結局為主
            state.setUnlockedRealCases(false);
            state.setLastNpcText(script("ENDING_NORMAL",
                    "路開了。你可以回去了。\n\n只是你沒能把每一則的因果都看穿。"
                            + "\n\n請讀「故事與真實事件」檔案。回到現實之後，也路上小心。")
                    + "\n\n（揭謎 " + revealedCount + "/" + totalStories + " 則）");
        }
        state.setPhase(GamePhase.ENDING);
        return state;
    }

    public GameState goFinished(GameState state) {
        requirePhase(state, GamePhase.ENDING);
        state.setPhase(GamePhase.FINISHED);
        return state;
    }

    public String helperChat(GameState state, String message, HintLevel level) {
        return helperAiService.chat(state, message, level);
    }

    public String helperHint(GameState state, HintLevel level) {
        return helperAiService.oneShotHint(state, level);
    }

    public void setHelperLevel(GameState state, HintLevel level) {
        if (level == null) throw ApiException.badRequest("LEVEL", "hintLevel 必填");
        state.setHelperHintLevel(level);
    }

    public record RealCaseInfo(String text, String url, String label) {}

    @Transactional(readOnly = true)
    public RealCaseInfo realCase(GameState state, int storyOrder) {
        Story story = storyRepository.findByStoryOrder(storyOrder)
                .orElseThrow(() -> ApiException.notFound("STORY_NOT_FOUND", "找不到故事"));
        // 本則玩過即可看該則真實事件；真結局後全部可看
        boolean unlocked = state.isUnlockedRealCases()
                || state.getCompletedStoryIds().contains(story.getId());
        if (!unlocked) {
            throw ApiException.forbidden("LOCKED", "尚未解鎖此故事的真實事件（需先玩過此故事）");
        }
        String text = story.getRealCaseText();
        if (text == null || text.isBlank()) {
            text = "（此案說明尚未登錄）";
        }
        String url = (story.getRealCaseUrl() == null || story.getRealCaseUrl().isBlank())
                ? null : story.getRealCaseUrl();
        String label = (url == null || story.getRealCaseLabel() == null || story.getRealCaseLabel().isBlank())
                ? null : story.getRealCaseLabel();
        return new RealCaseInfo(text, url, label);
    }

    public List<StoryOption> availableOptions(GameState state) {
        Story story = requireCurrentStory(state);
        return optionRepository.findByStoryIdAndEnabledTrueOrderBySortOrderAsc(story.getId()).stream()
                .filter(o -> !state.getSelectedOptionIds().contains(o.getId()))
                .toList();
    }

    public List<Story> allStories() {
        return storyRepository.findByEnabledTrueOrderByStoryOrderAsc();
    }

    public boolean isUnlocked(GameState state, Story story) {
        if (story.getStoryOrder() == 1) return true;
        return storyRepository.findByStoryOrder(story.getStoryOrder() - 1)
                .map(prev -> state.getCompletedStoryIds().contains(prev.getId()))
                .orElse(false);
    }

    private Story requireCurrentStory(GameState state) {
        if (state.getCurrentStoryId() == null) {
            throw ApiException.conflict("NO_STORY", "尚未選擇故事");
        }
        return storyRepository.findById(state.getCurrentStoryId())
                .orElseThrow(() -> ApiException.notFound("STORY_NOT_FOUND", "故事不存在"));
    }

    private void requirePhase(GameState state, GamePhase... allowed) {
        for (GamePhase p : allowed) {
            if (state.getPhase() == p) return;
        }
        throw ApiException.conflict("BAD_PHASE",
                "目前階段為 " + state.getPhase() + "，無法執行此操作");
    }

    private String script(String key, String fallback) {
        return dialogueScriptRepository.findByScriptKeyAndEnabledTrue(key)
                .map(DialogueScript::getContent)
                .orElse(fallback);
    }
}
