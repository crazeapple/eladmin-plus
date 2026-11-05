package com.eladminplus.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** 上传体积限制：storage.max-size 权威判定，超限拒 413（限制配置化，本测以 1KB 上限验证语义）。 */
@SpringBootTest(properties = {"storage.engine=local", "storage.max-size=1KB", "storage.local.base-path=build/test-storage-limit"})
@AutoConfigureMockMvc
class UploadLimitTest {

    /** 测试行文件名前缀，清理定位用 */
    private static final String TEST_PREFIX = "limit-test-";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StorageFileMapper storageFileMapper;

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
        deleteRecursively(Path.of("build/test-storage-limit"));
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    @Test
    void 超限上传拒413() throws Exception {
        byte[] oversize = "x".repeat(2048).getBytes(StandardCharsets.UTF_8);
        mockMvc
                .perform(multipart("/storage/files")
                        .file(new MockMultipartFile("file", TEST_PREFIX + "big.txt", "text/plain", oversize))
                        .header("Authorization", "Bearer " + rootToken()))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value(413))
                .andExpect(jsonPath("$.message").value("上传文件超出大小限制"));
        // 超限不落行
        assertThat(storageFileMapper.selectCount(
                        Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, TEST_PREFIX)))
                .isZero();
    }

    @Test
    void 限内上传放行() throws Exception {
        byte[] content = "small".getBytes(StandardCharsets.UTF_8);
        mockMvc
                .perform(multipart("/storage/files")
                        .file(new MockMultipartFile("file", TEST_PREFIX + "small.txt", "text/plain", content))
                        .header("Authorization", "Bearer " + rootToken()))
                .andExpect(status().isOk());
        assertThat(storageFileMapper.selectCount(
                        Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, TEST_PREFIX)))
                .isEqualTo(1);
    }
}
