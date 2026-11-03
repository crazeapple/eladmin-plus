package com.eladminplus.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.translation.TranslationService;
import com.eladminplus.system.domain.Dept;
import com.eladminplus.system.domain.UserJob;
import com.eladminplus.system.dto.UserPageQuery;
import com.eladminplus.system.dto.UserView;
import com.eladminplus.system.mapper.DeptMapper;
import com.eladminplus.system.mapper.UserJobMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 用户管理：分页查询（部门含子级过滤、模糊与状态与时间筛选）、写操作、保护语义。 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final UserJobMapper userJobMapper;
    private final DeptService deptService;
    private final TranslationService translationService;
    private final com.eladminplus.auth.mapper.UserRoleMapper userRoleMapper;
    private final com.eladminplus.auth.mapper.RoleMapper roleMapper;
    private final com.eladminplus.system.mapper.JobMapper jobMapper;

    public UserService(
            UserMapper userMapper,
            DeptMapper deptMapper,
            UserJobMapper userJobMapper,
            DeptService deptService,
            TranslationService translationService,
            com.eladminplus.auth.mapper.UserRoleMapper userRoleMapper,
            com.eladminplus.auth.mapper.RoleMapper roleMapper,
            com.eladminplus.system.mapper.JobMapper jobMapper) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.userJobMapper = userJobMapper;
        this.deptService = deptService;
        this.translationService = translationService;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.jobMapper = jobMapper;
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
                users.stream().map(User::getDeptId).filter(java.util.Objects::nonNull).distinct().toList(),
                locale);
        Map<Long, String> deptNames = deptMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName));

        Map<Long, List<Long>> roleIdsByUser = userRoleMapper.selectList(
                        Wrappers.<com.eladminplus.auth.domain.UserRole>lambdaQuery()
                                .in(com.eladminplus.auth.domain.UserRole::getUserId, userIds))
                .stream()
                .collect(Collectors.groupingBy(
                        com.eladminplus.auth.domain.UserRole::getUserId,
                        Collectors.mapping(com.eladminplus.auth.domain.UserRole::getRoleId, Collectors.toList())));

        List<UserJob> userJobs = userJobMapper.selectList(
                Wrappers.<UserJob>lambdaQuery().in(UserJob::getUserId, userIds));
        List<Long> jobIds = userJobs.stream().map(UserJob::getJobId).distinct().toList();
        Map<Long, String> jobOverrides = translationService.overrides("job", "name", jobIds, locale);
        Map<Long, String> jobNames = jobMapper.selectBatchIds(jobIds.isEmpty() ? List.of(-1L) : jobIds).stream()
                .collect(Collectors.toMap(com.eladminplus.system.domain.Job::getId,
                        com.eladminplus.system.domain.Job::getName));
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
}
