package com.eladminplus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 部门（树）。 */
@Data
@TableName("dept")
public class Dept {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    /** 默认语言名；非默认语言走 translation 覆盖 */
    private String name;

    private Integer sort;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
