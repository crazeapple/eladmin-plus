package com.eladminplus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据字典明细：label 为多语言 JSON 文本（原样存取不解析），同字典内 value 唯一。 */
@Data
@TableName("dict_detail")
public class DictDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属字典 id */
    private Long dictId;

    /** 标签多语言 JSON 文本（如 {"zh-CN":"男","en":"Male"}） */
    private String label;

    /** 字典值 */
    private String value;

    /** 排序，小者在前 */
    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
