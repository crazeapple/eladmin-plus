package com.eladminplus.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eladminplus.ops.domain.DatabaseConfig;
import org.apache.ibatis.annotations.Mapper;

/** database_config 表数据访问。 */
@Mapper
public interface DatabaseConfigMapper extends BaseMapper<DatabaseConfig> {}
