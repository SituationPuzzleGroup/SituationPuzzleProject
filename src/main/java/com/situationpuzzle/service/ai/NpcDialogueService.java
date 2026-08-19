package com.situationpuzzle.service.ai;

import com.situationpuzzle.domain.Story;
import com.situationpuzzle.domain.StoryOption;
import com.situationpuzzle.service.game.ChatTurn;
import com.situationpuzzle.service.game.GameState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class NpcDialogueService {
    private static final String NPC_SYSTEM_FALLBACK = """
            你是《狹縫之間圖書館》遊戲中的「圖書館館長」NPC——故事的守護者與敘述者。語氣沉穩、略帶神秘，必須使用繁體中文。
            回覆原則：
            1. 「標準答方向」是事實核心：你的回覆不可與它矛盾，但禁止逐字複述；請結合故事氛圍與玩家提問的情境，補充合理的情緒、畫面、鋪陳或反問，讓回應有溫度、貼近情節推進。
            2. 回覆長度 20～150 字，即使事實是「沒有」「不是」也要完整回應，禁止只回兩三個字。
            3. 事實層面不得超出標準答方向與真相卡允許的範圍；氣氛與描述可自由發揮。
            4. 若 can_reveal_truth=false，禁止說出完整謎底或真相卡細節。
            5. 不要提及你是 AI、不要提及分數系統。
            """;

    private final LlmClient llmClient;
    private final PromptTemplateService prompts;

    public NpcDialogueService(LlmClient llmClient, PromptTemplateService prompts) {
        this.llmClient = llmClient;
        this.prompts = prompts;
    }

    public record GeneratedLine(String text, String source) {}

    public GeneratedLine replyToOption(GameState state, Story story, StoryOption option, boolean canRevealTruth) {
        StringBuilder sb = new StringBuilder();
        String source = streamReplyToOption(state, story, option, canRevealTruth, sb::append);
        return new GeneratedLine(sb.toString(), source);
    }

    /**
     * 串流館長回覆。onToken 收到片段；回傳 source = LLM 或 SCRIPT。
     */
    public String streamReplyToOption(
            GameState state,
            Story story,
            StoryOption option,
            boolean canRevealTruth,
            Consumer<String> onToken) {
        String fallback = option.getReplyText();
        String system = prompts.render("NPC_SYSTEM", Map.of(
                "story_title", nullToEmpty(story.getTitle()),
                "can_reveal_truth", String.valueOf(canRevealTruth),
                "truth_card", canRevealTruth ? nullToEmpty(story.getTruthCard()) : "（尚未允許揭曉）"
        ), NPC_SYSTEM_FALLBACK);

        String user = """
                目前故事：%s
                故事結果敘述（玩家已知，可作為氛圍與描述的依據）：%s
                第 %d / %d 輪
                玩家提問：%s
                標準答方向（事實核心——不可矛盾，但請融入情境演繹，不要逐字複述）：%s
                can_reveal_truth=%s
                """.formatted(
                story.getTitle(),
                nullToEmpty(story.getResultText()),
                state.getCurrentRound(),
                state.getMaxRounds(),
                option.getOptionText(),
                option.getReplyText(),
                canRevealTruth
        );

        List<ChatTurn> history = trimHistory(state.getNpcChatHistory(), 6);
        StringBuilder full = new StringBuilder();
        boolean ok = llmClient.isAvailable()
                && llmClient.streamChat(system, history, user, token -> {
                    full.append(token);
                    onToken.accept(token);
                });

        if (!ok || full.isEmpty()) {
            full.setLength(0);
            StreamTextUtil.emitCharsWithDelay(fallback, token -> {
                full.append(token);
                onToken.accept(token);
            }, 18);
            appendHistory(state.getNpcChatHistory(), "user", option.getOptionText());
            appendHistory(state.getNpcChatHistory(), "assistant", full.toString());
            return "SCRIPT";
        }

        String text = full.toString().trim();
        appendHistory(state.getNpcChatHistory(), "user", option.getOptionText());
        appendHistory(state.getNpcChatHistory(), "assistant", text);
        return "LLM";
    }

    private static List<ChatTurn> trimHistory(List<ChatTurn> hist, int max) {
        if (hist == null || hist.isEmpty()) return List.of();
        int from = Math.max(0, hist.size() - max);
        return new ArrayList<>(hist.subList(from, hist.size()));
    }

    private static void appendHistory(List<ChatTurn> hist, String role, String content) {
        hist.add(new ChatTurn(role, content));
        while (hist.size() > 12) {
            hist.remove(0);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
