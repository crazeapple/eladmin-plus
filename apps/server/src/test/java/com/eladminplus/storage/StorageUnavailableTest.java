package com.eladminplus.storage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** RustFS 不可达：端点指向无监听端口，上传与按行路由下载均报 5xx 含「存储服务不可用」明确消息。
 *
 * <p>rustfs 引擎形态专属故障注入（引擎切换为 local 时语义不适用）：类标 {@code @Tag("infra")}，
 * 快层 CI 以 -PexcludeInfraTags 排除；无对应 E2E，本地全量跑覆盖。
 */
@Tag("infra")
@SpringBootTest(properties = {"storage.rustfs.endpoint=http://localhost:59999", "storage.rustfs.bucket=eladmin-plus-dead"})
@AutoConfigureMockMvc
class StorageUnavailableTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StorageFileMapper storageFileMapper;

    @AfterEach
    void cleanAfter() {
        storageFileMapper.delete(
                Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, "route-test-"));
    }

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    @Test
    void 不可达上传报存储服务不可用() throws Exception {
        mockMvc
                .perform(multipart("/storage/files")
                        .file(new MockMultipartFile("file", "dead-endpoint.txt", "text/plain", new byte[] {1}))
                        .header("Authorization", "Bearer " + rootToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("存储服务不可用"));
    }

    @Test
    void 不可达引擎行下载报存储服务不可用() throws Exception {
        StorageFile row = new StorageFile();
        row.setName("route-test-dead.txt");
        row.setStorageKey("209901/" + UUID.randomUUID() + ".txt");
        row.setExt("txt");
        row.setSize(1L);
        row.setMime("text/plain");
        row.setEngine("rustfs");
        row.setUploader("admin");
        storageFileMapper.insert(row);
        mockMvc
                .perform(get("/storage/files/{id}/download", row.getId()).header("Authorization", "Bearer " + rootToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("存储服务不可用"));
    }
}
