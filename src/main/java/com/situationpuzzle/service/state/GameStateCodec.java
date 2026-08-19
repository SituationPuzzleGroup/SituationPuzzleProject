package com.situationpuzzle.service.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.situationpuzzle.config.CookieProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * {@link ProgressCore} 的加密＋簽章 codec。
 *
 * <p>token 格式（Base64url, no padding）＝ {@code HMAC(32) ‖ IV(12) ‖ cipher+tag}：
 * <ol>
 *   <li>ProgressCore → Jackson JSON bytes</li>
 *   <li>AES-256-GCM 加密（隨機 12B IV，128-bit auth tag 附於密文尾）</li>
 *   <li>HMAC-SHA256(authKey, IV‖cipher) 簽章（32B）附於最前</li>
 * </ol>
 * 解碼先以常數時間比對驗章，再以 GCM 解密（tag 驗證）；任一失敗視同無有效 cookie。
 *
 * <p>兩把子鑰從同一 master secret 推導：{@code encKey=SHA256(secret‖"enc")}、
 * {@code authKey=SHA256(secret‖"auth")}。master secret 來自 {@code app.cookie.secret}
 *（環境變數 GAME_COOKIE_SECRET）；為空時啟動隨機產生（僅開發用，重啟即失效）。
 */
@Component
public class GameStateCodec {
    private static final Logger log = LoggerFactory.getLogger(GameStateCodec.class);
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final int MAC_LEN = 32;

    private final ObjectMapper mapper;
    private final byte[] encKey;
    private final byte[] authKey;
    private final SecureRandom random = new SecureRandom();

    public GameStateCodec(CookieProperties props, ObjectMapper mapper) {
        this.mapper = mapper;
        byte[] secret = deriveSecret(props);
        this.encKey = sha256(secret, "enc");
        this.authKey = sha256(secret, "auth");
    }

    private static byte[] deriveSecret(CookieProperties props) {
        String s = props.getSecret();
        if (s != null && !s.isBlank()) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
        byte[] rnd = new byte[32];
        new SecureRandom().nextBytes(rnd);
        log.warn("app.cookie.secret 未設定，隨機產生 master secret（僅開發用；"
                + "重啟後所有既有 sp_core cookie 將失效）。請設定環境變數 GAME_COOKIE_SECRET。");
        return rnd;
    }

    private static byte[] sha256(byte[] secret, String suffix) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(secret);
            md.update(suffix.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public String encode(ProgressCore core) {
        try {
            byte[] plain = mapper.writeValueAsBytes(core);
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain);
            byte[] mac = hmac(iv, ct);
            byte[] token = new byte[mac.length + iv.length + ct.length];
            System.arraycopy(mac, 0, token, 0, mac.length);
            System.arraycopy(iv, 0, token, mac.length, iv.length);
            System.arraycopy(ct, 0, token, mac.length + iv.length, ct.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (Exception e) {
            throw new IllegalStateException("encode state 失敗", e);
        }
    }

    /** 解碼；任何格式／簽章／解密失敗皆回 {@link Optional#empty()}（視同無有效 cookie）。 */
    public Optional<ProgressCore> decode(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            byte[] raw = Base64.getUrlDecoder().decode(token);
            if (raw.length < MAC_LEN + IV_LEN + 1) return Optional.empty();
            byte[] mac = Arrays.copyOfRange(raw, 0, MAC_LEN);
            byte[] iv = Arrays.copyOfRange(raw, MAC_LEN, MAC_LEN + IV_LEN);
            byte[] ct = Arrays.copyOfRange(raw, MAC_LEN + IV_LEN, raw.length);
            // 常數時間比對簽章；不符即丟
            if (!MessageDigest.isEqual(mac, hmac(iv, ct))) return Optional.empty();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ct);
            return Optional.of(mapper.readValue(plain, ProgressCore.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(byte[] iv, byte[] ct) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(authKey, "HmacSHA256"));
        mac.update(iv);
        return mac.doFinal(ct);
    }
}
