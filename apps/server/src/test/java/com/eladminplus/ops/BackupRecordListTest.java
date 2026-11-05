package com.eladminplus.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import com.eladminplus.storage.service.StorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 备份记录端点（storage_file source=backup 视图）：分页、文件名模糊筛选、手传文件不混入；删除代理
 * 同删对象与行、文件已失仅删记录；导出代理流式回原名、已删 404；无权限 403；非备份行与未知 id 404。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BackupRecordListTest {

    /** 测试行文件名前缀，清理定位用 */
    private static final String TEST_PREFIX = "list-test-db";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StorageFileMapper storageFileMapper;
    @Autowired
    private StorageService storageService;

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    /** 测试行清对象后删行（真实对象行经存储域删，fake 键行删除幂等不报错） */
    private void cleanTestRows() {
        for (StorageFile row :
                storageFileMapper.selectList(Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, TEST_PREFIX))) {
            storageService.remove(row.getId());
        }
    }

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    /** 插入备份来源测试行（fake 存储键，无真实对象），返回行 id */
    private Long insertBackupRow(String fileName, long size) {
        StorageFile row = new StorageFile();
        row.setName(fileName);
        row.setStorageKey("209901/" + fileName + "-" + System.nanoTime() + ".sql");
        row.setExt("sql");
        row.setSize(size);
        row.setMime("application/octet-stream");
        row.setEngine("rustfs");
        row.setSource(StorageService.SOURCE_BACKUP);
        row.setUploader("admin");
        storageFileMapper.insert(row);
        return row.getId();
    }

    /** 插入手传文件行（source 空），返回行 id */
    private Long insertManualRow(String fileName) {
        StorageFile row = new StorageFile();
        row.setName(fileName);
        row.setStorageKey("209901/" + fileName + "-" + System.nanoTime() + ".bin");
        row.setExt("bin");
        row.setSize(7L);
        row.setMime("application/octet-stream");
        row.setEngine("rustfs");
        row.setSource("");
        row.setUploader("admin");
        storageFileMapper.insert(row);
        return row.getId();
    }

    /** 经存储服务真实落册一条备份（含真实对象），返回行 */
    private StorageFile storeRealBackup(String fileName, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return storageService.storeBackup(
                fileName, new ByteArrayInputStream(bytes), bytes.length, "admin");
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
        insertBackupRow(TEST_PREFIX + "-first.sql", 12L);
        Long lastId = insertBackupRow(TEST_PREFIX + "-last.sql", 34L);

        // size=1 验分页形状：只回一条且为新记录（id 倒序）；total 含本次至少两行（他备份行共存，不断言精确值）
        String body = listBody("?page=0&size=1", token);
        assertThat(body).contains("\"page\":0").contains("\"size\":1");
        assertThat(body.indexOf(TEST_PREFIX + "-first.sql")).isNegative();
        assertThat(body).contains("\"id\":" + lastId);
        long total = Long.parseLong(body.replaceAll(".*\"total\":(\\d+).*", "$1"));
        assertThat(total).isGreaterThanOrEqualTo(2);
    }

    @Test
    void 文件名模糊筛选生效() throws Exception {
        String token = rootToken();
        insertBackupRow(TEST_PREFIX + "-季度报告.sql", 12L);
        insertBackupRow(TEST_PREFIX + "-README.sql", 34L);

        MvcResult hit = mockMvc
                .perform(get("/database/records").param("filename", "季度报告")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String hitBody = hit.getResponse().getContentAsString();
        assertThat(hitBody).contains(TEST_PREFIX + "-季度报告.sql");
        assertThat(hitBody).doesNotContain(TEST_PREFIX + "-README.sql");

        MvcResult miss = mockMvc
                .perform(get("/database/records").param("filename", "无此文件名").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String missBody = miss.getResponse().getContentAsString();
        assertThat(missBody).doesNotContain(TEST_PREFIX + "-季度报告.sql");
        assertThat(missBody).doesNotContain(TEST_PREFIX + "-README.sql");
        assertThat(missBody).contains("\"total\":0");
    }

    @Test
    void 手传文件不混入记录视图() throws Exception {
        String token = rootToken();
        Long backupId = insertBackupRow(TEST_PREFIX + "-backup.sql", 12L);
        Long manualId = insertManualRow(TEST_PREFIX + "-manual-upload.sql");

        String body = listBody("", token);
        assertThat(body).contains(TEST_PREFIX + "-backup.sql");
        assertThat(body).doesNotContain(TEST_PREFIX + "-manual-upload.sql");

        // 手传行经记录端点的删除与导出同样 404（行本身不受影响）
        mockMvc.perform(delete("/database/records/" + manualId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        assertThat(storageFileMapper.selectById(manualId)).isNotNull();
        mockMvc.perform(get("/database/records/" + manualId + "/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        assertThat(storageFileMapper.selectById(backupId)).isNotNull();
    }

    @Test
    void 删除记录同删对象与行() throws Exception {
        String token = rootToken();
        StorageFile row = storeRealBackup(TEST_PREFIX + "-drop.sql", "dump content");
        Long id = row.getId();
        assertThat(storageFileMapper.selectById(id)).isNotNull();

        mockMvc.perform(delete("/database/records/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(storageFileMapper.selectById(id)).isNull();
        // 对象同删：按行内引擎路由取流按 404 语义
        assertThatThrownBy(() -> storageService.open(row))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 文件已失仅删记录() throws Exception {
        String token = rootToken();
        Long id = insertBackupRow(TEST_PREFIX + "-ghost.sql", 4L);

        mockMvc.perform(delete("/database/records/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(storageFileMapper.selectById(id)).isNull();
    }

    @Test
    void 导出代理流式回原名与已删404() throws Exception {
        String token = rootToken();
        StorageFile row = storeRealBackup(TEST_PREFIX + "-export.sql", "export me");
        Long id = row.getId();

        MvcResult export = mockMvc
                .perform(get("/database/records/" + id + "/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(export.getResponse().getHeader("Content-Disposition")).contains(TEST_PREFIX + "-export.sql");
        assertThat(export.getResponse().getContentAsByteArray())
                .isEqualTo("export me".getBytes(StandardCharsets.UTF_8));

        // 已删文件 404：行与对象均已不在（存储域同删语义）
        storageService.remove(id);
        mockMvc.perform(get("/database/records/" + id + "/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 未知id404() throws Exception {
        String token = rootToken();
        mockMvc.perform(delete("/database/records/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/database/records/999999/export").header("Authorization", "Bearer " + token))
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
        mockMvc.perform(get("/database/records/1/export").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }
}
