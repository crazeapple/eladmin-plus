package com.eladminplus.common.web;

import com.eladminplus.common.R;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.context.i18n.LocaleContextHolder;
import tools.jackson.databind.ObjectMapper;

/**
 * 过滤器层统一错误响应：不进 DispatcherServlet 的拒绝（认证入口点 / 越权 / 限流等）
 * 走同一 R 结构输出，消息经 MessageSource 本地化。MVC 层异常由 @RestControllerAdvice 兜底。
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {}

    public static void write(
            HttpServletResponse response,
            HttpStatus status,
            String messageKey,
            MessageSource messageSource,
            ObjectMapper objectMapper)
            throws IOException {
        String message =
                messageSource.getMessage(messageKey, null, messageKey, LocaleContextHolder.getLocale());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.error(status, message)));
    }
}
