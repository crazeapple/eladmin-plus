package com.eladminplus.system.controller;

import com.eladminplus.auth.session.MenuTreeService;
import com.eladminplus.common.R;
import com.eladminplus.system.dto.MenuSaveRequest;
import com.eladminplus.system.service.MenuService;
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

/** 菜单管理端点：全量树（角色授权与管理页共用）+ 新增 / 编辑 / 删除。 */
@RestController
@RequestMapping("/menus")
public class MenuController {

    private final MenuTreeService menuTreeService;
    private final MenuService menuService;

    public MenuController(MenuTreeService menuTreeService, MenuService menuService) {
        this.menuTreeService = menuTreeService;
        this.menuService = menuService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:list') || hasAuthority('ROLE_ROOT')")
    public R<List<MenuTreeService.MenuNode>> tree() {
        return R.ok(menuTreeService.fullTree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> create(@Valid @RequestBody MenuSaveRequest request) {
        return R.ok(menuService.save(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MenuSaveRequest request) {
        menuService.save(new MenuSaveRequest(
                id,
                request.parentId(),
                request.type(),
                request.permission(),
                request.i18nKey(),
                request.componentPathKey(),
                request.icon(),
                request.hidden(),
                request.noCache(),
                request.sort()));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        menuService.remove(id);
        return R.ok();
    }
}
