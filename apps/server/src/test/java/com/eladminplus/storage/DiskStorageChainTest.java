package com.eladminplus.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 本地磁盘引擎全链：上传落行（键形态、大小与 MIME）→ 下载流式回原名 → 删除先对象后行；超权限 403、不存在 404。 */
@SpringBootTest(properties = {"storage.engine=local", "storage.local.base-path=build/test-storage"})
@AutoConfigureMockMvc
class DiskStorageChainTest {

    /** 测试行文件名前缀，清理定位用 */
    private static final String TEST_PREFIX = "chain-test-";

    private static final Pattern KEY_PATTERN =
            Pattern.compile("^\\d{6}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(\\w+)$");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
        // 测试自管磁盘目录：整目录重建清残留
        deleteRecursively(Path.of("build/test-storage"));
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
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

    private String permissionlessToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of(), List.of()).accessToken();
    }

    private Long upload(String token, String name, String mime, byte[] content) throws Exception {
        MvcResult result = mockMvc
                .perform(multipart("/storage/files")
                        .file(new MockMultipartFile("file", name, mime, content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data");
        return data.get("id").asLong();
    }

    @Test
    void 上传下载删除本地全链() throws Exception {
        String token = rootToken();
        byte[] content = "eladmin-plus storage chain".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Long id = upload(token, TEST_PREFIX + "logo.png", "image/png", content);

        // 行落库：键形态 {yyyyMM}/{uuid}.{ext}、大小与 MIME 与引擎在册
        StorageFile row = storageFileMapper.selectById(id);
        assertThat(row).isNotNull();
        assertThat(row.getName()).isEqualTo(TEST_PREFIX + "logo.png");
        assertThat(KEY_PATTERN.matcher(row.getStorageKey()).matches()).isTrue();
        assertThat(row.getSize()).isEqualTo(content.length);
        assertThat(row.getMime()).isEqualTo("image/png");
        assertThat(row.getEngine()).isEqualTo("local");
        assertThat(row.getUploader()).isEqualTo("admin");
        // 对象在磁盘：键映射 build/test-storage 下
        assertThat(Files.exists(Path.of("build/test-storage", row.getStorageKey()))).isTrue();

        // 下载：内容一致、原名在 Content-Disposition
        MvcResult download = mockMvc
                .perform(get("/storage/files/{id}/download", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);
        assertThat(download.getResponse().getHeader("Content-Disposition")).contains(TEST_PREFIX + "logo.png");

        // 删除：行与对象同消；再删再下均 404
        mockMvc
                .perform(delete("/storage/files/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(storageFileMapper.selectById(id)).isNull();
        assertThat(Files.exists(Path.of("build/test-storage", row.getStorageKey()))).isFalse();
        mockMvc
                .perform(delete("/storage/files/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc
                .perform(get("/storage/files/{id}/download", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 中文名下载原名不乱码() throws Exception {
        String token = rootToken();
        Long id = upload(token, TEST_PREFIX + "合同.txt", "text/plain", "合同内容".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        MvcResult download = mockMvc
                .perform(get("/storage/files/{id}/download", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String disposition = download.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).contains("attachment");
        // RFC 5987 形态：filename*=UTF-8'' 百分号编码
        assertThat(disposition).contains("filename*=UTF-8''");
    }

    @Test
    void 无权限点403授权后可过() throws Exception {
        Long id = upload(rootToken(), TEST_PREFIX + "perm.png", "image/png", new byte[] {1});

        // 无 upload 权限点：上传 403
        String plainToken = permissionlessToken();
        mockMvc
                .perform(multipart("/storage/files")
                        .file(new MockMultipartFile("file", TEST_PREFIX + "x.png", "image/png", new byte[] {1}))
                        .header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
        // 无 del 权限点：删除 403
        mockMvc
                .perform(delete("/storage/files/{id}", id).header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());

        // 显式授点（非 ROOT）：上传与删除放行
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String uploaderToken = tokenService
                .issue(admin, List.of(), List.of("storage:upload"))
                .accessToken();
        Long grantedId = upload(uploaderToken, TEST_PREFIX + "granted.png", "image/png", new byte[] {1});
        assertThat(grantedId).isNotNull();
        String deleterToken = tokenService.issue(admin, List.of(), List.of("storage:del")).accessToken();
        mockMvc
                .perform(delete("/storage/files/{id}", grantedId).header("Authorization", "Bearer " + deleterToken))
                .andExpect(status().isOk());
    }

    @Test
    void 不存在id下载与删除404() throws Exception {
        String token = rootToken();
        mockMvc
                .perform(get("/storage/files/{id}/download", 987654321L).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc
                .perform(delete("/storage/files/{id}", 987654321L).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
