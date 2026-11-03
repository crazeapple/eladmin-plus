package com.eladminplus.system.controller;

import com.eladminplus.common.R;
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
import org.springframework.web.bind.annotation.RestController;

/** 定时任务管理端点：白名单清单 + 任务 CRUD（操作与执行日志随配置域推进补齐）。 */
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
}
