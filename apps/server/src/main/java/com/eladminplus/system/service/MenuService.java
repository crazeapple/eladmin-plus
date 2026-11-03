package com.eladminplus.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.domain.RoleMenu;
import com.eladminplus.auth.mapper.MenuMapper;
import com.eladminplus.auth.mapper.RoleMenuMapper;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.system.dto.MenuSaveRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 菜单管理：新增 / 编辑 / 删除；删除校验（有子或有角色关联不能删）；type 创建后不可变更。 */
@Service
public class MenuService {

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    public MenuService(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Transactional
    public Long save(MenuSaveRequest request) {
        Menu menu = new Menu();
        menu.setId(request.id());
        menu.setParentId(request.parentId());
        menu.setType(request.type());
        menu.setPermission(request.permission());
        menu.setI18nKey(request.i18nKey());
        menu.setComponentPathKey(request.componentPathKey());
        menu.setIcon(request.icon());
        menu.setHidden(request.hidden());
        menu.setNoCache(request.noCache());
        menu.setSort(request.sort() == null ? 0 : request.sort());
        if (menu.getId() == null) {
            checkParent(menu.getParentId(), null);
            menuMapper.insert(menu);
        } else {
            Menu existing = menuMapper.selectById(menu.getId());
            if (existing == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
            }
            if (!existing.getType().equals(menu.getType())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "error.system.menu-type-immutable");
            }
            checkParent(menu.getParentId(), menu.getId());
            // 全量表单提交语义：显式逐列覆盖，null 一并落库（icon 清空等编辑诉求）
            menuMapper.update(
                    Wrappers.<Menu>lambdaUpdate()
                            .eq(Menu::getId, menu.getId())
                            .set(Menu::getParentId, menu.getParentId())
                            .set(Menu::getType, menu.getType())
                            .set(Menu::getPermission, menu.getPermission())
                            .set(Menu::getI18nKey, menu.getI18nKey())
                            .set(Menu::getComponentPathKey, menu.getComponentPathKey())
                            .set(Menu::getIcon, menu.getIcon())
                            .set(Menu::getHidden, menu.getHidden() == null ? false : menu.getHidden())
                            .set(Menu::getNoCache, menu.getNoCache() == null ? false : menu.getNoCache())
                            .set(Menu::getSort, menu.getSort()));
        }
        return menu.getId();
    }

    @Transactional
    public void remove(Long id) {
        if (menuMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        Long childCount = menuMapper.selectCount(Wrappers.<Menu>lambdaQuery().eq(Menu::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "error.system.menu-has-children");
        }
        Long roleCount = roleMenuMapper.selectCount(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getMenuId, id));
        if (roleCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "error.system.menu-in-use");
        }
        menuMapper.deleteById(id);
    }

    /** 上级菜单存在性（非空时）与防环（编辑时不可指向自身或自身子树） */
    private void checkParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.menu-parent-cycle");
        }
        Menu parent = menuMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.menu-parent-missing");
        }
        if (selfId != null && subtreeContains(selfId, parentId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.menu-parent-cycle");
        }
    }

    /** target 是否在 ancestorId 的子树内（全量加载内存递归） */
    private boolean subtreeContains(Long ancestorId, Long targetId) {
        List<Menu> children =
                menuMapper.selectList(Wrappers.<Menu>lambdaQuery().eq(Menu::getParentId, ancestorId));
        for (Menu child : children) {
            if (Objects.equals(child.getId(), targetId) || subtreeContains(child.getId(), targetId)) {
                return true;
            }
        }
        return false;
    }
}
