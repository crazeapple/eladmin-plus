package com.eladminplus.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户角色关联。 */
@Data
@TableName("user_role")
public class UserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long roleId;
}
