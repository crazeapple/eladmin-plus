package com.eladminplus.common.datascope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 数据范围标注：标在 Mapper 查询方法上，按当前用户角色展开部门集合改写 dept_id IN 条件。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 目标表别名（join 场景显式声明；单表查询留空） */
    String alias() default "";
}
