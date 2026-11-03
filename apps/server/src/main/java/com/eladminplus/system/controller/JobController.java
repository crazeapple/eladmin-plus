package com.eladminplus.system.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.system.dto.JobSaveRequest;
import com.eladminplus.system.dto.JobView;
import com.eladminplus.system.service.JobService;
import jakarta.validation.Valid;
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

/** 岗位管理端点。 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:job:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<JobView>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer enabled) {
        return R.ok(jobService.page(page, size, name, enabled));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:job:add') || hasAuthority('ROLE_ROOT')")
    public R<Long> create(@Valid @RequestBody JobSaveRequest request) {
        return R.ok(jobService.save(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:job:edit') || hasAuthority('ROLE_ROOT')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody JobSaveRequest request) {
        jobService.save(new JobSaveRequest(id, request.name(), request.nameEn(), request.sort(), request.enabled()));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:job:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        jobService.remove(id);
        return R.ok();
    }
}
