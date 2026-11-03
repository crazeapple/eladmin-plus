package com.eladminplus.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.RoleMenu;
import com.eladminplus.auth.domain.UserRole;
import com.eladminplus.auth.mapper.RoleMenuMapper;
import com.eladminplus.auth.mapper.UserRoleMapper;
import com.eladminplus.auth.session.AuthContextService;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.common.translation.TranslationService;
import com.eladminplus.system.domain.RoleDept;
import com.eladminplus.system.mapper.RoleDeptMapper;
import com.eladminplus.system.dto.RoleSaveRequest;
import com.eladminplus.system.dto.RoleView;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 角色管理：分页筛选、CRUD（多语言、数据范围）、删除与越权校验、自定义部门集合维护。 */
@Service
public class RoleService {

    private final com.eladminplus.auth.mapper.RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final TranslationService translationService;
    private final AuthContextService authContextService;

    public RoleService(
            com.eladminplus.auth.mapper.RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            RoleMenuMapper roleMenuMapper,
            RoleDeptMapper roleDeptMapper,
            TranslationService translationService,
            AuthContextService authContextService) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.translationService = translationService;
        this.authContextService = authContextService;
    }

    /** 角色分页：名称模糊（默认语言列）、状态、时间筛选 */
    public PageResult<RoleView> page(int page, int size, String name, Integer enabled) {
        Page<Role> mpPage = roleMapper.selectPage(new PageRequest(page, size).toMpPage(),
                Wrappers.<Role>lambdaQuery()
                        .like(StringUtils.hasText(name), Role::getName, name)
                        .eq(enabled != null, Role::getEnabled, enabled)
                        .orderByAsc(Role::getLevel)
                        .orderByAsc(Role::getId));
        var locale = translationService.currentLocale();
        var overrides = translationService.overrides(
                "role", "name", mpPage.getRecords().stream().map(Role::getId).toList(), locale);
        List<RoleView> content = mpPage.getRecords().stream()
                .map(role -> toView(role, overrides.getOrDefault(role.getId(), role.getName())))
                .toList();
        return new PageResult<>(content, mpPage.getTotal(), (int) (mpPage.getCurrent() - 1), (int) mpPage.getSize());
    }

    private RoleView toView(Role role, String resolvedName) {
        List<Long> customDeptIds = "CUSTOM".equals(role.getDataScope())
                ? roleDeptMapper.selectList(Wrappers.<RoleDept>lambdaQuery().eq(RoleDept::getRoleId, role.getId()))
                        .stream().map(RoleDept::getDeptId).toList()
                : null;
        return new RoleView(role.getId(), role.getCode(), resolvedName, role.getDescription(), role.getLevel(),
                role.getDataScope(), customDeptIds, role.getEnabled(), role.getBuiltIn(), role.getCreatedAt());
    }

    /** 越权约束：目标角色 level 小于操作者自身最高角色（权限更大）即拒；ROOT 直通 */
    private void checkLevelPermission(Long targetRoleId, Integer targetLevel) {
        TokenService.SessionContext operator = authContextService.currentSession();
        if (operator.roles().contains("ROLE_ROOT")) {
            return;
        }
        int operatorBest = operator.roles().stream()
                .filter(code -> !code.equals("ROLE_ROOT"))
                .mapToInt(code -> roleMapper
                        .selectList(Wrappers.<Role>lambdaQuery().eq(Role::getCode, code)).stream()
                        .mapToInt(r -> r.getLevel() == null ? Integer.MAX_VALUE : r.getLevel())
                        .min()
                        .orElse(Integer.MAX_VALUE))
                .min()
                .orElse(Integer.MAX_VALUE);
        if (targetLevel != null && targetLevel < operatorBest) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.role-level-exceeded");
        }
        if (targetRoleId != null) {
            Role existing = roleMapper.selectById(targetRoleId);
            if (existing != null && existing.getLevel() != null && existing.getLevel() < operatorBest) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.role-level-exceeded");
            }
        }
    }

    @Transactional
    public Long save(RoleSaveRequest request) {
        TokenService.SessionContext operator = authContextService.currentSession();
        boolean operatorRoot = operator.roles().contains("ROLE_ROOT");

        Role role = new Role();
        role.setId(request.id());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setLevel(request.level() == null ? 999 : request.level());
        role.setDataScope(request.dataScope());
        role.setEnabled(request.enabled() == null ? 1 : request.enabled());

        if (role.getId() == null) {
            // 新增：code 与默认名唯一
            Long dupCode = roleMapper.selectCount(Wrappers.<Role>lambdaQuery().eq(Role::getCode, request.code()));
            if (dupCode > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.role-code-duplicate");
            }
            Long dupName = roleMapper.selectCount(Wrappers.<Role>lambdaQuery().eq(Role::getName, request.name()));
            if (dupName > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.role-name-duplicate");
            }
            checkLevelPermission(null, role.getLevel());
            role.setCode(request.code());
            role.setBuiltIn(false);
            roleMapper.insert(role);
        } else {
            Role existing = roleMapper.selectById(role.getId());
            if (existing == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
            }
            if (Boolean.TRUE.equals(existing.getBuiltIn())) {
                // 内置角色：不可删、不可改 code 与 level;名称等展示字段可改
                role.setCode(existing.getCode());
                role.setLevel(existing.getLevel());
            } else {
                checkLevelPermission(role.getId(), role.getLevel());
                role.setCode(existing.getCode()); // code 创建后不可改
            }
            roleMapper.updateById(role);
        }

        translationService.syncOverride("role", role.getId(), "name", TranslationService.EN_LOCALE, request.nameEn());

        // 自定义数据范围部门集合：CUSTOM 全量替换,其他档清空
        roleDeptMapper.delete(Wrappers.<RoleDept>lambdaQuery().eq(RoleDept::getRoleId, role.getId()));
        if ("CUSTOM".equals(role.getDataScope()) && request.customDeptIds() != null) {
            for (Long deptId : request.customDeptIds()) {
                RoleDept roleDept = new RoleDept();
                roleDept.setRoleId(role.getId());
                roleDept.setDeptId(deptId);
                roleDeptMapper.insert(roleDept);
            }
        }
        return role.getId();
    }

    @Transactional
    public void remove(Long id) {
        checkLevelPermission(id, null);
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.role-built-in");
        }
        Long userCount = userRoleMapper.selectCount(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getRoleId, id));
        if (userCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.role-in-use");
        }
        // 先清授权与数据范围关联再删主行（role_menu 的外键约束指向 role 主键）
        roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, id));
        roleDeptMapper.delete(Wrappers.<RoleDept>lambdaQuery().eq(RoleDept::getRoleId, id));
        roleMapper.deleteById(id);
        translationService.removeAll("role", id);
    }
}
