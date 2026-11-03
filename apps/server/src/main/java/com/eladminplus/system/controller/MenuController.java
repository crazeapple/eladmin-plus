package com.eladminplus.system.controller;

import com.eladminplus.auth.session.MenuTreeService;
import com.eladminplus.common.R;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 菜单树只读端点（角色授权数据源）；菜单管理 CRUD 归配置域提案。 */
@RestController
@RequestMapping("/menus")
public class MenuController {

    private final MenuTreeService menuTreeService;

    public MenuController(MenuTreeService menuTreeService) {
        this.menuTreeService = menuTreeService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:list') || hasAuthority('ROLE_ROOT')")
    public R<List<MenuTreeService.MenuNode>> tree() {
        return R.ok(menuTreeService.fullTree());
    }
}
