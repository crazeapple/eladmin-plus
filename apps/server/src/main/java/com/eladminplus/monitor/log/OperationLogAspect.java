package com.eladminplus.monitor.log;

import com.eladminplus.common.web.ClientIp;
import com.eladminplus.monitor.domain.OperationLog;
import com.eladminplus.monitor.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * @Log 操作日志切面：环绕计时后组装五字段（操作人、IP、描述、耗时、请求参数）交异步落表。
 * 记录面只随显式标注展开；切面自身任何异常吞掉只记日志，不干扰业务响应。
 * 参数敏感字段（键名含 password）值掩码入库；非 JSON 请求体落描述性占位。
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    /** password 类字段值的统一掩码 */
    private static final String PASSWORD_MASK = "******";

    /** 非 JSON 请求体（文件上传等）的描述性占位 */
    private static final String NON_JSON_PLACEHOLDER = "[非 JSON 请求体，参数未入库]";

    /** 参数序列化失败时的降级占位（该条操作记录的其余字段照常落库） */
    private static final String SERIALIZATION_PLACEHOLDER = "[参数序列化失败]";

    private static final int DESCRIPTION_MAX = 255;

    /** request_param 列为 TEXT，截断上限留足余量 */
    private static final int PARAM_MAX = 20_000;

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogService operationLogService, ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, Log operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            save(joinPoint, operationLog.value(), System.currentTimeMillis() - start);
        }
    }

    /** 组装并交异步落表；组装异常吞掉（旁路记录不得影响业务响应） */
    private void save(ProceedingJoinPoint joinPoint, String description, long elapsedMs) {
        try {
            OperationLog entry = new OperationLog();
            entry.setUsername(currentUsername());
            entry.setDescription(truncate(description, DESCRIPTION_MAX));
            entry.setElapsedMs(elapsedMs);
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                entry.setIp(ClientIp.resolve(attributes.getRequest()));
            }
            entry.setRequestParam(requestParams(joinPoint));
            operationLogService.record(entry);
        } catch (Exception e) {
            log.error("操作日志组装失败：description={}", description, e);
        }
    }

    /** 操作人取会话认证主体（用户名）；无认证态（匿名端点）留空 */
    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof String principal
                ? principal
                : null;
    }

    /** 请求参数入库文本：Servlet / 文件参数视为非 JSON 请求体裁占位；序列化失败降级占位 */
    private String requestParams(ProceedingJoinPoint joinPoint) {
        boolean nonJson = false;
        List<Object> payload = new ArrayList<>();
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof BindingResult
                    || arg instanceof MultipartFile) {
                nonJson = true;
                continue;
            }
            payload.add(arg);
        }
        if (nonJson) {
            return NON_JSON_PLACEHOLDER;
        }
        if (payload.isEmpty()) {
            return null;
        }
        try {
            Object body = payload.size() == 1 ? payload.get(0) : payload;
            JsonNode node = objectMapper.valueToTree(body);
            maskPasswords(node);
            return truncate(objectMapper.writeValueAsString(node), PARAM_MAX);
        } catch (Exception e) {
            log.warn("操作日志参数序列化失败，落占位：{}", e.getMessage());
            return SERIALIZATION_PLACEHOLDER;
        }
    }

    /** 递归掩码：键名含 password 的字段值置统一掩码（大小写不敏感，嵌套与数组内同样生效） */
    private void maskPasswords(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (entry.getKey().toLowerCase().contains("password")) {
                    ((ObjectNode) node).set(entry.getKey(), StringNode.valueOf(PASSWORD_MASK));
                } else {
                    maskPasswords(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::maskPasswords);
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
