package com.eladminplus.common.translation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 通用翻译覆盖行：主表默认列承载唯一约束与检索，非默认语言展示值在此覆盖。 */
@Data
@TableName("translation")
public class Translation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实体名（dept / job / role / app …），小写 */
    private String entity;

    private Long entityId;

    /** 覆盖字段名（如 name） */
    private String field;

    /** 语言标签（zh-CN / en）；zh-CN 为默认语言不入覆盖 */
    private String locale;

    private String value;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
