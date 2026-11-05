package com.eladminplus.ops.controller;

import com.eladminplus.common.R;
import com.eladminplus.ops.dto.DatabaseConfigSaveRequest;
import com.eladminplus.ops.dto.DatabaseConfigView;
import com.eladminplus.ops.service.DatabaseConfigService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 连接配置端点：列表 / 详情 / 新增 / 更新 / 删除。权限点 database:list / add / edit / del（ROLE_ROOT 直通）。 */
@RestController
@RequestMapping("/database/configs")
public class DatabaseConfigController {

    private final DatabaseConfigService databaseConfigService;

    public DatabaseConfigController(DatabaseConfigService databaseConfigService) {
        this.databaseConfigService = databaseConfigService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('database:list') || hasAuthority('ROLE_ROOT')")
    public R<List<DatabaseConfigView>> list() {
        return R.ok(databaseConfigService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('database:list') || hasAuthority('ROLE_ROOT')")
    public R<DatabaseConfigView> detail(@PathVariable Long id) {
        return R.ok(DatabaseConfigView.from(databaseConfigService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('database:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> create(@RequestBody DatabaseConfigSaveRequest request) {
        return R.ok(databaseConfigService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('database:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> update(@PathVariable Long id, @RequestBody DatabaseConfigSaveRequest request) {
        databaseConfigService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('database:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        databaseConfigService.remove(id);
        return R.ok();
    }
}
