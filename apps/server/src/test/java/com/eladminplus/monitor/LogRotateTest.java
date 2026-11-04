package com.eladminplus.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.monitor.domain.ExceptionLog;
import com.eladminplus.monitor.domain.OperationLog;
import com.eladminplus.monitor.mapper.ExceptionLogMapper;
import com.eladminplus.monitor.mapper.OperationLogMapper;
import com.eladminplus.monitor.service.LogCleanupService;
import com.eladminplus.monitor.task.LogCleanupJob;
import com.eladminplus.system.dto.TaskTargetView;
import com.eladminplus.system.task.TaskMethodScanner;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 日志自动轮转：清理任务在白名单在册、超保留天数两表行被删、保留期内行不动、非法配置抛错不误删。 */
@SpringBootTest
class LogRotateTest {

    /** 测试行用户名前缀，清理定位用 */
    private static final String TEST_PREFIX = "rotatetest.";

    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TaskMethodScanner taskMethodScanner;
    @Autowired
    private LogCleanupService logCleanupService;
    @Autowired
    private LogCleanupJob logCleanupJob;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private ExceptionLogMapper exceptionLogMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        operationLogMapper.delete(
                Wrappers.<OperationLog>lambdaQuery().likeRight(OperationLog::getUsername, TEST_PREFIX));
        exceptionLogMapper.delete(
                Wrappers.<ExceptionLog>lambdaQuery().likeRight(ExceptionLog::getUsername, TEST_PREFIX));
    }

    /** 插入测试行并直写创建时间（回拨 or 前拨），返回行 id */
    private Long insertOperationRow(String suffix, String createdAt) {
        OperationLog row = new OperationLog();
        row.setUsername(TEST_PREFIX + suffix);
        row.setDescription("轮转测试行");
        operationLogMapper.insert(row);
        jdbc.update("UPDATE operation_log SET created_at = ? WHERE id = ?", createdAt, row.getId());
        return row.getId();
    }

    private Long insertExceptionRow(String suffix, String createdAt) {
        ExceptionLog row = new ExceptionLog();
        row.setUsername(TEST_PREFIX + suffix);
        row.setName(IllegalStateException.class.getName());
        row.setMessage("轮转测试行");
        exceptionLogMapper.insert(row);
        jdbc.update("UPDATE exception_log SET created_at = ? WHERE id = ?", createdAt, row.getId());
        return row.getId();
    }

    @Test
    void 清理任务在白名单在册() {
        List<TaskTargetView> targets = taskMethodScanner.scan();
        assertThat(targets)
                .anyMatch(target -> "logCleanupJob".equals(target.beanName())
                        && "clean".equals(target.methodName()));
        // 预注册行的 Bean 与方法即本任务 Bean（V7 种子与白名单一致）
        assertThat(logCleanupJob).isNotNull();
    }

    @Test
    void 超保留天数两表行被删且保留期内行不动() {
        String expired = SQL_TIME.format(LocalDateTime.now().minusDays(31));
        String kept = SQL_TIME.format(LocalDateTime.now().minusDays(29));
        Long oldOperationId = insertOperationRow("old", expired);
        Long newOperationId = insertOperationRow("new", kept);
        Long oldExceptionId = insertExceptionRow("old", expired);
        Long newExceptionId = insertExceptionRow("new", kept);

        logCleanupService.clean();

        assertThat(operationLogMapper.selectById(oldOperationId)).isNull();
        assertThat(operationLogMapper.selectById(newOperationId)).isNotNull();
        assertThat(exceptionLogMapper.selectById(oldExceptionId)).isNull();
        assertThat(exceptionLogMapper.selectById(newExceptionId)).isNotNull();
    }

    @Test
    void 保留天数配置非法抛错不误删() {
        String expired = SQL_TIME.format(LocalDateTime.now().minusDays(31));
        Long operationId = insertOperationRow("illegal", expired);
        Long exceptionId = insertExceptionRow("illegal", expired);

        for (String badValue : List.of("abc", "0", "-1", " ")) {
            LogCleanupService badService =
                    new LogCleanupService(operationLogMapper, exceptionLogMapper, badValue);
            assertThatThrownBy(badService::clean)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("保留天数配置非法");
        }

        // 抛错路径零删除：超期行原样在册
        assertThat(operationLogMapper.selectById(operationId)).isNotNull();
        assertThat(exceptionLogMapper.selectById(exceptionId)).isNotNull();
    }
}
