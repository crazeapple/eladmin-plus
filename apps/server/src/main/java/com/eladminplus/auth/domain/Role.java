package com.eladminplus.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色；code = ROLE_ROOT 为内置超管角色位，判定不依赖用户名。 */
@Data
@TableName("role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    /** 内置角色不可删除 */
    private Boolean builtIn;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
