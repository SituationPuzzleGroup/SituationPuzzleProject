package com.situationpuzzle.web;

import com.situationpuzzle.dto.ApiResponse;
import com.situationpuzzle.service.state.GameContext;
import com.situationpuzzle.service.state.StateEnvelopeBuilder;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 出站 JSON：凡 controller 回傳 {@link ApiResponse}，且本請求帶有效進度
 * （{@link GameContext#isPresent()}），就把最新 {@code state} envelope 塞進回應，
 * 供前端寫回 cookie（{@code sp_core}）與 sessionStorage（{@code sp_history}）。
 *
 * <p>必須以 {@link ControllerAdvice} 標註 — Spring MVC 透過 {@code ControllerAdviceBean}
 * 機制顯式發現 ResponseBodyAdvice 並套用；單純 {@code @Component} 雖在容器內，
 * 但 {@code RequestMappingHandlerAdapter} 不保證收集（時機敏感，Spring Boot 3.x 易漏），
 * 將導致 {@code supports/beforeBodyWrite} 從不被呼叫。
 *
 * <p>SSE 端點回傳 void、自行手寫 response，不經此 advice — 其 {@code done} event 另行
 * 內嵌同一 envelope（因 SSE 串流提早 commit，無法寫 Set-Cookie）。
 */
@ControllerAdvice
public class StateEnvelopeAdvice implements ResponseBodyAdvice<Object> {
    private final GameContext gameContext;
    private final StateEnvelopeBuilder builder;

    public StateEnvelopeAdvice(GameContext gameContext, StateEnvelopeBuilder builder) {
        this.gameContext = gameContext;
        this.builder = builder;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return ApiResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> api && gameContext.isPresent()) {
            api.setState(builder.build(gameContext.state()));
        }
        return body;
    }
}
