package com.eladminplus.auth.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.auth.domain.User;
import com.eladminplus.common.datascope.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** User 表数据访问。 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 数据范围分页查询：DataPermissionInterceptor 按方法上的 @DataScope 改写部门条件 */
    @DataScope
    @Select("SELECT * FROM `user` ${ew.customSqlSegment}")
    Page<User> selectPageWithScope(
            Page<User> page, @Param(Constants.WRAPPER) Wrapper<User> wrapper);
}
