package com.eladminplus.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.ops.domain.BackupRecord;
import com.eladminplus.ops.domain.DatabaseConfig;
import com.eladminplus.ops.mapper.BackupRecordMapper;
import com.eladminplus.ops.mapper.DatabaseConfigMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 手动备份全链（compose mysql 真实执行）：docker exec 容器内 mysqldump，stdout 流直写宿主机文件，
 * 落盘 sql 含建库语句与表结构、backup_record 落库（库名、大小、目录、文件名）；目录不存在自动创建；
 * 备份失败 5xx 含「备份执行失败」明确原因且不落记录。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BackupChainTest {

    /** 测试配置行备注前缀，清理定位用 */
    private static final String TEST_PREFIX = "chain-test-";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DatabaseConfigMapper databaseConfigMapper;
    @Autowired
    private BackupRecordMapper backupRecordMapper;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    private void cleanTestRows() {
        databaseConfigMapper.delete(
                Wrappers.<DatabaseConfig>lambdaQuery().likeRight(DatabaseConfig::getRemark, TEST_PREFIX));
        backupRecordMapper.delete(
                Wrappers.<BackupRecord>lambdaQuery().likeRight(BackupRecord::getDir, tempDir.toString()));
    }

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    /** 插入连接配置行：指向 compose 实例（localhost:3307 的 eladmin_plus 库），密码可指定以测失败链路 */
    private Long insertConfig(String password) {
        DatabaseConfig row = new DatabaseConfig();
        row.setHost("localhost");
        row.setPort(3307);
        row.setName("eladmin_plus");
        row.setUserName("root");
        row.setPassword(password);
        row.setRemark(TEST_PREFIX + "配置");
        databaseConfigMapper.insert(row);
        return row.getId();
    }

    /** 触发备份并返回响应体文本 */
    private String backup(String token, Long configId, String dir) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/database/configs/" + configId + "/backup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dir\":\"" + dir + "\"}"))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void 备份全链真实容器导出落盘并落记录() throws Exception {
        String token = rootToken();
        Long configId = insertConfig("root");
        // 嵌套不存在目录：验证自动创建（mkdir 语义）
        Path dir = tempDir.resolve("auto/nested");

        MvcResult result = mockMvc
                .perform(post("/database/configs/" + configId + "/backup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dir\":\"" + dir + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString();

        assertThat(Files.isDirectory(dir)).isTrue();
        // 响应与落库记录同形：文件名、目录、库名齐全
        assertThat(body).contains("\"dbName\":\"eladmin_plus\"").contains("\"dir\":\"" + dir + "\"");
        String fileName = body.replaceAll(".*\"fileName\":\"([^\"]+)\".*", "$1");
        assertThat(fileName).matches("eladmin_plus_\\d{14}\\.sql");

        Path file = dir.resolve(fileName);
        assertThat(Files.exists(file)).isTrue();
        String sql = Files.readString(file);
        assertThat(sql).contains("CREATE DATABASE").contains("CREATE TABLE");

        // 大小取落盘实际字节数
        long actual = Files.size(file);
        assertThat(body).contains("\"size\":" + actual);
        BackupRecord record = backupRecordMapper
                .selectOne(Wrappers.<BackupRecord>lambdaQuery().eq(BackupRecord::getFileName, fileName));
        assertThat(record).isNotNull();
        assertThat(record.getDbName()).isEqualTo("eladmin_plus");
        assertThat(record.getDir()).isEqualTo(dir.toString());
        assertThat(record.getSize()).isEqualTo(actual);
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void 凭据错误5xx含备份执行失败且不落记录() throws Exception {
        String token = rootToken();
        Long configId = insertConfig("wrong-pass");
        Path dir = tempDir.resolve("fail");

        String body = backup(token, configId, dir.toString());
        assertThat(body).contains("备份执行失败");
        // 半截文件就地清理：目标目录无 sql 残留
        try (Stream<Path> files = Files.exists(dir) ? Files.list(dir) : Stream.empty()) {
            assertThat(files.filter(path -> path.toString().endsWith(".sql")).count()).isZero();
        }
        assertThat(backupRecordMapper
                        .selectCount(Wrappers.<BackupRecord>lambdaQuery().likeRight(BackupRecord::getDir, tempDir.toString())))
                .isZero();
    }

    @Test
    void 未知配置id404() throws Exception {
        mockMvc
                .perform(post("/database/configs/999999/backup")
                        .header("Authorization", "Bearer " + rootToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dir\":\"" + tempDir.resolve("missing") + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 目录缺失400() throws Exception {
        Long configId = insertConfig("root");
        mockMvc
                .perform(post("/database/configs/" + configId + "/backup")
                        .header("Authorization", "Bearer " + rootToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dir\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数或操作不被接受"));
    }

    @Test
    void 无backup权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        Long configId = insertConfig("root");
        mockMvc
                .perform(post("/database/configs/" + configId + "/backup")
                        .header("Authorization", "Bearer " + plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dir\":\"" + tempDir.resolve("denied") + "\"}"))
                .andExpect(status().isForbidden());
    }
}
