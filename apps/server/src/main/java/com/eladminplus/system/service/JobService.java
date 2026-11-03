package com.eladminplus.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.common.translation.TranslationService;
import com.eladminplus.system.domain.Job;
import com.eladminplus.system.domain.UserJob;
import com.eladminplus.system.dto.JobSaveRequest;
import com.eladminplus.system.dto.JobView;
import com.eladminplus.system.mapper.JobMapper;
import com.eladminplus.system.mapper.UserJobMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 岗位管理：分页筛选、CRUD（多语言）、关联删除校验。 */
@Service
public class JobService {

    private final JobMapper jobMapper;
    private final UserJobMapper userJobMapper;
    private final TranslationService translationService;

    public JobService(JobMapper jobMapper, UserJobMapper userJobMapper, TranslationService translationService) {
        this.jobMapper = jobMapper;
        this.userJobMapper = userJobMapper;
        this.translationService = translationService;
    }

    /** 岗位分页：名称模糊（默认语言列）、状态筛选；名称按当前请求语言覆盖 */
    public PageResult<JobView> page(int page, int size, String name, Integer enabled) {
        Page<Job> mpPage = jobMapper.selectPage(new PageRequest(page, size).toMpPage(),
                Wrappers.<Job>lambdaQuery()
                        .like(StringUtils.hasText(name), Job::getName, name)
                        .eq(enabled != null, Job::getEnabled, enabled)
                        .orderByAsc(Job::getSort)
                        .orderByAsc(Job::getId));
        var locale = translationService.currentLocale();
        var overrides = translationService.overrides(
                "job", "name", mpPage.getRecords().stream().map(Job::getId).toList(), locale);
        List<JobView> content = mpPage.getRecords().stream()
                .map(job -> new JobView(
                        job.getId(), overrides.getOrDefault(job.getId(), job.getName()), job.getSort(),
                        job.getEnabled()))
                .toList();
        return new PageResult<>(content, mpPage.getTotal(), (int) (mpPage.getCurrent() - 1), (int) mpPage.getSize());
    }

    @Transactional
    public Long save(JobSaveRequest request) {
        Job job = new Job();
        job.setId(request.id());
        job.setName(request.name());
        job.setSort(request.sort() == null ? 0 : request.sort());
        job.setEnabled(request.enabled());
        if (job.getId() == null) {
            jobMapper.insert(job);
        } else {
            if (jobMapper.selectById(job.getId()) == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
            }
            jobMapper.updateById(job);
        }
        translationService.syncOverride("job", job.getId(), "name", TranslationService.EN_LOCALE, request.nameEn());
        return job.getId();
    }

    @Transactional
    public void remove(Long id) {
        if (jobMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        Long refCount = userJobMapper.selectCount(Wrappers.<UserJob>lambdaQuery().eq(UserJob::getJobId, id));
        if (refCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.job-in-use");
        }
        jobMapper.deleteById(id);
        translationService.removeAll("job", id);
    }
}
