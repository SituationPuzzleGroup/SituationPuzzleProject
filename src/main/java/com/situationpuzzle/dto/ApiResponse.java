package com.situationpuzzle.dto;

import java.util.Map;

public class ApiResponse<T> {
    private boolean ok;
    private T data;
    private Map<String, String> error;
    /** 無狀態化：前端據此寫回 cookie/sessionStorage。無進度時為 null（non_null 不外溢）。 */
    private StateEnvelope state;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.ok = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.ok = false;
        r.error = Map.of("code", code, "message", message);
        return r;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Map<String, String> getError() { return error; }
    public void setError(Map<String, String> error) { this.error = error; }
    public StateEnvelope getState() { return state; }
    public void setState(StateEnvelope state) { this.state = state; }
}
