package com.eladminplus.monitor.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.monitor.domain.OperationLog;
import com.eladminplus.monitor.dto.OperationLogPageQuery;
import com.eladminplus.monitor.service.OperationLogService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 操作日志维护端点：分页筛选、单删批删、全部清空与当前筛选全集 Excel 导出。 */
@RestController
@RequestMapping("/logs/operations")
public class OperationLogController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:log:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<OperationLog>> page(OperationLogPageQuery query) {
        return R.ok(operationLogService.page(query));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('monitor:log:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        operationLogService.remove(id);
        return R.ok();
    }

    /** 批量删除：id 清单随 body */
    @DeleteMapping
    @PreAuthorize("hasAuthority('monitor:log:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> removeBatch(@RequestBody List<Long> ids) {
        operationLogService.removeBatch(ids);
        return R.ok();
    }

    /** 全部清空（不辨筛选条件） */
    @DeleteMapping("/all")
    @PreAuthorize("hasAuthority('monitor:log:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> clear() {
        operationLogService.clear();
        return R.ok();
    }

    /** 导出当前筛选全集（复用查询条件不分页），xlsx */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('monitor:log:list') || hasAuthority('ROLE_ROOT')")
    public ResponseEntity<byte[]> export(OperationLogPageQuery query) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operation_log.xlsx")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(operationLogService.export(query));
    }
}
