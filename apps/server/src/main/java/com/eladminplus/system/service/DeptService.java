package com.eladminplus.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.common.translation.TranslationService;
import com.eladminplus.system.domain.Dept;
import com.eladminplus.system.dto.DeptSaveRequest;
import com.eladminplus.system.dto.DeptTreeNode;
import com.eladminplus.system.mapper.DeptMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 部门树管理：多语言展示（通用翻译覆盖）、CRUD、删除校验（有子或有关联用户不能删）。 */
@Service
public class DeptService {

    private final DeptMapper deptMapper;
    private final TranslationService translationService;
    private final UserMapper userMapper;

    public DeptService(DeptMapper deptMapper, TranslationService translationService, UserMapper userMapper) {
        this.deptMapper = deptMapper;
        this.translationService = translationService;
        this.userMapper = userMapper;
    }

    /** 全量树：sort 升序；名称按当前请求语言覆盖 */
    public List<DeptTreeNode> tree() {
        List<Dept> all = deptMapper.selectList(Wrappers.<Dept>lambdaQuery().orderByAsc(Dept::getSort));
        String locale = translationService.currentLocale();
        Map<Long, String> overrides = translationService.overrides(
                "dept", "name", all.stream().map(Dept::getId).toList(), locale);
        return buildTree(all, null, overrides);
    }

    private List<DeptTreeNode> buildTree(List<Dept> all, Long parentId, Map<Long, String> overrides) {
        List<DeptTreeNode> nodes = new ArrayList<>();
        for (Dept dept : all) {
            if (!Objects.equals(dept.getParentId(), parentId)) {
                continue;
            }
            String name = overrides.getOrDefault(dept.getId(), dept.getName());
            nodes.add(new DeptTreeNode(dept.getId(), dept.getParentId(), name, dept.getSort(), dept.getEnabled(),
                    buildTree(all, dept.getId(), overrides)));
        }
        return nodes;
    }

    @Transactional
    public Long save(DeptSaveRequest request) {
        Dept dept = new Dept();
        dept.setId(request.id());
        dept.setParentId(request.parentId());
        dept.setName(request.name());
        dept.setSort(request.sort() == null ? 0 : request.sort());
        dept.setEnabled(request.enabled());
        if (dept.getId() == null) {
            deptMapper.insert(dept);
        } else {
            if (deptMapper.selectById(dept.getId()) == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
            }
            // 上级不可指向自身或自身子树（防环）
            if (dept.getParentId() != null && subtreeContains(dept.getId(), dept.getParentId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.dept-parent-cycle");
            }
            deptMapper.updateById(dept);
        }
        translationService.syncOverride("dept", dept.getId(), "name", TranslationService.EN_LOCALE, request.nameEn());
        return dept.getId();
    }

    private boolean subtreeContains(Long ancestorId, Long targetId) {
        if (ancestorId.equals(targetId)) {
            return true;
        }
        List<Dept> children =
                deptMapper.selectList(Wrappers.<Dept>lambdaQuery().eq(Dept::getParentId, ancestorId));
        for (Dept child : children) {
            if (subtreeContains(child.getId(), targetId)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void remove(Long id) {
        if (deptMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        Long childCount = deptMapper.selectCount(Wrappers.<Dept>lambdaQuery().eq(Dept::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.dept-has-children");
        }
        Long userCount = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getDeptId, id));
        if (userCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.dept-in-use");
        }
        deptMapper.deleteById(id);
        translationService.removeAll("dept", id);
    }
}
