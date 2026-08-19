package com.situationpuzzle.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "dialogue_script")
public class DialogueScript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_key", nullable = false, unique = true, length = 100)
    private String scriptKey;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    public Long getId() { return id; }
    public String getScriptKey() { return scriptKey; }
    public void setScriptKey(String scriptKey) { this.scriptKey = scriptKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
