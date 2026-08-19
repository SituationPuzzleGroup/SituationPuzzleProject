package com.situationpuzzle.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "story_option")
public class StoryOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    @Column(name = "reply_text", nullable = false, columnDefinition = "TEXT")
    private String replyText;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "score_value", nullable = false)
    private int scoreValue = 20;

    @Column(name = "hint_tag", length = 100)
    private String hintTag;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    public Long getId() { return id; }
    public Long getStoryId() { return storyId; }
    public void setStoryId(Long storyId) { this.storyId = storyId; }
    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }
    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public int getScoreValue() { return scoreValue; }
    public void setScoreValue(int scoreValue) { this.scoreValue = scoreValue; }
    public String getHintTag() { return hintTag; }
    public void setHintTag(String hintTag) { this.hintTag = hintTag; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
