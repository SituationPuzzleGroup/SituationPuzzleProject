package com.situationpuzzle.exception;

import com.situationpuzzle.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * 全域例外處理。
 * 靜態資源 404、客戶端中斷連線等「非業務錯誤」降噪，避免誤記成 ERROR。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /** 瀏覽器自動要 favicon 等：回 404，不打 ERROR */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    /**
     * 客戶端重新整理／關閉分頁時中斷下載（webp、SSE 等）。
     * 連線已斷，勿再寫 JSON body。
     */
    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleClientAbort(Exception ex) {
        log.debug("Client aborted request: {}", rootMessage(ex));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex, HttpServletRequest request) {
        if (isClientAbort(ex)) {
            log.debug("Client aborted (wrapped): {}", rootMessage(ex));
            return null; // 連線已斷，不寫回應
        }
        String path = request != null ? request.getRequestURI() : "?";
        log.error("Unhandled error on {}", path, ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("INTERNAL", "伺服器內部錯誤"));
    }

    private static boolean isClientAbort(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof ClientAbortException
                    || t instanceof AsyncRequestNotUsableException) {
                return true;
            }
            if (t instanceof IOException) {
                String m = t.getMessage();
                if (m != null && (m.contains("連線被對方重設")
                        || m.contains("Broken pipe")
                        || m.contains("Connection reset")
                        || m.contains("中止了一個已建立的連線"))) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : ex.getClass().getSimpleName();
    }
}
