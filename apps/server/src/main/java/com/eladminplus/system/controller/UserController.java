package com.eladminplus.system.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.system.dto.UserPageQuery;
import com.eladminplus.system.dto.UserSaveRequest;
import com.eladminplus.system.dto.UserView;
import com.eladminplus.system.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理端点：分页查询与写操作（含保护语义）。 */
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

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> create(@Valid @RequestBody UserSaveRequest request) {
        return R.ok(userService.save(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userService.save(new UserSaveRequest(
                id,
                request.username(),
                request.nickName(),
                request.email(),
                request.phone(),
                request.gender(),
                request.enabled(),
                request.deptId(),
                null,
                request.roleIds(),
                request.jobIds()));
        return R.ok();
    }

    @PutMapping("/password")
    @PreAuthorize("hasAuthority('system:user:resetPwd') || hasAuthority('ROLE_ROOT')")
    public R<Void> resetPasswords(@RequestBody List<Long> ids) {
        userService.resetPasswords(ids);
        return R.ok();
    }

    @PutMapping("/{id}/enabled/{value}")
    @PreAuthorize("hasAuthority('system:user:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> setEnabled(@PathVariable Long id, @PathVariable int value) {
        userService.setEnabled(id, value);
        return R.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('system:user:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> removeBatch(@RequestBody List<Long> ids) {
        userService.removeBatch(ids);
        return R.ok();
    }
}
