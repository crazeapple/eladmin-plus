package com.eladminplus.monitor.controller;

import com.eladminplus.common.R;
import com.eladminplus.monitor.dto.OnlineUserView;
import com.eladminplus.monitor.service.OnlineUserService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 在线用户端点：会话索引枚举列表与按用户强退。 */
@RestController
@RequestMapping("/online")
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    public OnlineUserController(OnlineUserService onlineUserService) {
        this.onlineUserService = onlineUserService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:online:list') || hasAuthority('ROLE_ROOT')")
    public R<List<OnlineUserView>> list() {
        return R.ok(onlineUserService.list());
    }

    /** 强退目标用户：全部 access 会话与 refresh 键即时失效，对含自己在内无豁免 */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('monitor:online:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> kick(@PathVariable Long userId) {
        onlineUserService.kick(userId);
        return R.ok();
    }
}
