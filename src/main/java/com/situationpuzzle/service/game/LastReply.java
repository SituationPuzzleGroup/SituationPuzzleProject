package com.situationpuzzle.service.game;

import java.io.Serializable;

public class LastReply implements Serializable {
    private Long optionId;
    private String replyText;
    private int scoreDelta;
    private int storyScore;
    private String source; // LLM | SCRIPT

    public LastReply() {}

    public LastReply(Long optionId, String replyText, int scoreDelta, int storyScore, String source) {
        this.optionId = optionId;
        this.replyText = replyText;
        this.scoreDelta = scoreDelta;
        this.storyScore = storyScore;
        this.source = source;
    }

    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }
    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }
    public int getScoreDelta() { return scoreDelta; }
    public void setScoreDelta(int scoreDelta) { this.scoreDelta = scoreDelta; }
    public int getStoryScore() { return storyScore; }
    public void setStoryScore(int storyScore) { this.storyScore = storyScore; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
