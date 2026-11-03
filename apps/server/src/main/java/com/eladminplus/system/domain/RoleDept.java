package com.eladminplus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 角色自定义数据范围的部门集合。 */
@Data
@TableName("role_dept")
public class RoleDept {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long deptId;
}
