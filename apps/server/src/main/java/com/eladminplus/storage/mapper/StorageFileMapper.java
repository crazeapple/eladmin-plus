package com.eladminplus.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eladminplus.storage.domain.StorageFile;
import org.apache.ibatis.annotations.Mapper;

/** storage_file 表数据访问。 */
@Mapper
public interface StorageFileMapper extends BaseMapper<StorageFile> {}
