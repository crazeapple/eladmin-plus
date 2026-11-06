package com.eladminplus.auth.preferences;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.common.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 用户级偏好读写服务：读按库值解析（NULL / 损坏回缺省），写全量四项幂等落列。
 * 写前逐字段校验，非法 400 且报错点名全部非法字段（库值不动）。
 */
@Service
public class PreferencesService {

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public PreferencesService(UserMapper userMapper, ObjectMapper objectMapper, MessageSource messageSource) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    /** 读：库值原文解析，NULL / 损坏 / 字段非法均回缺省 */
    public UserPreferences of(User user) {
        return UserPreferences.parse(user.getPreferences(), objectMapper);
    }

    /** 写：全量四项幂等覆盖（只动 preferences 列），非法值 400 逐字段点名 */
    public void update(Long userId, PreferencesUpdateRequest request) {
        List<String> invalid = new ArrayList<>();
        if (!UserPreferences.validTheme(request.theme())) {
            invalid.add(fieldReason("theme", "error.preferences.theme-invalid"));
        }
        if (!UserPreferences.validPrimaryColor(request.primaryColor())) {
            invalid.add(fieldReason("primaryColor", "error.preferences.primary-color-invalid"));
        }
        if (!UserPreferences.validLocale(request.locale())) {
            invalid.add(fieldReason("locale", "error.preferences.locale-invalid"));
        }
        if (!UserPreferences.validIconSet(request.iconSet())) {
            invalid.add(fieldReason("iconSet", "error.preferences.icon-set-invalid"));
        }
        if (!invalid.isEmpty()) {
            // 逐字段组合消息（服务期已本地化），处理器对非 key 文本原样直出
            throw new BusinessException(HttpStatus.BAD_REQUEST, String.join(separator(), invalid));
        }
        String json = new UserPreferences(
                request.theme(),
                request.primaryColor(),
                request.locale(),
                request.iconSet())
                .toJson(objectMapper);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getPreferences, json));
    }

    /** 单字段报错片段：字段名 + 请求 locale 的原因文案 */
    private String fieldReason(String field, String reasonKey) {
        Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        return field + " " + messageSource.getMessage(reasonKey, null, reasonKey, locale);
    }

    /** 组合分隔符随请求语言（中文全角分号，其余半角） */
    private static String separator() {
        return Locale.SIMPLIFIED_CHINESE.getLanguage()
                .equals(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage())
                ? "；"
                : "; ";
    }
}
