package com.eladminplus.auth.preferences;

/** 偏好更新请求：全量四项（幂等，无部分更新语义）。 */
public record PreferencesUpdateRequest(String theme, String primaryColor, String locale, String iconSet) {}
