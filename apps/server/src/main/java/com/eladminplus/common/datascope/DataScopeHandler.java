package com.eladminplus.common.datascope;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.domain.UserRole;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.mapper.UserRoleMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.system.domain.RoleDept;
import com.eladminplus.system.mapper.RoleDeptMapper;
import com.eladminplus.system.service.DeptService;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 数据范围改写：读取 Mapper 方法的 @DataScope 注解，按当前用户角色展开部门并集改写 dept_id IN。
 * 三档语义：ALL 全部（跳过改写）、DEPT 本级含全部子级、CUSTOM 勾选集合（不递归）；
 * ROOT 直通跳过；无角色或无部门 → 恒假条件（数据不可见）。
 */
@Component
public class DataScopeHandler implements com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final UserMapper userMapper;
    private final DeptService deptService;
    private final Map<String, Optional<DataScope>> annotationCache = new ConcurrentHashMap<>();

    public DataScopeHandler(
            @org.springframework.context.annotation.Lazy RoleMapper roleMapper,
            @org.springframework.context.annotation.Lazy UserRoleMapper userRoleMapper,
            @org.springframework.context.annotation.Lazy RoleDeptMapper roleDeptMapper,
            @org.springframework.context.annotation.Lazy UserMapper userMapper,
            @org.springframework.context.annotation.Lazy DeptService deptService) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.userMapper = userMapper;
        this.deptService = deptService;
    }

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        DataScope scope = annotationOf(mappedStatementId);
        if (scope == null) {
            return where;
        }
        TokenService.SessionContext operator = currentSessionOrNull();
        if (operator == null || operator.roles().contains("ROLE_ROOT")) {
            return where; // 无认证态与 ROOT 直通,不改写
        }

        List<Long> scopeDeptIds = expandScope(operator);
        if (scopeDeptIds == null) {
            return where; // 任一角色 ALL → 全部数据
        }
        Expression deptIn = inCondition(scope.alias(), scopeDeptIds);
        return where == null ? deptIn : new AndExpression(where, deptIn);
    }

    /** 角色集合展开为部门并集；任一角色 ALL 返回 null 表示全部数据 */
    private List<Long> expandScope(TokenService.SessionContext operator) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, operator.userId()))
                .stream()
                .map(UserRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        List<Long> union = new ArrayList<>();
        for (Role role : roles) {
            String scope = role.getDataScope() == null ? "ALL" : role.getDataScope();
            switch (scope) {
                case "ALL" -> {
                    return null;
                }
                case "DEPT" -> {
                    User user = userMapper.selectById(operator.userId());
                    if (user != null && user.getDeptId() != null) {
                        union.addAll(deptService.selfAndDescendantIds(user.getDeptId()));
                    }
                }
                case "CUSTOM" -> union.addAll(roleDeptMapper
                        .selectList(Wrappers.<RoleDept>lambdaQuery().eq(RoleDept::getRoleId, role.getId()))
                        .stream()
                        .map(RoleDept::getDeptId)
                        .toList());
                default -> { /* 未知档位按无贡献处理 */ }
            }
        }
        return union.stream().distinct().toList();
    }

    /** dept_id IN (...)；空集合 → 恒假（不可见） */
    private Expression inCondition(String alias, List<Long> deptIds) {
        String columnName = (alias == null || alias.isBlank()) ? "dept_id" : alias + ".dept_id";
        Column column = new Column(columnName);
        if (deptIds.isEmpty()) {
            return new InExpression(column, new ParenthesedExpressionList<>(new LongValue(-1)));
        }
        List<Expression> values = deptIds.stream().<Expression>map(LongValue::new).toList();
        return new InExpression(column, new ParenthesedExpressionList<>(values));
    }

    private TokenService.SessionContext currentSessionOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof TokenService.SessionContext context) {
            return context;
        }
        return null;
    }

    /** mappedStatementId → Mapper 方法上的 @DataScope（带缓存） */
    private DataScope annotationOf(String mappedStatementId) {
        return annotationCache
                .computeIfAbsent(mappedStatementId, id -> {
                    try {
                        String className = id.substring(0, id.lastIndexOf('.'));
                        String methodName = id.substring(id.lastIndexOf('.') + 1);
                        for (Method method : Class.forName(className).getMethods()) {
                            if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScope.class)) {
                                return Optional.of(method.getAnnotation(DataScope.class));
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        // 类不可达即无标注
                    }
                    return Optional.<DataScope>empty();
                })
                .orElse(null);
    }
}
