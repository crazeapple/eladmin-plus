package com.eladminplus.system.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.system.dto.UserPageQuery;
import com.eladminplus.system.dto.UserView;
import com.eladminplus.system.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理端点：查询在 T04，写操作随 T05 追加。 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<UserView>> page(UserPageQuery query) {
        return R.ok(userService.page(query));
    }
}
