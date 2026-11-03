package com.eladminplus.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.domain.UserRole;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.mapper.UserRoleMapper;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.session.AuthContextService;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.common.translation.TranslationService;
import com.eladminplus.system.domain.Dept;
import com.eladminplus.system.domain.Job;
import com.eladminplus.system.domain.UserJob;
import com.eladminplus.system.dto.UserPageQuery;
import com.eladminplus.system.dto.UserSaveRequest;
import com.eladminplus.system.dto.UserView;
import com.eladminplus.system.mapper.DeptMapper;
import com.eladminplus.system.mapper.JobMapper;
import com.eladminplus.system.mapper.UserJobMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 用户管理：分页查询（部门含子级过滤、模糊与状态与时间筛选）、写操作、保护语义。 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final UserJobMapper userJobMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final JobMapper jobMapper;
    private final DeptService deptService;
    private final TranslationService translationService;
    private final AuthContextService authContextService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public UserService(
            UserMapper userMapper,
            DeptMapper deptMapper,
            UserJobMapper userJobMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            JobMapper jobMapper,
            DeptService deptService,
            TranslationService translationService,
            AuthContextService authContextService,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.userJobMapper = userJobMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.jobMapper = jobMapper;
        this.deptService = deptService;
        this.translationService = translationService;
        this.authContextService = authContextService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** 用户分页：部门过滤含子级；名称与邮箱模糊；状态与创建时间范围筛选 */
    public PageResult<UserView> page(UserPageQuery query) {
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? 10 : query.size();
        var wrapper = Wrappers.<User>lambdaQuery();
        if (query.deptId() != null) {
            wrapper.in(User::getDeptId, deptService.selfAndDescendantIds(query.deptId()));
        }
        if (StringUtils.hasText(query.name())) {
            // 名称同时命中用户名与昵称（OR 组合需嵌套,避免与后续 AND 条件串优先级）
            wrapper.and(w -> w.like(User::getUsername, query.name()).or().like(User::getNickName, query.name()));
        }
        if (StringUtils.hasText(query.email())) {
            wrapper.like(User::getEmail, query.email());
        }
        if (query.enabled() != null) {
            wrapper.eq(User::getEnabled, query.enabled());
        }
        if (StringUtils.hasText(query.startTime())) {
            wrapper.ge(User::getCreatedAt, parseTime(query.startTime()));
        }
        if (StringUtils.hasText(query.endTime())) {
            wrapper.le(User::getCreatedAt, parseTime(query.endTime()));
        }
        wrapper.orderByAsc(User::getId);
        Page<User> mpPage = userMapper.selectPage(new PageRequest(page, size).toMpPage(), wrapper);
        return new PageResult<>(assembleViews(mpPage.getRecords()), mpPage.getTotal(),
                (int) (mpPage.getCurrent() - 1), (int) mpPage.getSize());
    }

    /** 批量组装视图：部门名（多语言）、角色 id、岗位 id 与岗位名（多语言） */
    private List<UserView> assembleViews(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = users.stream().map(User::getId).toList();
        String locale = translationService.currentLocale();

        Map<Long, String> deptOverrides = translationService.overrides(
                "dept", "name",
                users.stream().map(User::getDeptId).filter(Objects::nonNull).distinct().toList(),
                locale);
        Map<Long, String> deptNames = deptMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName));

        Map<Long, List<Long>> roleIdsByUser = userRoleMapper.selectList(
                        Wrappers.<UserRole>lambdaQuery().in(UserRole::getUserId, userIds))
                .stream()
                .collect(Collectors.groupingBy(
                        UserRole::getUserId, Collectors.mapping(UserRole::getRoleId, Collectors.toList())));

        List<UserJob> userJobs = userJobMapper.selectList(
                Wrappers.<UserJob>lambdaQuery().in(UserJob::getUserId, userIds));
        List<Long> jobIds = userJobs.stream().map(UserJob::getJobId).distinct().toList();
        Map<Long, String> jobOverrides = translationService.overrides("job", "name", jobIds, locale);
        Map<Long, String> jobNames = jobMapper.selectBatchIds(jobIds.isEmpty() ? List.of(-1L) : jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Job::getName));
        Map<Long, List<Long>> jobIdsByUser = userJobs.stream()
                .collect(Collectors.groupingBy(UserJob::getUserId,
                        Collectors.mapping(UserJob::getJobId, Collectors.toList())));
        Function<Long, List<String>> jobNamesOf = userId -> jobIdsByUser.getOrDefault(userId, List.of()).stream()
                .map(jobId -> jobOverrides.getOrDefault(jobId, jobNames.getOrDefault(jobId, "")))
                .toList();

        List<UserView> views = new ArrayList<>();
        for (User user : users) {
            String deptName = user.getDeptId() == null
                    ? null
                    : deptOverrides.getOrDefault(user.getDeptId(), deptNames.get(user.getDeptId()));
            views.add(new UserView(
                    user.getId(),
                    user.getUsername(),
                    user.getNickName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getGender(),
                    user.getEnabled(),
                    user.getCreatedAt(),
                    user.getDeptId(),
                    deptName,
                    roleIdsByUser.getOrDefault(user.getId(), List.of()),
                    jobIdsByUser.getOrDefault(user.getId(), List.of()),
                    jobNamesOf.apply(user.getId())));
        }
        return views;
    }

    private LocalDateTime parseTime(String value) {
        return LocalDateTime.parse(value);
    }

    /**
     * 保存用户：新增含初始密码（BCrypt）；角色与岗位关联全量替换。
     * 保护语义：角色越权约束（所选角色不得高于自身最高级别，ROOT 直通）、自我保护
     * （不能禁用自己、不能修改自己的角色）、用户名与邮箱唯一（友好错误）。
     */
    @Transactional
    public Long save(UserSaveRequest request) {
        TokenService.SessionContext operator = authContextService.currentSession();
        boolean operatorRoot = operator.roles().contains("ROLE_ROOT");

        // 用户名与邮箱唯一（编辑排除自身）
        Long dupUsername = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, request.username())
                .ne(request.id() != null, User::getId, request.id()));
        if (dupUsername > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.username-duplicate");
        }
        Long dupEmail = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, request.email())
                .ne(request.id() != null, User::getId, request.id()));
        if (dupEmail > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.email-duplicate");
        }

        // 越权约束：所选角色中级别数字小于自身最高角色（权限更大）即拒
        List<Long> roleIds = request.roleIds() == null ? List.of() : request.roleIds();
        validateRoleAssignment(operator, operatorRoot, roleIds);

        User user = new User();
        user.setId(request.id());
        user.setUsername(request.username());
        user.setNickName(request.nickName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setGender(request.gender());
        user.setEnabled(request.enabled());
        user.setDeptId(request.deptId());
        if (user.getId() == null) {
            if (!StringUtils.hasText(request.password())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.password-required");
            }
            user.setPassword(passwordEncoder.encode(request.password()));
            userMapper.insert(user);
        } else {
            User existing = userMapper.selectById(user.getId());
            if (existing == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
            }
            // 自我保护：不能禁用自己、不能修改自己的角色（其余字段可自改）
            if (user.getId().equals(operator.userId())) {
                if (request.enabled() != null && request.enabled() == 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.self-disable");
                }
                List<Long> existingRoles = userRoleMapper.selectList(
                                Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, user.getId()))
                        .stream().map(UserRole::getRoleId).sorted().toList();
                if (!Objects.equals(existingRoles, roleIds.stream().sorted().toList())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.self-role-change");
                }
            }
            userMapper.updateById(user); // 编辑不改密码字段（null 不更新）
        }

        syncAssociations(user.getId(), roleIds, request.jobIds() == null ? List.of() : request.jobIds());
        return user.getId();
    }

    /** 角色越权校验：所选角色 level 小于自身最高角色（权限更大）即拒；ROOT 直通 */
    private void validateRoleAssignment(
            TokenService.SessionContext operator, boolean operatorRoot, List<Long> roleIds) {
        if (operatorRoot || roleIds.isEmpty()) {
            return;
        }
        int operatorBest = bestLevel(operator.userId());
        Integer targetBest = roleMapper.selectBatchIds(roleIds).stream()
                .mapToInt(role -> role.getLevel() == null ? Integer.MAX_VALUE : role.getLevel())
                .min()
                .orElse(Integer.MAX_VALUE);
        if (targetBest < operatorBest) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.role-level-exceeded");
        }
    }

    /** 操作者最高角色（level 最小者）；无角色计 Integer.MAX_VALUE */
    private int bestLevel(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .mapToInt(role -> role.getLevel() == null ? Integer.MAX_VALUE : role.getLevel())
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    /** 关联全量替换：user_role 与 user_job */
    private void syncAssociations(Long userId, List<Long> roleIds, List<Long> jobIds) {
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
        userJobMapper.delete(Wrappers.<UserJob>lambdaQuery().eq(UserJob::getUserId, userId));
        for (Long jobId : jobIds) {
            UserJob userJob = new UserJob();
            userJob.setUserId(userId);
            userJob.setJobId(jobId);
            userJobMapper.insert(userJob);
        }
    }

    /**
     * 状态启停：停用即强退（删该用户全部会话与 refresh）。
     * 保护：不能停用自己、admin 账号不能被停用。
     */
    public void setEnabled(Long userId, int value) {
        TokenService.SessionContext operator = authContextService.currentSession();
        if (userId.equals(operator.userId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.self-disable");
        }
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        if ("admin".equals(target.getUsername())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.admin-protected");
        }
        User update = new User();
        update.setId(userId);
        update.setEnabled(value);
        userMapper.updateById(update);
        if (value == 0) {
            tokenService.revokeAllForUser(userId);
        }
    }

    /** 批量删除：自我保护（不能删自己）与 admin 保护；级联清理角色与岗位关联 */
    @Transactional
    public void removeBatch(List<Long> ids) {
        TokenService.SessionContext operator = authContextService.currentSession();
        if (ids.contains(operator.userId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.self-delete");
        }
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        if (admin != null && ids.contains(admin.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.admin-protected");
        }
        for (Long id : ids) {
            userMapper.deleteById(id);
            userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, id));
            userJobMapper.delete(Wrappers.<UserJob>lambdaQuery().eq(UserJob::getUserId, id));
        }
    }
}
