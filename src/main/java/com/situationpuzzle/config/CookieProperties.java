package com.situationpuzzle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 進度核心 cookie（sp_core）設定。
 *
 * <p>狀態無狀態化後，遊戲進度核心以 HMAC 簽章 + AES-GCM 加密塞進 cookie，
 * 由 client 持有。此處只放 cookie 名稱／壽命與用於簽章加密的 master secret。
 *
 * <p>secret 請用環境變數 {@code GAME_COOKIE_SECRET}（本機 .env，勿提交版控）。
 * 為空時啟動隨機產生（僅開發用，重啟即失效，所有既有 cookie 失效）。
 */
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {
    /** 簽章/加密用 master secret；空字串代表「未設定」。 */
    private String secret = "";
    /** cookie 名稱。 */
    private String cookieName = "sp_core";
    /** cookie 壽命（秒），預設 2 小時，與原 session timeout 對齊。 */
    private int maxAgeSec = 7200;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getCookieName() { return cookieName; }
    public void setCookieName(String cookieName) { this.cookieName = cookieName; }
    public int getMaxAgeSec() { return maxAgeSec; }
    public void setMaxAgeSec(int maxAgeSec) { this.maxAgeSec = maxAgeSec; }
}
