package com.eladminplus.common.error;

import com.eladminplus.common.R;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INTERNAL_MESSAGE_KEY = "error.internal";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusiness(BusinessException ex, Locale locale) {
        return ResponseEntity.status(ex.getStatus())
                .body(R.error(ex.getStatus(), resolve(ex.getMessage(), locale)));
    }

    /** 方法级 @PreAuthorize 的拒绝在 MVC 层抛出，须在此转 403（否则落入未知异常成 500） */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.error(HttpStatus.FORBIDDEN, resolve("error.auth.forbidden", locale)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnknown(Exception ex, Locale locale) {
        // 未知异常：堆栈进日志，响应只回通用消息，不泄内部细节；locale 与请求一致
        log.error("未处理异常", ex);
        String message = messageSource.getMessage(INTERNAL_MESSAGE_KEY, null, "服务内部错误，请稍后再试", locale);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.error(HttpStatus.INTERNAL_SERVER_ERROR, message));
    }

    /** 消息码经 MessageSource 解析；不是码则原样直出 */
    private String resolve(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }
}
