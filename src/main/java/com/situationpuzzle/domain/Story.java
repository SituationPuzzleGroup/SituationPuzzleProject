package com.situationpuzzle.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "story")
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "story_order", nullable = false, unique = true)
    private Integer storyOrder;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "result_text", nullable = false, columnDefinition = "TEXT")
    private String resultText;

    @Column(name = "ask_prompt_text", columnDefinition = "TEXT")
    private String askPromptText;

    @Column(name = "low_score_summary", nullable = false, columnDefinition = "TEXT")
    private String lowScoreSummary;

    @Column(name = "high_score_summary", nullable = false, columnDefinition = "TEXT")
    private String highScoreSummary;

    @Column(name = "finish_text", columnDefinition = "TEXT")
    private String finishText;

    @Column(name = "truth_score_threshold", nullable = false)
    private Integer truthScoreThreshold = 60;

    /** 僅後端使用，不下發前端 */
    @Column(name = "truth_card", columnDefinition = "TEXT")
    private String truthCard;

    @Column(name = "real_case_text", columnDefinition = "TEXT")
    private String realCaseText;

    /** 真實事件延伸閱讀連結（公開百科／報導等） */
    @Column(name = "real_case_url", length = 500)
    private String realCaseUrl;

    /** 連結按鈕顯示名稱 */
    @Column(name = "real_case_label", length = 200)
    private String realCaseLabel;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    public Long getId() { return id; }
    public Integer getStoryOrder() { return storyOrder; }
    public void setStoryOrder(Integer storyOrder) { this.storyOrder = storyOrder; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public String getAskPromptText() { return askPromptText; }
    public void setAskPromptText(String askPromptText) { this.askPromptText = askPromptText; }
    public String getLowScoreSummary() { return lowScoreSummary; }
    public void setLowScoreSummary(String lowScoreSummary) { this.lowScoreSummary = lowScoreSummary; }
    public String getHighScoreSummary() { return highScoreSummary; }
    public void setHighScoreSummary(String highScoreSummary) { this.highScoreSummary = highScoreSummary; }
    public String getFinishText() { return finishText; }
    public void setFinishText(String finishText) { this.finishText = finishText; }
    public Integer getTruthScoreThreshold() { return truthScoreThreshold; }
    public void setTruthScoreThreshold(Integer truthScoreThreshold) { this.truthScoreThreshold = truthScoreThreshold; }
    public String getTruthCard() { return truthCard; }
    public void setTruthCard(String truthCard) { this.truthCard = truthCard; }
    public String getRealCaseText() { return realCaseText; }
    public void setRealCaseText(String realCaseText) { this.realCaseText = realCaseText; }
    public String getRealCaseUrl() { return realCaseUrl; }
    public void setRealCaseUrl(String realCaseUrl) { this.realCaseUrl = realCaseUrl; }
    public String getRealCaseLabel() { return realCaseLabel; }
    public void setRealCaseLabel(String realCaseLabel) { this.realCaseLabel = realCaseLabel; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
