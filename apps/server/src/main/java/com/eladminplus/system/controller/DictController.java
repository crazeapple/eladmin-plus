package com.eladminplus.system.controller;

import com.eladminplus.common.R;
import com.eladminplus.system.domain.Dict;
import com.eladminplus.system.domain.DictDetail;
import com.eladminplus.system.dto.DictDetailSaveRequest;
import com.eladminplus.system.dto.DictItemView;
import com.eladminplus.system.dto.DictSaveRequest;
import com.eladminplus.system.service.DictService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 字典管理端点：字典两级 CRUD（明细嵌套在字典下）。 */
@RestController
@RequestMapping("/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:dict:list') || hasAuthority('ROLE_ROOT')")
    public R<List<Dict>> list() {
        return R.ok(dictService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> create(@Valid @RequestBody DictSaveRequest request) {
        return R.ok(dictService.save(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DictSaveRequest request) {
        dictService.save(new DictSaveRequest(id, request.name(), request.description()));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        dictService.remove(id);
        return R.ok();
    }

    @GetMapping("/{dictId}/details")
    @PreAuthorize("hasAuthority('system:dict:list') || hasAuthority('ROLE_ROOT')")
    public R<List<DictDetail>> listDetails(@PathVariable Long dictId) {
        return R.ok(dictService.listDetails(dictId));
    }

    /** 消费端点：按字典名取明细数组（认证即可，useDict 全应用消费，不挂字典管理权限点） */
    @GetMapping("/{name}")
    public R<List<DictItemView>> byName(@PathVariable String name) {
        return R.ok(dictService.byName(name));
    }

    @PostMapping("/{dictId}/details")
    @PreAuthorize("hasAuthority('system:dict:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> createDetail(
            @PathVariable Long dictId, @Valid @RequestBody DictDetailSaveRequest request) {
        return R.ok(dictService.saveDetail(dictId, request));
    }

    @PutMapping("/{dictId}/details/{detailId}")
    @PreAuthorize("hasAuthority('system:dict:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> updateDetail(
            @PathVariable Long dictId,
            @PathVariable Long detailId,
            @Valid @RequestBody DictDetailSaveRequest request) {
        dictService.saveDetail(dictId, new DictDetailSaveRequest(
                detailId, request.label(), request.value(), request.sort()));
        return R.ok();
    }

    @DeleteMapping("/{dictId}/details/{detailId}")
    @PreAuthorize("hasAuthority('system:dict:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> removeDetail(@PathVariable Long dictId, @PathVariable Long detailId) {
        dictService.removeDetail(dictId, detailId);
        return R.ok();
    }
}
