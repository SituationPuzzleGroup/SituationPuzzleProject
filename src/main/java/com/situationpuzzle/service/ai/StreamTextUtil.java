package com.situationpuzzle.service.ai;

import java.util.function.Consumer;

/** 腳本備援時的偽串流（逐字送出） */
public final class StreamTextUtil {
    private StreamTextUtil() {}

    public static void emitChars(String text, Consumer<String> onToken) {
        if (text == null || text.isEmpty()) return;
        // 依 Unicode code point 逐字，避免切斷 surrogate
        text.codePoints().forEach(cp -> onToken.accept(new String(Character.toChars(cp))));
    }

    public static void emitCharsWithDelay(String text, Consumer<String> onToken, long delayMs) {
        if (text == null || text.isEmpty()) return;
        text.codePoints().forEach(cp -> {
            onToken.accept(new String(Character.toChars(cp)));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
}
