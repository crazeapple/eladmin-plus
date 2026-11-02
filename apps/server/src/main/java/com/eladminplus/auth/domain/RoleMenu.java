package com.eladminplus.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 角色菜单关联。 */
@Data
@TableName("role_menu")
public class RoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long menuId;
}
