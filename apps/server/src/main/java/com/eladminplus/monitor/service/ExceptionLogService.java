package com.eladminplus.monitor.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.monitor.domain.ExceptionLog;
import com.eladminplus.monitor.dto.ExceptionLogExcelRow;
import com.eladminplus.monitor.dto.ExceptionLogPageQuery;
import com.eladminplus.monitor.log.LogAsyncConfig;
import com.eladminplus.monitor.mapper.ExceptionLogMapper;
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
 * 异常日志域服务：全局异常处理器 5xx 分支钩子的异步落表入口，分页筛选
 * （操作人 / IP 精确、异常消息模糊、时间范围）、单删批删、全部清空与当前筛选全集的 Excel 导出。
 */
@Service
public class ExceptionLogService {

    private static final Logger log = LoggerFactory.getLogger(ExceptionLogService.class);

    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExceptionLogMapper exceptionLogMapper;

    public ExceptionLogService(ExceptionLogMapper exceptionLogMapper) {
        this.exceptionLogMapper = exceptionLogMapper;
    }

    /** 异步落一行异常日志（落表失败只记日志，不影响错误响应） */
    @Async(LogAsyncConfig.LOG_WRITER_EXECUTOR)
    public void record(ExceptionLog entry) {
        try {
            exceptionLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("异常日志落表失败：name={}", entry.getName(), e);
        }
    }

    /** 异常日志分页：筛选见 {@link ExceptionLogPageQuery}，新记录在前 */
    public PageResult<ExceptionLog> page(ExceptionLogPageQuery query) {
        LambdaQueryWrapper<ExceptionLog> wrapper = filterOf(query);
        wrapper.orderByDesc(ExceptionLog::getId);
        Page<ExceptionLog> result =
                exceptionLogMapper.selectPage(new PageRequest(pageOf(query), sizeOf(query)).toMpPage(), wrapper);
        return PageResult.from(result);
    }

    /** 单条删除：行不存在按 404 拒绝 */
    public void remove(Long id) {
        if (exceptionLogMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        exceptionLogMapper.deleteById(id);
    }

    /** 批量删除：幂等删除在册行 */
    public void removeBatch(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            exceptionLogMapper.deleteByIds(ids);
        }
    }

    /** 全部清空 */
    public void clear() {
        exceptionLogMapper.delete(Wrappers.<ExceptionLog>emptyWrapper());
    }

    /** 导出当前筛选全集（复用查询条件不分页，含堆栈列），xlsx 字节流 */
    public byte[] export(ExceptionLogPageQuery query) {
        List<ExceptionLogExcelRow> data = exceptionLogMapper.selectList(filterOf(query)).stream()
                .map(row -> {
                    ExceptionLogExcelRow excel = new ExceptionLogExcelRow();
                    excel.setUsername(row.getUsername());
                    excel.setIp(row.getIp());
                    excel.setName(row.getName());
                    excel.setMessage(row.getMessage());
                    excel.setStackTrace(row.getStackTrace());
                    excel.setCreatedAt(
                            row.getCreatedAt() == null ? null : EXPORT_TIME_FORMAT.format(row.getCreatedAt()));
                    return excel;
                })
                .toList();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, ExceptionLogExcelRow.class).sheet("异常日志").doWrite(data);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("异常日志导出失败", e);
        }
    }

    /** 筛选条件组装：分页与导出共用一套（操作人与 IP 精确、异常消息模糊、创建时间闭区间） */
    private LambdaQueryWrapper<ExceptionLog> filterOf(ExceptionLogPageQuery query) {
        LambdaQueryWrapper<ExceptionLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(query.username())) {
            wrapper.eq(ExceptionLog::getUsername, query.username());
        }
        if (StringUtils.hasText(query.ip())) {
            wrapper.eq(ExceptionLog::getIp, query.ip());
        }
        if (StringUtils.hasText(query.message())) {
            wrapper.like(ExceptionLog::getMessage, query.message());
        }
        if (query.startAt() != null) {
            wrapper.ge(ExceptionLog::getCreatedAt, query.startAt());
        }
        if (query.endAt() != null) {
            wrapper.le(ExceptionLog::getCreatedAt, query.endAt());
        }
        return wrapper;
    }

    private static int pageOf(ExceptionLogPageQuery query) {
        return query.page() == null ? 0 : query.page();
    }

    private static int sizeOf(ExceptionLogPageQuery query) {
        return query.size() == null ? 10 : query.size();
    }
}
