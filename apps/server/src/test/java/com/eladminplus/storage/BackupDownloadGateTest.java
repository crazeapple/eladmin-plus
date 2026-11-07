package com.eladminplus.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 备份下载门禁（storage download 端点按 source 分型）：source=backup 行要求 database:backup
 * 权限点（ROLE_ROOT 直通），无权限 403；普通行维持「登录即可下载」语义零变化（无权限登录态同样 200）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BackupDownloadGateTest {

    /** 测试行文件名前缀，清理定位用（备份行与普通行同名前缀，不靠 source 区分清理面） */
    private static final String TEST_PREFIX = "gate-test-";

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

    private final List<Long> createdIds = new java.util.ArrayList<>();

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    /** 测试行先删对象后删行（复用存储域删除语义），不留孤儿对象 */
    private void cleanTestRows() {
        for (StorageFile row : storageFileMapper.selectList(Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, TEST_PREFIX))) {
            storageService.remove(row.getId());
        }
        createdIds.clear();
    }

    private String tokenWith(List<String> roles, List<String> permissions) {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, roles, permissions).accessToken();
    }

    private StorageFile createBackupRow() {
        byte[] payload = "gzip-sql-bytes".getBytes(StandardCharsets.UTF_8);
        StorageFile row = storageService.storeBackup(
                TEST_PREFIX + "backup.sql", new ByteArrayInputStream(payload), payload.length, "admin");
        createdIds.add(row.getId());
        return row;
    }

    private StorageFile createNormalRow() {
        StorageFile row = storageService.upload(
                new MockMultipartFile(
                        "file", TEST_PREFIX + "normal.txt", "text/plain", "plain-bytes".getBytes(StandardCharsets.UTF_8)),
                "admin");
        createdIds.add(row.getId());
        return row;
    }

    private byte[] download(String token, Long id) throws Exception {
        return mockMvc
                .perform(get("/storage/files/" + id + "/download").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    @Test
    void 备份行无权限登录态403() throws Exception {
        Long id = createBackupRow().getId();
        mockMvc
                .perform(get("/storage/files/" + id + "/download").header("Authorization", "Bearer " + tokenWith(List.of(), List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 备份行持databaseBackup权限点200内容原样() throws Exception {
        StorageFile row = createBackupRow();
        byte[] body = download(tokenWith(List.of(), List.of("database:backup")), row.getId());
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("gzip-sql-bytes");
    }

    @Test
    void 备份行root直通200内容原样() throws Exception {
        StorageFile row = createBackupRow();
        byte[] body = download(tokenWith(List.of("ROLE_ROOT"), List.of()), row.getId());
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("gzip-sql-bytes");
    }

    @Test
    void 普通行无权限登录态200登录即可回归() throws Exception {
        StorageFile row = createNormalRow();
        byte[] body = download(tokenWith(List.of(), List.of()), row.getId());
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("plain-bytes");
    }

    @Test
    void 未登录401语义不随门禁变化() throws Exception {
        Long id = createBackupRow().getId();
        mockMvc.perform(get("/storage/files/" + id + "/download")).andExpect(status().isUnauthorized());
    }
}
