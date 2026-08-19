package com.situationpuzzle.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_prompt_template")
public class AiPromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false, unique = true, length = 100)
    private String templateKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    public Long getId() { return id; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
