package com.eladminplus.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 存储列表端点：分页 + 原名模糊 + 上传人精确 + 创建时间范围各维筛选；无权限 403。 */
@SpringBootTest
@AutoConfigureMockMvc
class StorageListTest {

    /** 测试行文件名前缀，清理定位用 */
    private static final String TEST_PREFIX = "list-test-";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StorageFileMapper storageFileMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    private void cleanTestRows() {
        storageFileMapper.delete(Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, TEST_PREFIX));
    }

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    /** 插入测试行（上传人与时间列直接落库以测筛选），返回行 id */
    private Long insertRow(String name, String uploader, String createdAt) {
        StorageFile row = new StorageFile();
        row.setName(name);
        row.setStorageKey("209901/" + name + "-" + System.nanoTime() + ".bin");
        row.setExt("bin");
        row.setSize(12L);
        row.setMime("application/octet-stream");
        row.setEngine("rustfs");
        row.setUploader(uploader);
        storageFileMapper.insert(row);
        if (createdAt != null) {
            jdbc.update("UPDATE storage_file SET created_at = ? WHERE id = ?", createdAt, row.getId());
        }
        return row.getId();
    }

    private String listBody(String queryString, String token) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/storage/files" + queryString).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 分页与新记录在前() throws Exception {
        String token = rootToken();
        insertRow(TEST_PREFIX + "first.txt", "admin", null);
        Long lastId = insertRow(TEST_PREFIX + "last.txt", "admin", null);

        // 前缀过滤圈定测试行集（与他测共存不串数），size=1 验分页形状
        String body = listBody("?page=0&size=1&name=" + TEST_PREFIX, token);
        assertThat(body).contains("\"total\":2");
        assertThat(body).contains("\"page\":0").contains("\"size\":1");
        // 只回一条且为新记录（id 倒序）
        assertThat(body.indexOf(TEST_PREFIX + "first.txt")).isNegative();
        assertThat(body).contains("\"id\":" + lastId);
    }

    @Test
    void 原名模糊与上传人精确筛选生效() throws Exception {
        String token = rootToken();
        insertRow(TEST_PREFIX + "季度报告.txt", "admin", null);
        insertRow(TEST_PREFIX + "README.txt", "demo", null);

        String likeBody = listBody("?name=季度报告", token);
        assertThat(likeBody).contains(TEST_PREFIX + "季度报告.txt");
        assertThat(likeBody).doesNotContain(TEST_PREFIX + "README.txt");

        String uploaderBody = listBody("?uploader=demo", token);
        assertThat(uploaderBody).contains(TEST_PREFIX + "README.txt");
        assertThat(uploaderBody).doesNotContain(TEST_PREFIX + "季度报告.txt");
    }

    @Test
    void 时间范围筛选生效() throws Exception {
        String token = rootToken();
        insertRow(TEST_PREFIX + "old.txt", "admin", "2026-01-01 08:00:00");
        insertRow(TEST_PREFIX + "new.txt", "admin", null);

        String rangeBody = listBody("?startTime=2025-12-31T00:00:00&endTime=2026-01-01T23:59:59", token);
        assertThat(rangeBody).contains(TEST_PREFIX + "old.txt");
        assertThat(rangeBody).doesNotContain(TEST_PREFIX + "new.txt");

        // 时间格式非法按 400
        mockMvc
                .perform(get("/storage/files").param("startTime", "not-a-date")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 无list权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc.perform(get("/storage/files").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }
}
