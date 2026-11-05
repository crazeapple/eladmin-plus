package com.eladminplus.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.ops.domain.BackupRecord;
import com.eladminplus.ops.mapper.BackupRecordMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 备份记录端点：分页正确；删除记录同删文件、文件已失仅删记录；无权限 403；未知 id 404。 */
@SpringBootTest
@AutoConfigureMockMvc
class BackupRecordListTest {

    /** 测试行库名前缀，清理定位用 */
    private static final String TEST_PREFIX = "list-test-db";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
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
        backupRecordMapper.delete(
                Wrappers.<BackupRecord>lambdaQuery().likeRight(BackupRecord::getDbName, TEST_PREFIX));
    }

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    /** 插入测试记录行，返回行 id；realFile 非空时落真实文件（测删记录同删文件） */
    private Long insertRow(String fileName, Long size, boolean withRealFile) throws Exception {
        BackupRecord row = new BackupRecord();
        row.setFileName(fileName);
        row.setDir(tempDir.toString());
        row.setDbName(TEST_PREFIX + "-" + System.nanoTime());
        row.setSize(size);
        backupRecordMapper.insert(row);
        if (withRealFile) {
            Files.writeString(tempDir.resolve(fileName), "dump");
        }
        return row.getId();
    }

    private String listBody(String queryString, String token) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/database/records" + queryString).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void 分页与新记录在前() throws Exception {
        String token = rootToken();
        insertRow(TEST_PREFIX + "-first.sql", 12L, false);
        Long lastId = insertRow(TEST_PREFIX + "-last.sql", 34L, false);

        // size=1 验分页形状：只回一条且为新记录（id 倒序）；total 含本次至少两行（历史成功备份行共存，不断言精确值）
        String body = listBody("?page=0&size=1", token);
        assertThat(body).contains("\"page\":0").contains("\"size\":1");
        assertThat(body.indexOf(TEST_PREFIX + "-first.sql")).isNegative();
        assertThat(body).contains("\"id\":" + lastId);
        long total = Long.parseLong(body.replaceAll(".*\"total\":(\\d+).*", "$1"));
        assertThat(total).isGreaterThanOrEqualTo(2);
    }

    @Test
    void 删除记录同删文件() throws Exception {
        String token = rootToken();
        String fileName = TEST_PREFIX + "-drop.sql";
        Long id = insertRow(fileName, 4L, true);
        Path file = tempDir.resolve(fileName);
        assertThat(Files.exists(file)).isTrue();

        mockMvc.perform(delete("/database/records/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(backupRecordMapper.selectById(id)).isNull();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void 文件已失仅删记录() throws Exception {
        String token = rootToken();
        Long id = insertRow(TEST_PREFIX + "-ghost.sql", 4L, false);

        mockMvc.perform(delete("/database/records/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(backupRecordMapper.selectById(id)).isNull();
    }

    @Test
    void 未知id404() throws Exception {
        mockMvc.perform(delete("/database/records/999999").header("Authorization", "Bearer " + rootToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 无权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc.perform(get("/database/records").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/database/records/1").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }
}
