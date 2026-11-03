package com.eladminplus.system.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.system.domain.TaskLog;
import com.eladminplus.system.dto.TaskSaveRequest;
import com.eladminplus.system.dto.TaskTargetView;
import com.eladminplus.system.dto.TaskView;
import com.eladminplus.system.service.TaskService;
import com.eladminplus.system.task.TaskMethodScanner;
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

/** 定时任务管理端点：白名单清单 + 任务 CRUD + 操作（暂停 / 恢复 / 立即执行）与执行日志。 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskMethodScanner taskMethodScanner;
    private final TaskService taskService;

    public TaskController(TaskMethodScanner taskMethodScanner, TaskService taskService) {
        this.taskMethodScanner = taskMethodScanner;
        this.taskService = taskService;
    }

    /** 白名单发现：@TaskMethod 标记方法清单（任务表单下拉数据源） */
    @GetMapping("/targets")
    @PreAuthorize("hasAuthority('system:task:list') || hasAuthority('ROLE_ROOT')")
    public R<List<TaskTargetView>> targets() {
        return R.ok(taskMethodScanner.scan());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:task:list') || hasAuthority('ROLE_ROOT')")
    public R<List<TaskView>> list() {
        return R.ok(taskService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:task:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> create(@Valid @RequestBody TaskSaveRequest request) {
        return R.ok(taskService.save(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:task:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TaskSaveRequest request) {
        taskService.save(new TaskSaveRequest(
                id, request.name(), request.beanName(), request.methodName(), request.param(),
                request.cron(), request.status()));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:task:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        taskService.remove(id);
        return R.ok();
    }

    /** 暂停：trigger 状态翻 PAUSED */
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('system:task:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> pause(@PathVariable Long id) {
        taskService.pause(id);
        return R.ok();
    }

    /** 恢复：重新排程 */
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('system:task:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> resume(@PathVariable Long id) {
        taskService.resume(id);
        return R.ok();
    }

    /** 立即执行一次：异步触发不阻塞请求 */
    @PostMapping("/{id}/exec")
    @PreAuthorize("hasAuthority('system:task:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> executeOnce(@PathVariable Long id) {
        taskService.executeOnce(id);
        return R.ok();
    }

    /** 执行日志分页（按任务过滤） */
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('system:task:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<TaskLog>> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(taskService.logs(id, page, size));
    }
}
