package com.eladminplus.system.controller;

import com.eladminplus.common.R;
import com.eladminplus.system.dto.TaskTargetView;
import com.eladminplus.system.task.TaskMethodScanner;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 定时任务管理端点：白名单清单 + 任务 CRUD 与操作（随配置域推进补齐）。 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskMethodScanner taskMethodScanner;

    public TaskController(TaskMethodScanner taskMethodScanner) {
        this.taskMethodScanner = taskMethodScanner;
    }

    /** 白名单发现：@TaskMethod 标记方法清单（任务表单下拉数据源） */
    @GetMapping("/targets")
    @PreAuthorize("hasAuthority('system:task:list') || hasAuthority('ROLE_ROOT')")
    public R<List<TaskTargetView>> targets() {
        return R.ok(taskMethodScanner.scan());
    }
}
