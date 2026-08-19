package com.situationpuzzle.service.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.situationpuzzle.config.CookieProperties;
import com.situationpuzzle.service.game.ChatTurn;
import com.situationpuzzle.service.game.GameState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 入站：從 cookie + request body 重建無狀態的 {@link GameState}。
 *
 * <p>所有 {@code /api/**} 請求最早期執行：
 * <ol>
 *   <li>以 {@link CachedBodyHttpServletRequestWrapper} 包裝 request，讓 body 可被
 *       本 filter 與後續 {@code @RequestBody} 重複讀取。</li>
 *   <li>讀 {@code sp_core} cookie → {@link GameStateCodec#decode} → {@link ProgressCore}；
 *       解碼失敗／無 cookie 視同無進度（不採納）。</li>
 *   <li>從 body 取 {@code _history.npcChat}/{@code _history.helperChat} 填回兩條對話紀錄
 *       （GET 無 body → 空 list）。</li>
 *   <li>合成 {@link GameState} 並 {@link GameContext#adopt} 進 request scope。</li>
 * </ol>
 * 出站的 envelope 由 {@code StateEnvelopeAdvice}（JSON）與 controller（SSE done）負責。
 */
@Component
public class StateReconstructionFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(StateReconstructionFilter.class);

    private final GameStateCodec codec;
    private final CookieProperties cookieProperties;
    private final ObjectMapper mapper;
    private final GameContext gameContext;

    public StateReconstructionFilter(GameStateCodec codec, CookieProperties cookieProperties,
                                     ObjectMapper mapper, GameContext gameContext) {
        this.codec = codec;
        this.cookieProperties = cookieProperties;
        this.mapper = mapper;
        this.gameContext = gameContext;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 僅對 API 路徑重建狀態（靜態資源 / 首頁不需要，也不需要可重讀 body）
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequestWrapper wrapped = new CachedBodyHttpServletRequestWrapper(request);

        ProgressCore core = readCoreCookie(wrapped);
        if (core != null) {
            GameState state = new GameState();
            core.mergeInto(state);
            applyHistory(state, wrapped.cachedBody());
            gameContext.adopt(state);
        }

        filterChain.doFilter(wrapped, response);
    }

    private ProgressCore readCoreCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        String name = cookieProperties.getCookieName();
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return codec.decode(c.getValue()).orElse(null);
            }
        }
        return null;
    }

    /** 從 body 的 {@code _history} 取兩條對話紀錄填回 state（非 JSON / 無 _history → 維持空）。 */
    private void applyHistory(GameState state, byte[] body) {
        if (body == null || body.length == 0) return;
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode h = root.get("_history");
            if (h == null) return;
            List<ChatTurn> npc = readTurns(h.get("npcChat"));
            List<ChatTurn> helper = readTurns(h.get("helperChat"));
            if (npc != null) state.setNpcChatHistory(npc);
            if (helper != null) state.setHelperChatHistory(helper);
        } catch (Exception e) {
            // 非 JSON body（如 GET 或表單）：忽略，history 維持空 list
            log.debug("解析 _history 失敗（忽略）：{}", e.getMessage());
        }
    }

    private List<ChatTurn> readTurns(JsonNode node) {
        if (node == null || !node.isArray()) return null;
        List<ChatTurn> list = new ArrayList<>();
        for (JsonNode e : node) {
            String role = e.hasNonNull("role") ? e.get("role").asText() : "user";
            String content = e.hasNonNull("content") ? e.get("content").asText() : "";
            list.add(new ChatTurn(role, content));
        }
        return list;
    }

    /**
     * 把 request body 完整讀進 byte[]，{@code getInputStream()} 每次回傳從頭開始的新串流，
     * 讓 filter 與 {@code @RequestBody} 都能各自讀一次。
     */
    static final class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        byte[] cachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedServletInputStream(cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    /** 包 ByteArrayInputStream 的 ServletInputStream（每次 getInputStream() 新建一個，可重讀）。 */
    private static final class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        CachedServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }
}
