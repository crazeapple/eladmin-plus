package com.eladminplus.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eladminplus.ops.domain.BackupRecord;
import org.apache.ibatis.annotations.Mapper;

/** backup_record 表数据访问。 */
@Mapper
public interface BackupRecordMapper extends BaseMapper<BackupRecord> {}
