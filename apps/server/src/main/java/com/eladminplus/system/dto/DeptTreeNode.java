package com.eladminplus.system.dto;

import java.util.List;

/** 部门树节点：name 已按当前请求语言解析（覆盖优先，回默认列）。 */
public record DeptTreeNode(Long id, Long parentId, String name, Integer sort, Integer enabled, List<DeptTreeNode> children) {}
