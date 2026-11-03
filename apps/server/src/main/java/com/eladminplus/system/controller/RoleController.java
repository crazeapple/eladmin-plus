package com.eladminplus.system.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.system.dto.RoleSaveRequest;
import com.eladminplus.system.dto.RoleView;
import com.eladminplus.auth.session.MenuTreeService;
import com.eladminplus.system.service.RoleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 角色管理端点。 */
@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;
    private final MenuTreeService menuTreeService;

    public RoleController(RoleService roleService, MenuTreeService menuTreeService) {
        this.roleService = roleService;
        this.menuTreeService = menuTreeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<RoleView>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer enabled) {
        return R.ok(roleService.page(page, size, name, enabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> create(@Valid @RequestBody RoleSaveRequest request) {
        return R.ok(roleService.save(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        roleService.save(new RoleSaveRequest(
                id,
                request.code(),
                request.name(),
                request.nameEn(),
                request.description(),
                request.level(),
                request.dataScope(),
                request.customDeptIds(),
                request.enabled()));
        return R.ok();
    }

    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:edit') || hasAuthority('ROLE_ROOT')")
    public R<List<Long>> menuIdsOfRole(@PathVariable Long id) {
        return R.ok(menuTreeService.menuIdsOfRole(id));
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> saveRoleMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        menuTreeService.saveRoleMenus(id, menuIds);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        roleService.remove(id);
        return R.ok();
    }
}
