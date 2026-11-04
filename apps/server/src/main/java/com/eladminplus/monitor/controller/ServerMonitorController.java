package com.eladminplus.monitor.controller;

import com.eladminplus.common.R;
import com.eladminplus.monitor.dto.ServerMonitorView;
import com.eladminplus.monitor.service.ServerMonitorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 服务监控端点：单端点一次取齐系统、JVM 与连接池指标分组。 */
@RestController
@RequestMapping("/monitor")
public class ServerMonitorController {

    private final ServerMonitorService serverMonitorService;

    public ServerMonitorController(ServerMonitorService serverMonitorService) {
        this.serverMonitorService = serverMonitorService;
    }

    @GetMapping("/server")
    @PreAuthorize("hasAuthority('monitor:monitor:list') || hasAuthority('ROLE_ROOT')")
    public R<ServerMonitorView> server() {
        return R.ok(serverMonitorService.collect());
    }
}
