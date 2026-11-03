package com.eladminplus.system.controller;

import com.eladminplus.common.R;
import com.eladminplus.system.dto.DeptSaveRequest;
import com.eladminplus.system.dto.DeptTreeNode;
import com.eladminplus.system.service.DeptService;
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

/** 部门树管理端点。 */
@RestController
@RequestMapping("/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:dept:list') || hasAuthority('ROLE_ROOT')")
    public R<List<DeptTreeNode>> tree() {
        return R.ok(deptService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> create(@Valid @RequestBody DeptSaveRequest request) {
        return R.ok(deptService.save(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DeptSaveRequest request) {
        deptService.save(new DeptSaveRequest(
                id, request.parentId(), request.name(), request.nameEn(), request.sort(), request.enabled()));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        deptService.remove(id);
        return R.ok();
    }
}
