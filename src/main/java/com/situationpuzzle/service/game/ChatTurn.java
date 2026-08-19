package com.situationpuzzle.service.game;

import java.io.Serializable;

public class ChatTurn implements Serializable {
    private String role;
    private String content;

    public ChatTurn() {}

    public ChatTurn(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
