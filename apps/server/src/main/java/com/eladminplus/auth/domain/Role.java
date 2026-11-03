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

    private String description;

    /** 角色级别，越小权限越大 */
    private Integer level;

    /** 数据范围：ALL 全部 / DEPT 本级含子级 / CUSTOM 自定义 */
    private String dataScope;

    /** 状态：1 启用 0 停用 */
    private Integer enabled;

    /** 内置角色不可删除 */
    private Boolean builtIn;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
