package com.situationpuzzle.service.ai;

import com.situationpuzzle.domain.Story;
import com.situationpuzzle.domain.StoryOption;
import com.situationpuzzle.repository.DialogueScriptRepository;
import com.situationpuzzle.repository.StoryOptionRepository;
import com.situationpuzzle.repository.StoryRepository;
import com.situationpuzzle.service.game.ChatTurn;
import com.situationpuzzle.service.game.GameState;
import com.situationpuzzle.service.game.HintLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class HelperAiService {
    private static final String HELPER_SYSTEM_FALLBACK = """
            你是《狹縫之間圖書館》遊戲右側的玩家助理精靈（像早期 Office 迴紋針），站在玩家一邊。必須使用繁體中文，口吻親切口語，認真完整地回答玩家；回覆長度 15～50 字。
            你可以給提示；依 hint_level 決定洩漏程度。不要假裝自己是館長 NPC。
            你的提示不影響分數。
            """;

    /** 遊戲簡介（尚未進入故事時注入，讓精靈能正確介紹玩法） */
    private static final String GAME_INTRO = """
            《狹縫之間圖書館》：玩家掉進時空縫隙裡的一座圖書館。目標是玩完四則故事猜謎，\
            才能從縫隙回到原來的世界；沒有通關就會一直留在縫隙裡。玩法：館長會先講出故事的「結果」，\
            玩家每則故事有四次提問機會（點畫面上的選項），問得越接近因果，越可能揭曉謎底；\
            正確提問每題 20 分，單則 60 分以上館長會說出真相。四則都完成後，畫面中央會出現「通往世界」按鈕，\
            按下去就會播放開門動畫並回到現實世界。另外，每則故事都改編自真實事件，\
            玩過的故事卡下方會露出「聆聽故事起源」，點擊可以看該則對應的真實事件介紹與連結。""";

    /** 各階段的畫面導覽：畫面上有什麼、玩家可以做什麼 */
    private static String phaseGuide(com.situationpuzzle.service.game.GamePhase phase) {
        if (phase == null) return "";
        return switch (phase) {
            case TITLE, FINISHED -> """
                    現在是標題頁：中央有標題卡，下方有「進入遊戲」與「製作名單」兩顆按鈕。\
                    「製作名單」裡可以看到人物繪製、程式開發的作者、素材與服務的感謝名單、\
                    使用的技術，以及原始碼連結。玩家還沒開始遊戲。""";
            case INTRO -> """
                    現在是開場：館長正在說明背景與遊戲方式，點擊底部對話框可以推進對話。""";
            case STORY_MENU -> """
                    現在是故事選單：畫面右側是故事一、故事二，左側是故事三、故事四\
                    （要照順序解鎖，還不能玩的會顯示鎖頭）。已經玩過的故事，卡片下方會露出\
                    「聆聽故事起源」的小籤條，點它可以看到該則故事對應的真實事件。\
                    全部完成後，畫面正中央會出現「通往世界」按鈕。""";
            case LEAVE_HINT -> """
                    四則故事都完成了：畫面正中央有「通往世界」按鈕，按下去就會迎接結局。""";
            case STORY_RESULT -> """
                    現在館長剛講完這則故事的「結果」，點擊對話框會進入提問階段。""";
            case ASK_PROMPT, SELECT_OPTION -> """
                    現在是提問階段：畫面左右兩側共有八個提問選項，點擊任何一個，\
                    館長就會回答；每則故事只能問四次，要斟酌。""";
            case OPTION_REPLY -> """
                    館長正在回答（或剛回答完）玩家的提問，點擊對話框可以繼續。""";
            case STORY_SUMMARY -> """
                    這則故事剛結束，正在顯示結果與分數，點擊對話框會回到故事選單。""";
            case ENDING -> """
                    現在是結局：館長說完最後的話之後，點擊對話框會播放開門動畫，玩家將回到現實世界。""";
        };
    }

    private final LlmClient llmClient;
    private final PromptTemplateService prompts;
    private final StoryRepository storyRepository;
    private final StoryOptionRepository optionRepository;
    private final DialogueScriptRepository dialogueScriptRepository;

    public HelperAiService(
            LlmClient llmClient,
            PromptTemplateService prompts,
            StoryRepository storyRepository,
            StoryOptionRepository optionRepository,
            DialogueScriptRepository dialogueScriptRepository) {
        this.llmClient = llmClient;
        this.prompts = prompts;
        this.storyRepository = storyRepository;
        this.optionRepository = optionRepository;
        this.dialogueScriptRepository = dialogueScriptRepository;
    }

    public record StreamResult(String text, String source) {}

    public String chat(GameState state, String message, HintLevel levelOverride) {
        StringBuilder sb = new StringBuilder();
        streamChat(state, message, levelOverride, sb::append);
        return sb.toString();
    }

    public String oneShotHint(GameState state, HintLevel levelOverride) {
        return chat(state, "請依目前進度給我一則提示（繁體中文）。", levelOverride);
    }

    public StreamResult streamHint(GameState state, HintLevel levelOverride, Consumer<String> onToken) {
        return streamChat(state, "請依目前進度給我一則提示（繁體中文）。", levelOverride, onToken);
    }

    public StreamResult streamChat(
            GameState state,
            String message,
            HintLevel levelOverride,
            Consumer<String> onToken) {
        HintLevel level = levelOverride != null ? levelOverride : state.getHelperHintLevel();
        if (level == HintLevel.OFF) {
            String off = "（提示已關閉。把等級調高一點我才會開口喔。）";
            StreamTextUtil.emitCharsWithDelay(off, onToken, 12);
            state.setLastHelperBubble(off);
            return new StreamResult(off, "SCRIPT");
        }

        String fallback = dialogueScriptRepository.findByScriptKeyAndEnabledTrue("HELPER_FALLBACK")
                .map(d -> d.getContent())
                .orElse("我現在有點迷糊……你可以先從「誰的認知出了問題」這個方向想想看？");

        ContextPack ctx = buildContext(state, level);
        String scriptText = scriptHint(level, ctx, fallback);

        String system = prompts.render("HELPER_SYSTEM", Map.of(
                "hint_level", level.name(),
                "story_title", nullToEmpty(ctx.storyTitle),
                "truth_card", level.ordinal() >= HintLevel.HIGH.ordinal()
                        ? nullToEmpty(ctx.truthCard)
                        : "（等級不足，不提供完整真相）"
        ), HELPER_SYSTEM_FALLBACK);

        String user = """
                hint_level=%s
                phase=%s
                round=%s/%s
                story=%s
                scene_guide=%s
                %sremaining_hint_tags=%s
                correct_directions_if_allowed=%s
                玩家說：%s
                請用繁體中文回答，長度 15～50 字。
                """.formatted(
                level.name(),
                state.getPhase(),
                state.getCurrentRound(),
                state.getMaxRounds(),
                nullToEmpty(ctx.storyTitle),
                phaseGuide(state.getPhase()),
                ctx.gameIntro ? "game_intro=" + GAME_INTRO + "\n" : "",
                ctx.remainingTags,
                level.ordinal() >= HintLevel.MID.ordinal() ? ctx.correctHints : "(不提供)",
                message == null ? "給我一點提示" : message
        );

        List<ChatTurn> history = trim(state.getHelperChatHistory(), 6);
        StringBuilder full = new StringBuilder();
        boolean ok = llmClient.isAvailable()
                && llmClient.streamChat(system, history, user, token -> {
                    full.append(token);
                    onToken.accept(token);
                });

        String text;
        String source;
        if (!ok || full.isEmpty()) {
            full.setLength(0);
            StreamTextUtil.emitCharsWithDelay(scriptText, token -> {
                full.append(token);
                onToken.accept(token);
            }, 14);
            text = full.toString();
            source = "SCRIPT";
        } else {
            text = full.toString().trim();
            source = "LLM";
        }

        append(state.getHelperChatHistory(), "user", message == null ? "提示" : message);
        append(state.getHelperChatHistory(), "assistant", text);
        state.setLastHelperBubble(text);
        return new StreamResult(text, source);
    }

    private String scriptHint(HintLevel level, ContextPack ctx, String fallback) {
        return switch (level) {
            case OFF -> "提示已關閉。";
            case LOW -> "想想看：故事裡「誰的認知」可能有問題？";
            case MID -> ctx.remainingTags.isBlank()
                    ? "線索標籤暫時沒了，試著回顧已問過的答案之間的矛盾。"
                    : "可以注意這些方向：" + ctx.remainingTags;
            case HIGH, SPOILER -> !ctx.correctHints.isBlank()
                    ? "偷偷跟你說，正確方向大概是：" + ctx.correctHints
                    : fallback;
        };
    }

    private ContextPack buildContext(GameState state, HintLevel level) {
        ContextPack p = new ContextPack();
        if (state.getCurrentStoryId() == null) {
            p.storyTitle = "（尚未進入故事）";
            p.gameIntro = true; // 沒進故事：帶遊戲簡介，讓精靈能正確介紹玩法與畫面
            return p;
        }
        Story story = storyRepository.findById(state.getCurrentStoryId()).orElse(null);
        if (story == null) return p;
        p.storyTitle = story.getTitle();
        p.truthCard = story.getTruthCard();
        List<StoryOption> options = optionRepository.findByStoryIdAndEnabledTrueOrderBySortOrderAsc(story.getId());
        List<StoryOption> remaining = options.stream()
                .filter(o -> !state.getSelectedOptionIds().contains(o.getId()))
                .toList();
        p.remainingTags = remaining.stream()
                .map(StoryOption::getHintTag)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.joining("、"));
        p.correctHints = remaining.stream()
                .filter(StoryOption::isCorrect)
                .map(o -> o.getHintTag() != null ? o.getHintTag() : o.getOptionText())
                .collect(Collectors.joining("；"));
        if (level == HintLevel.SPOILER && story.getTruthCard() != null) {
            p.correctHints = story.getTruthCard();
        }
        return p;
    }

    private static List<ChatTurn> trim(List<ChatTurn> hist, int max) {
        if (hist == null || hist.isEmpty()) return List.of();
        int from = Math.max(0, hist.size() - max);
        return new ArrayList<>(hist.subList(from, hist.size()));
    }

    private static void append(List<ChatTurn> hist, String role, String content) {
        hist.add(new ChatTurn(role, content));
        while (hist.size() > 12) hist.remove(0);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static class ContextPack {
        String storyTitle = "";
        String truthCard = "";
        String remainingTags = "";
        String correctHints = "";
        boolean gameIntro = false;
    }
}
