package com.eladminplus.monitor.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.monitor.domain.OperationLog;
import com.eladminplus.monitor.dto.OperationLogExcelRow;
import com.eladminplus.monitor.dto.OperationLogPageQuery;
import com.eladminplus.monitor.log.LogAsyncConfig;
import com.eladminplus.monitor.mapper.OperationLogMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志域服务：@Log 切面的异步落表入口，分页筛选（操作人 / IP 精确、描述模糊、时间范围）、
 * 单删批删、全部清空与当前筛选全集的 Excel 导出。
 */
@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /** 异步落一行操作日志（日志是旁路关注，落表失败只记日志不影响业务响应） */
    @Async(LogAsyncConfig.LOG_WRITER_EXECUTOR)
    public void record(OperationLog entry) {
        try {
            operationLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("操作日志落表失败：description={}", entry.getDescription(), e);
        }
    }

    /** 操作日志分页：筛选见 {@link OperationLogPageQuery}，新记录在前 */
    public PageResult<OperationLog> page(OperationLogPageQuery query) {
        LambdaQueryWrapper<OperationLog> wrapper = filterOf(query);
        wrapper.orderByDesc(OperationLog::getId);
        Page<OperationLog> result =
                operationLogMapper.selectPage(new PageRequest(pageOf(query), sizeOf(query)).toMpPage(), wrapper);
        return PageResult.from(result);
    }

    /** 单条删除：行不存在按 404 拒绝 */
    public void remove(Long id) {
        if (operationLogMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        operationLogMapper.deleteById(id);
    }

    /** 批量删除：幂等删除在册行 */
    public void removeBatch(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            operationLogMapper.deleteByIds(ids);
        }
    }

    /** 全部清空 */
    public void clear() {
        operationLogMapper.delete(Wrappers.<OperationLog>emptyWrapper());
    }

    /** 导出当前筛选全集（复用查询条件不分页），xlsx 字节流 */
    public byte[] export(OperationLogPageQuery query) {
        List<OperationLogExcelRow> data = operationLogMapper.selectList(filterOf(query)).stream()
                .map(row -> {
                    OperationLogExcelRow excel = new OperationLogExcelRow();
                    excel.setUsername(row.getUsername());
                    excel.setIp(row.getIp());
                    excel.setDescription(row.getDescription());
                    excel.setElapsedMs(row.getElapsedMs());
                    excel.setRequestParam(row.getRequestParam());
                    excel.setCreatedAt(
                            row.getCreatedAt() == null ? null : EXPORT_TIME_FORMAT.format(row.getCreatedAt()));
                    return excel;
                })
                .toList();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, OperationLogExcelRow.class).sheet("操作日志").doWrite(data);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("操作日志导出失败", e);
        }
    }

    /** 筛选条件组装：分页与导出共用一套（操作人与 IP 精确、描述模糊、创建时间闭区间） */
    private LambdaQueryWrapper<OperationLog> filterOf(OperationLogPageQuery query) {
        LambdaQueryWrapper<OperationLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(query.username())) {
            wrapper.eq(OperationLog::getUsername, query.username());
        }
        if (StringUtils.hasText(query.ip())) {
            wrapper.eq(OperationLog::getIp, query.ip());
        }
        if (StringUtils.hasText(query.description())) {
            wrapper.like(OperationLog::getDescription, query.description());
        }
        if (query.startAt() != null) {
            wrapper.ge(OperationLog::getCreatedAt, query.startAt());
        }
        if (query.endAt() != null) {
            wrapper.le(OperationLog::getCreatedAt, query.endAt());
        }
        return wrapper;
    }

    private static int pageOf(OperationLogPageQuery query) {
        return query.page() == null ? 0 : query.page();
    }

    private static int sizeOf(OperationLogPageQuery query) {
        return query.size() == null ? 10 : query.size();
    }
}
