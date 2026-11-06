package com.eladminplus.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户。 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，明文不落库 */
    private String password;

    private String nickName;

    private String email;

    private String phone;

    /** 性别：0 未知 1 男 2 女 */
    private Integer gender;

    /** 头像文件 id（storage_file），空即无头像 */
    private Long avatar;

    /** 用户级偏好 JSON 文本（theme / primaryColor / locale / iconSet），NULL 即缺省 */
    private String preferences;

    /** 状态：1 启用 0 停用 */
    private Integer enabled;

    private Long deptId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
