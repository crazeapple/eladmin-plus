package com.eladminplus.monitor.service;

import com.eladminplus.monitor.dto.ServerMonitorView;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;

/**
 * 服务监控指标采集：系统与 JVM 侧走 oshi，连接池走 Micrometer 读 HikariCP 指标（零埋点）。
 * 单端点一次取齐六组，前端单次轮询渲染整页；单项采集失败该组置空不阻断整体响应
 * （容器环境部分指标不可得属常态）。
 */
@Service
public class ServerMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ServerMonitorService.class);

    /** CPU 使用率采样窗口毫秒（短采样即得即时使用率，阻塞可忽略） */
    private static final long CPU_SAMPLE_PERIOD_MS = 500;

    private final MeterRegistry meterRegistry;

    public ServerMonitorService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public ServerMonitorView collect() {
        HardwareAbstractionLayer hal = new SystemInfo().getHardware();
        return new ServerMonitorView(
                capture(() -> cpu(hal.getProcessor())),
                capture(() -> memory(hal.getMemory())),
                capture(() -> swap(hal.getMemory())),
                capture(this::disks),
                capture(this::jvm),
                capture(this::pool));
    }

    /** 单组采集包装：单项失败该组置空，只记日志不阻断整体响应 */
    private <T> T capture(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("服务指标单项采集失败，该组置空：{}", e.getMessage());
            return null;
        }
    }

    protected ServerMonitorView.CpuView cpu(CentralProcessor processor) {
        double usedPercent = processor.getSystemCpuLoad(CPU_SAMPLE_PERIOD_MS) * 100;
        return new ServerMonitorView.CpuView(
                usedPercent, processor.getPhysicalProcessorCount(), processor.getLogicalProcessorCount());
    }

    protected ServerMonitorView.MemoryView memory(GlobalMemory memory) {
        long total = memory.getTotal();
        long free = memory.getAvailable();
        return new ServerMonitorView.MemoryView(
                total, total - free, free, total == 0 ? 0 : (double) (total - free) / total * 100);
    }

    protected ServerMonitorView.SwapView swap(GlobalMemory memory) {
        return new ServerMonitorView.SwapView(memory.getVirtualMemory().getSwapTotal(),
                memory.getVirtualMemory().getSwapUsed());
    }

    protected List<ServerMonitorView.FileStoreView> disks() {
        return new SystemInfo().getOperatingSystem().getFileSystem().getFileStores().stream()
                .map(ServerMonitorService::fileStoreView)
                .toList();
    }

    protected ServerMonitorView.JvmView jvm() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        return new ServerMonitorView.JvmView(
                heapMax < 0 ? null : heapMax,
                memoryBean.getHeapMemoryUsage().getUsed(),
                memoryBean.getHeapMemoryUsage().getCommitted(),
                threadBean.getThreadCount());
    }

    protected ServerMonitorView.PoolView pool() {
        return new ServerMonitorView.PoolView(
                gaugeValue("hikaricp.connections.active"),
                gaugeValue("hikaricp.connections.idle"),
                gaugeValue("hikaricp.connections.pending"),
                gaugeValue("hikaricp.connections.max"),
                gaugeValue("hikaricp.connections.allocated"));
    }

    /** 读仪表指标值：未在册或 NaN（池未初始化）返回 null */
    private Integer gaugeValue(String meterName) {
        var gauge = meterRegistry.find(meterName).gauge();
        if (gauge == null) {
            return null;
        }
        double value = gauge.value();
        return Double.isNaN(value) ? null : (int) Math.round(value);
    }

    private static ServerMonitorView.FileStoreView fileStoreView(OSFileStore store) {
        return new ServerMonitorView.FileStoreView(store.getMount(), store.getTotalSpace(), store.getUsableSpace());
    }
}
