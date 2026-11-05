package com.eladminplus.ops.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 备份记录：成功备份账本（失败不落行），与落盘文件一一对应——删记录同删文件。 */
@Data
@TableName("backup_record")
public class BackupRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 备份文件名（{库名}_{yyyyMMddHHmmss}.sql） */
    private String fileName;

    /** 备份目录（绝对路径，删记录时按此定位文件） */
    private String dir;

    /** 库名 */
    private String dbName;

    /** 文件字节数 */
    private Long size;

    private LocalDateTime createdAt;
}
