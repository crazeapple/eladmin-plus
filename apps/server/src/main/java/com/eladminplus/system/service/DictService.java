package com.eladminplus.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.system.domain.Dict;
import com.eladminplus.system.domain.DictDetail;
import com.eladminplus.system.dto.DictDetailSaveRequest;
import com.eladminplus.system.dto.DictItemView;
import com.eladminplus.system.dto.DictSaveRequest;
import com.eladminplus.system.mapper.DictDetailMapper;
import com.eladminplus.system.mapper.DictMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 字典两级管理：字典与明细全 CRUD；字典名全局唯一、同字典 value 唯一（409）；
 * label 多语言 JSON 只在入口校验对象形态，库中原样存取不解析（切语言零重拉契约）。
 */
@Service
public class DictService {

    private final DictMapper dictMapper;
    private final DictDetailMapper dictDetailMapper;
    private final ObjectMapper objectMapper;

    public DictService(DictMapper dictMapper, DictDetailMapper dictDetailMapper, ObjectMapper objectMapper) {
        this.dictMapper = dictMapper;
        this.dictDetailMapper = dictDetailMapper;
        this.objectMapper = objectMapper;
    }

    /** 全量字典（管理页列表，量级小不分页） */
    public List<Dict> list() {
        return dictMapper.selectList(Wrappers.<Dict>lambdaQuery().orderByAsc(Dict::getId));
    }

    @Transactional
    public Long save(DictSaveRequest request) {
        Dict dict = new Dict();
        dict.setId(request.id());
        dict.setName(request.name());
        dict.setDescription(request.description());
        Long dup = dictMapper.selectCount(Wrappers.<Dict>lambdaQuery()
                .eq(Dict::getName, dict.getName())
                .ne(dict.getId() != null, Dict::getId, dict.getId()));
        if (dup > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "error.system.dict-name-duplicate");
        }
        if (dict.getId() == null) {
            dictMapper.insert(dict);
        } else {
            if (dictMapper.selectById(dict.getId()) == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
            }
            // 全量表单提交语义：显式逐列覆盖（描述可清空）
            dictMapper.update(
                    Wrappers.<Dict>lambdaUpdate()
                            .eq(Dict::getId, dict.getId())
                            .set(Dict::getName, dict.getName())
                            .set(Dict::getDescription, dict.getDescription()));
        }
        return dict.getId();
    }

    @Transactional
    public void remove(Long id) {
        if (dictMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        // 服务层级联：先删明细再删主行（与角色删除级联同款顺序语义）
        dictDetailMapper.delete(Wrappers.<DictDetail>lambdaQuery().eq(DictDetail::getDictId, id));
        dictMapper.deleteById(id);
    }

    /** 字典明细全量（管理端编辑；sort 升序、id 兜底稳定序） */
    public List<DictDetail> listDetails(Long dictId) {
        requireDict(dictId);
        return detailsOf(dictId);
    }

    /** 消费端点：按字典名取明细数组（label JSON 原样下发；字典不存在 404） */
    public List<DictItemView> byName(String name) {
        Dict dict = dictMapper.selectOne(Wrappers.<Dict>lambdaQuery().eq(Dict::getName, name));
        if (dict == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        return detailsOf(dict.getId()).stream()
                .map(detail -> new DictItemView(detail.getLabel(), detail.getValue(), detail.getSort()))
                .toList();
    }

    private List<DictDetail> detailsOf(Long dictId) {
        return dictDetailMapper.selectList(Wrappers.<DictDetail>lambdaQuery()
                .eq(DictDetail::getDictId, dictId)
                .orderByAsc(DictDetail::getSort)
                .orderByAsc(DictDetail::getId));
    }

    @Transactional
    public Long saveDetail(Long dictId, DictDetailSaveRequest request) {
        requireDict(dictId);
        checkLabel(request.label());
        DictDetail detail = new DictDetail();
        detail.setId(request.id());
        detail.setDictId(dictId);
        detail.setLabel(request.label());
        detail.setValue(request.value());
        detail.setSort(request.sort() == null ? 0 : request.sort());
        Long dup = dictDetailMapper.selectCount(Wrappers.<DictDetail>lambdaQuery()
                .eq(DictDetail::getDictId, dictId)
                .eq(DictDetail::getValue, detail.getValue())
                .ne(detail.getId() != null, DictDetail::getId, detail.getId()));
        if (dup > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "error.system.dict-value-duplicate");
        }
        if (detail.getId() == null) {
            dictDetailMapper.insert(detail);
        } else {
            DictDetail existing = dictDetailMapper.selectById(detail.getId());
            if (existing == null || !dictId.equals(existing.getDictId())) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
            }
            // 全量表单提交语义：显式逐列覆盖（label 与 value 均可编辑改写）
            dictDetailMapper.update(
                    Wrappers.<DictDetail>lambdaUpdate()
                            .eq(DictDetail::getId, detail.getId())
                            .set(DictDetail::getLabel, detail.getLabel())
                            .set(DictDetail::getValue, detail.getValue())
                            .set(DictDetail::getSort, detail.getSort()));
        }
        return detail.getId();
    }

    @Transactional
    public void removeDetail(Long dictId, Long detailId) {
        requireDict(dictId);
        DictDetail detail = dictDetailMapper.selectById(detailId);
        if (detail == null || !dictId.equals(detail.getDictId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        dictDetailMapper.deleteById(detailId);
    }

    private void requireDict(Long dictId) {
        if (dictMapper.selectById(dictId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
    }

    /** label 须为合法 JSON 且为对象形态（locale → 文案映射，useDict 消费契约） */
    private void checkLabel(String label) {
        try {
            if (!objectMapper.readTree(label).isObject()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "error.system.dict-label-invalid");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "error.system.dict-label-invalid");
        }
    }
}
