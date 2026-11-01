package com.eladminplus.stats;

/**
 * 概览统计：形状占位契约，字段随首页仪表盘需求演进；真实数据源接入时仅换 service 实现不动结构。
 */
public record OverviewStats(
        long userCount,
        long onlineCount,
        long todayVisitCount,
        long logCount
) {
}
