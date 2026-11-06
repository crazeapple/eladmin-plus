package com.eladminplus.auth.preferences;

import tools.jackson.databind.ObjectMapper;

/**
 * 用户级偏好四项（theme / primaryColor / locale / iconSet），整体存 user.preferences JSON 列。
 * 列 NULL / 内容损坏 / 字段非法一律按缺省回退（读路径不 500）；合法值域在此收口，写路径校验共用。
 */
public record UserPreferences(String theme, String primaryColor, String locale, String iconSet) {

    public static final String DEFAULT_THEME = "auto";
    public static final String DEFAULT_PRIMARY_COLOR = "#5d87ff";
    public static final String DEFAULT_LOCALE = "system";
    public static final String DEFAULT_ICON_SET = "svg";

    /** 合法值域：主题三态、语言三态（system 随浏览器）、图标双库（ep 为 EP 原生图标） */
    public static final java.util.Set<String> THEMES = java.util.Set.of("light", "dark", "auto");
    public static final java.util.Set<String> LOCALES = java.util.Set.of("system", "zh-CN", "en");
    public static final java.util.Set<String> ICON_SETS = java.util.Set.of("svg", "ep");

    /** 主色形态：# 前缀 hex，3 / 6 / 8 位（8 位含透明度） */
    public static final java.util.regex.Pattern COLOR_PATTERN =
            java.util.regex.Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    public static UserPreferences defaults() {
        return new UserPreferences(DEFAULT_THEME, DEFAULT_PRIMARY_COLOR, DEFAULT_LOCALE, DEFAULT_ICON_SET);
    }

    /** 单字段合法判定（写路径逐字段校验与读路径回退共用同一值域） */
    public static boolean validTheme(String value) {
        return value != null && THEMES.contains(value);
    }

    public static boolean validPrimaryColor(String value) {
        return value != null && COLOR_PATTERN.matcher(value).matches();
    }

    public static boolean validLocale(String value) {
        return value != null && LOCALES.contains(value);
    }

    public static boolean validIconSet(String value) {
        return value != null && ICON_SETS.contains(value);
    }

    /**
     * 解析库值原文：NULL / 空 / 损坏 JSON / 字段非法 → 该字段落缺省（整列缺省即全缺省），不抛不 500。
     */
    public static UserPreferences parse(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) {
            return defaults();
        }
        try {
            UserPreferences stored = mapper.readValue(raw, UserPreferences.class);
            if (stored == null) {
                return defaults();
            }
            return new UserPreferences(
                    validTheme(stored.theme) ? stored.theme : DEFAULT_THEME,
                    validPrimaryColor(stored.primaryColor) ? stored.primaryColor : DEFAULT_PRIMARY_COLOR,
                    validLocale(stored.locale) ? stored.locale : DEFAULT_LOCALE,
                    validIconSet(stored.iconSet) ? stored.iconSet : DEFAULT_ICON_SET);
        } catch (Exception corrupt) {
            return defaults();
        }
    }

    /** 序列化为列存 JSON（全量四项，无缺省折叠——写什么读什么，幂等语义直白） */
    public String toJson(ObjectMapper mapper) {
        return mapper.writeValueAsString(this);
    }
}
