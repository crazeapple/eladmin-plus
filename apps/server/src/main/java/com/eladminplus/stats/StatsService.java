package com.eladminplus.stats;

import org.springframework.stereotype.Service;

@Service
public class StatsService {

    /**
     * 概览统计静态占位值：各计数对应后续提案的数据源（用户、在线会话、访问、操作日志），
     * 当前无真实来源，返回演示数据。
     */
    public OverviewStats overview() {
        return new OverviewStats(128, 3, 1024, 2048);
    }
}
