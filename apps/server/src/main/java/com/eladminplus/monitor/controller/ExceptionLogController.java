package com.eladminplus.monitor.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.monitor.domain.ExceptionLog;
import com.eladminplus.monitor.dto.ExceptionLogPageQuery;
import com.eladminplus.monitor.service.ExceptionLogService;
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

/** 异常日志维护端点：与操作日志同构——分页筛选、单删批删、全部清空与当前筛选全集 Excel 导出。 */
@RestController
@RequestMapping("/logs/exceptions")
public class ExceptionLogController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExceptionLogService exceptionLogService;

    public ExceptionLogController(ExceptionLogService exceptionLogService) {
        this.exceptionLogService = exceptionLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('monitor:log:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<ExceptionLog>> page(ExceptionLogPageQuery query) {
        return R.ok(exceptionLogService.page(query));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('monitor:log:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        exceptionLogService.remove(id);
        return R.ok();
    }

    /** 批量删除：id 清单随 body */
    @DeleteMapping
    @PreAuthorize("hasAuthority('monitor:log:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> removeBatch(@RequestBody List<Long> ids) {
        exceptionLogService.removeBatch(ids);
        return R.ok();
    }

    /** 全部清空（不辨筛选条件） */
    @DeleteMapping("/all")
    @PreAuthorize("hasAuthority('monitor:log:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> clear() {
        exceptionLogService.clear();
        return R.ok();
    }

    /** 导出当前筛选全集（复用查询条件不分页，含堆栈列），xlsx */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('monitor:log:list') || hasAuthority('ROLE_ROOT')")
    public ResponseEntity<byte[]> export(ExceptionLogPageQuery query) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=exception_log.xlsx")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(exceptionLogService.export(query));
    }
}
