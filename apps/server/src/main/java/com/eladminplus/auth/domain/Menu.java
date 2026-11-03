package com.eladminplus.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 菜单树节点：type 0 目录 / 1 菜单 / 2 按钮；按钮型权限点绑菜单树。 */
@Data
@TableName("menu")
public class Menu {

    public static final int TYPE_DIRECTORY = 0;
    public static final int TYPE_MENU = 1;
    public static final int TYPE_BUTTON = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单 id，根为 null */
    private Long parentId;

    private Integer type;

    /** 按钮型权限点（module:entity:action） */
    private String permission;

    /** 展示文案 i18n key（目录 / 菜单） */
    private String i18nKey;

    /** 前端组件路径 key（views 下相对路径，不含扩展名） */
    private String componentPathKey;

    /** 菜单图标名 */
    private String icon;

    /** 侧边栏隐藏 */
    private Boolean hidden;

    /** 页面不入 keep-alive 缓存 */
    private Boolean noCache;

    /** 排序，小者在前 */
    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
