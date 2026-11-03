package com.eladminplus.common.translation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通用翻译覆盖：主表默认语言列承载唯一约束与检索，非默认语言展示值按
 * (entity, entityId, field, locale) 覆盖。默认语言（zh-CN）零额外查询。
 */
@Service
public class TranslationService {

    public static final String DEFAULT_LOCALE = "zh-CN";
    public static final String EN_LOCALE = "en";

    private final TranslationMapper mapper;

    public TranslationService(TranslationMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前请求语言锚定为受支持 locale：en 请求落 en，其余（zh 系与其他）一律缺省中文 */
    public String currentLocale() {
        String tag = LocaleContextHolder.getLocale().toLanguageTag();
        return tag.startsWith("en") ? EN_LOCALE : DEFAULT_LOCALE;
    }

    /** 批量取覆盖值 → Map&lt;entityId, value&gt;；默认语言或不支持的 ids 空集返回空 map，调用方回默认列 */
    public Map<Long, String> overrides(String entity, String field, Collection<Long> entityIds, String locale) {
        if (entityIds.isEmpty() || DEFAULT_LOCALE.equals(locale)) {
            return Map.of();
        }
        return mapper.selectList(Wrappers.<Translation>lambdaQuery()
                        .eq(Translation::getEntity, entity)
                        .eq(Translation::getField, field)
                        .eq(Translation::getLocale, locale)
                        .in(Translation::getEntityId, entityIds))
                .stream()
                .collect(Collectors.toMap(Translation::getEntityId, Translation::getValue));
    }

    /** 单实体覆盖值；无覆盖返回 null */
    public String overrideOf(String entity, Long entityId, String field, String locale) {
        return overrides(entity, field, List.of(entityId), locale).get(entityId);
    }

    /**
     * 全量同步覆盖：value 非空 upsert、null 清除（表单全量提交语义）。须在事务内调用。
     */
    @Transactional
    public void syncOverride(String entity, Long entityId, String field, String locale, String value) {
        if (DEFAULT_LOCALE.equals(locale)) {
            return; // 默认语言写主表默认列，不入覆盖
        }
        Translation existing = mapper.selectOne(Wrappers.<Translation>lambdaQuery()
                .eq(Translation::getEntity, entity)
                .eq(Translation::getEntityId, entityId)
                .eq(Translation::getField, field)
                .eq(Translation::getLocale, locale));
        if (value == null || value.isBlank()) {
            if (existing != null) {
                mapper.deleteById(existing.getId());
            }
            return;
        }
        if (existing != null) {
            existing.setValue(value);
            mapper.updateById(existing);
            return;
        }
        Translation row = new Translation();
        row.setEntity(entity);
        row.setEntityId(entityId);
        row.setField(field);
        row.setLocale(locale);
        row.setValue(value);
        mapper.insert(row);
    }

    /** 实体删除时级联清理其全部覆盖行。须在事务内调用。 */
    @Transactional
    public void removeAll(String entity, Long entityId) {
        mapper.delete(Wrappers.<Translation>lambdaQuery()
                .eq(Translation::getEntity, entity)
                .eq(Translation::getEntityId, entityId));
    }
}
