package com.eladminplus.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.ops.domain.DatabaseConfig;
import com.eladminplus.ops.mapper.DatabaseConfigMapper;
import com.eladminplus.storage.StorageProperties;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import com.eladminplus.storage.service.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 手动备份全链（dev compose 备份 sidecar 真实执行）：HTTP 调内网端点，sidecar 自带 dump 客户端直连
 * mysql 容器，gzip 流回后经存储域落当前配置引擎并打标 source=backup，storage_file 行在册
 * （原名形态、大小为 gzip 实际字节数、解压含建库语句）；备份失败 5xx 含「备份执行失败」明确原因
 * 且无 storage_file 行。
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
    private StorageFileMapper storageFileMapper;
    @Autowired
    private StorageService storageService;
    @Autowired
    private StorageProperties storageProperties;

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    /** 备份行清对象后删行（本库 source=backup 行均出自本测试，测试库内无他源）；配置行按备注前缀清 */
    private void cleanTestRows() {
        for (StorageFile row :
                storageFileMapper.selectList(Wrappers.<StorageFile>lambdaQuery().eq(StorageFile::getSource, StorageService.SOURCE_BACKUP))) {
            storageService.remove(row.getId());
        }
        databaseConfigMapper.delete(
                Wrappers.<DatabaseConfig>lambdaQuery().likeRight(DatabaseConfig::getRemark, TEST_PREFIX));
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

    private long backupRowCount() {
        return storageFileMapper.selectCount(
                Wrappers.<StorageFile>lambdaQuery().eq(StorageFile::getSource, StorageService.SOURCE_BACKUP));
    }

    @Test
    void 备份全链真实容器导出经存储域落册() throws Exception {
        String token = rootToken();
        Long configId = insertConfig("root");

        MvcResult result = mockMvc
                .perform(post("/database/configs/" + configId + "/backup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString();

        // 响应即行视图：原名形态、库名解析展示
        assertThat(body).contains("\"dbName\":\"eladmin_plus\"");
        String fileName = body.replaceAll(".*\"fileName\":\"([^\"]+)\".*", "$1");
        assertThat(fileName).matches("eladmin_plus_\\d{14}\\.sql\\.gz");
        long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        StorageFile row = storageFileMapper.selectById(id);
        assertThat(row).isNotNull();
        assertThat(row.getSource()).isEqualTo(StorageService.SOURCE_BACKUP);
        assertThat(row.getName()).isEqualTo(fileName);
        assertThat(row.getExt()).isEqualTo("sql.gz");
        assertThat(row.getEngine()).isEqualTo(storageProperties.engine());
        assertThat(row.getUploader()).isEqualTo("admin");

        // 落引擎内容真实可读：gzip 包裹的 SQL 解压含建库语句与表结构语句，行内大小即 gzip 实际字节数
        try (InputStream in = storageService.open(row)) {
            byte[] bytes = in.readAllBytes();
            String sql;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                sql = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertThat(sql).contains("CREATE DATABASE").contains("CREATE TABLE");
            assertThat(row.getSize()).isEqualTo(bytes.length);
        }
    }

    @Test
    void 凭据错误5xx含备份执行失败且无行() throws Exception {
        String token = rootToken();
        Long configId = insertConfig("wrong-pass");

        MvcResult result = mockMvc
                .perform(post("/database/configs/" + configId + "/backup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("备份执行失败");
        assertThat(backupRowCount()).isZero();
    }

    @Test
    void 未知配置id404() throws Exception {
        mockMvc
                .perform(post("/database/configs/999999/backup").header("Authorization", "Bearer " + rootToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 无backup权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        Long configId = insertConfig("root");
        mockMvc
                .perform(post("/database/configs/" + configId + "/backup").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }
}
