package com.eladminplus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据字典：name 为业务标识，唯一；明细走 dict_detail。 */
@Data
@TableName("dict")
public class Dict {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典名，业务标识，唯一 */
    private String name;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
