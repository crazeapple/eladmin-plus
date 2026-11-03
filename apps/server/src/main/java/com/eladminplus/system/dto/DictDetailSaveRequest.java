package com.eladminplus.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 字典明细保存请求：id 为空即新增；label 须为合法 JSON 对象文本，原样存取；同字典内 value 唯一。 */
public record DictDetailSaveRequest(
        Long id,
        @NotBlank @Size(max = 512) String label,
        @NotBlank @Size(max = 128) String value,
        Integer sort) {}
