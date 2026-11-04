package com.eladminplus.common.error;

import com.eladminplus.common.R;
import com.eladminplus.common.web.ClientIp;
import com.eladminplus.monitor.domain.ExceptionLog;
import com.eladminplus.monitor.service.ExceptionLogService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INTERNAL_MESSAGE_KEY = "error.internal";

    /** exception_log 列容量内的截断上限：message 512（VARCHAR），堆栈 20000（TEXT 且留多字节余量） */
    private static final int MESSAGE_MAX = 512;
    private static final int STACK_MAX = 20_000;

    private final MessageSource messageSource;
    private final ExceptionLogService exceptionLogService;

    public GlobalExceptionHandler(MessageSource messageSource, ExceptionLogService exceptionLogService) {
        this.messageSource = messageSource;
        this.exceptionLogService = exceptionLogService;
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
        recordException(ex);
        String message = messageSource.getMessage(INTERNAL_MESSAGE_KEY, null, "服务内部错误，请稍后再试", locale);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.error(HttpStatus.INTERNAL_SERVER_ERROR, message));
    }

    /** 5xx 系统异常旁路落 exception_log（含堆栈文本，超长截断）；4xx 业务拒绝不落；组装异常吞掉不影响错误响应 */
    private void recordException(Exception ex) {
        try {
            ExceptionLog entry = new ExceptionLog();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            entry.setUsername(
                    authentication != null && authentication.getPrincipal() instanceof String principal
                            ? principal
                            : null);
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                entry.setIp(ClientIp.resolve(attributes.getRequest()));
            }
            entry.setName(ex.getClass().getName());
            entry.setMessage(truncate(ex.getMessage(), MESSAGE_MAX));
            entry.setStackTrace(truncate(stackTextOf(ex), STACK_MAX));
            exceptionLogService.record(entry);
        } catch (Exception e) {
            log.warn("异常日志组装失败：name={}", ex.getClass().getName(), e);
        }
    }

    private static String stackTextOf(Throwable ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    /** 消息码经 MessageSource 解析；不是码则原样直出 */
    private String resolve(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }
}
