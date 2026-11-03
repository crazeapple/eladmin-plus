package com.eladminplus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户岗位关联。 */
@Data
@TableName("user_job")
public class UserJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;
}
