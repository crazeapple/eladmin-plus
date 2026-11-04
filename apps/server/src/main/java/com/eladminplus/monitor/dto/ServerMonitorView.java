package com.eladminplus.monitor.dto;

import java.util.List;

/**
 * 服务监控指标视图：单端点一次取齐六组——CPU、内存、Swap、磁盘、JVM 内存与线程、连接池（HikariCP）。
 * 单项采集失败该组整体置空（null），不阻断其余组；字节单位字段以 Bytes 结尾标注，容量比为百分数。
 */
public record ServerMonitorView(
        CpuView cpu,
        MemoryView memory,
        SwapView swap,
        List<FileStoreView> disks,
        JvmView jvm,
        PoolView pool) {

    /** CPU：使用率（0~100）与物理 / 逻辑核数 */
    public record CpuView(double usedPercent, int physicalCores, int logicalCores) {}

    /** 物理内存：总量、已用、可用与使用率 */
    public record MemoryView(long totalBytes, long usedBytes, long freeBytes, double usedPercent) {}

    /** Swap：总量与已用 */
    public record SwapView(long totalBytes, long usedBytes) {}

    /** 磁盘：挂载点为行的文件系统容量视图 */
    public record FileStoreView(String mount, long totalBytes, long usableBytes) {}

    /** JVM：堆容量与线程数（heapMax 未定义时为 null） */
    public record JvmView(Long heapMaxBytes, long heapUsedBytes, long heapCommittedBytes, int threadCount) {}

    /** 连接池（HikariCP）：活跃 / 空闲 / 等待 / 上限 / 已分配；指标未在册时为 null */
    public record PoolView(Integer active, Integer idle, Integer pending, Integer max, Integer allocated) {}
}
