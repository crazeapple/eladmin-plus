package com.eladminplus.storage.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 存储文件：每次上传独立成行；引擎列记落库时的引擎，下载按行内引擎字段路由（混引擎历史可读）。 */
@Data
@TableName("storage_file")
public class StorageFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名 */
    private String name;

    /** 存储键（{yyyyMM}/{uuid}.{ext}） */
    private String storageKey;

    /** 扩展名（小写，无点） */
    private String ext;

    /** 文件字节数 */
    private Long size;

    /** MIME 类型 */
    private String mime;

    /** 存储引擎：local 本地磁盘 / rustfs 对象存储 */
    private String engine;

    /** 来源：空为页面上传 / backup 数据库备份（服务层内部打标，不暴露参数） */
    private String source;

    /** 上传人用户名 */
    private String uploader;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
