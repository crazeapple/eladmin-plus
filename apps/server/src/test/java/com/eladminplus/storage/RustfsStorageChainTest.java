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
import com.eladminplus.storage.StorageProperties;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * RustFS 引擎全链（默认配置激活 rustfs，真实读写 compose 实例）：上传下载删除落对象、引擎字段落 rustfs、
 * 桶不存在自动创建、混引擎历史按行内引擎字段路由下载。
 * 桶名取独立测试桶（eladmin-plus-it），前后清空建桶以覆盖「桶不存在自动创建」路径。
 *
 * <p>依赖外部基础设施（RustFS 对象存储）：类标 {@code @Tag("infra")}，快层 CI 以 -PexcludeInfraTags
 * 排除；上传下载链路由慢层 E2E tools-storage.spec.ts 覆盖。
 */
@Tag("infra")
@SpringBootTest(properties = {"storage.rustfs.bucket=eladmin-plus-it", "storage.local.base-path=build/test-storage-rustfs"})
@AutoConfigureMockMvc
class RustfsStorageChainTest {

    /** 测试行文件名前缀，清理定位用 */
    private static final String TEST_PREFIX = "rustfs-test-";

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
    @Autowired
    private StorageProperties properties;

    private MinioClient probe;

    @BeforeEach
    void setUp() {
        probe = MinioClient.builder()
                .endpoint(properties.rustfs().endpoint())
                .credentials(properties.rustfs().accessKey(), properties.rustfs().secretKey())
                .build();
        cleanTestRows();
        cleanBucket();
        deleteRecursively(Path.of("build/test-storage-rustfs"));
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
        cleanBucket();
        deleteRecursively(Path.of("build/test-storage-rustfs"));
    }

    private void cleanTestRows() {
        storageFileMapper.delete(Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, TEST_PREFIX));
        storageFileMapper.delete(
                Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, "route-test-"));
    }

    /** 清空并删测试桶：保证「桶不存在自动创建」路径每次真实覆盖（尽力清理，残留不影响幂等） */
    private void cleanBucket() {
        String bucket = properties.rustfs().bucket();
        try {
            if (!probe.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                return;
            }
            for (Result<Item> item :
                    probe.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())) {
                probe.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(item.get().objectName()).build());
            }
            probe.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            throw new IllegalStateException("测试桶清理失败", e);
        }
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

    private Long upload(String token, String name, byte[] content) throws Exception {
        MvcResult result = mockMvc
                .perform(multipart("/storage/files")
                        .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private boolean bucketExists() throws Exception {
        return probe.bucketExists(BucketExistsArgs.builder().bucket(properties.rustfs().bucket()).build());
    }

    private boolean objectExists(String key) {
        try {
            probe.statObject(StatObjectArgs.builder()
                    .bucket(properties.rustfs().bucket())
                    .object(key)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 上传下载删除真实走RustFS且引擎字段落库() throws Exception {
        String token = rootToken();
        assertThat(bucketExists()).as("前置：测试桶已清空").isFalse();
        byte[] content = "rustfs chain content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Long id = upload(token, TEST_PREFIX + "doc.bin", content);

        // 引擎字段落 rustfs，对象真实在桶内
        StorageFile row = storageFileMapper.selectById(id);
        assertThat(row.getEngine()).isEqualTo("rustfs");
        assertThat(objectExists(row.getStorageKey())).isTrue();

        // 下载内容一致
        MvcResult download = mockMvc
                .perform(get("/storage/files/{id}/download", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);

        // 删除：行与对象同消
        mockMvc
                .perform(delete("/storage/files/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(storageFileMapper.selectById(id)).isNull();
        assertThat(objectExists(row.getStorageKey())).isFalse();
    }

    @Test
    void 桶不存在自动创建() throws Exception {
        String token = rootToken();
        assertThat(bucketExists()).as("前置：测试桶已清空").isFalse();
        upload(token, TEST_PREFIX + "seed.bin", new byte[] {1});
        assertThat(bucketExists()).as("上传后桶自动创建").isTrue();
    }

    @Test
    void 混引擎历史按行内引擎字段路由下载() throws Exception {
        String token = rootToken();
        // 本地引擎落的历史行：对象直接放本地引擎目录，行记 engine=local（当前配置引擎是 rustfs）
        byte[] content = "local legacy content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String legacyKey = "209901/" + UUID.randomUUID() + ".txt";
        Path legacyFile = Path.of("build/test-storage-rustfs", legacyKey);
        Files.createDirectories(legacyFile.getParent());
        Files.write(legacyFile, content);

        StorageFile row = new StorageFile();
        row.setName("route-test-legacy.txt");
        row.setStorageKey(legacyKey);
        row.setExt("txt");
        row.setSize((long) content.length);
        row.setMime("text/plain");
        row.setEngine("local");
        row.setUploader("admin");
        storageFileMapper.insert(row);

        MvcResult download = mockMvc
                .perform(get("/storage/files/{id}/download", row.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);

        // rustfs 行照常路由（同请求序列内两引擎并存可读）
        Long rustfsId = upload(token, TEST_PREFIX + "new.bin", new byte[] {2});
        mockMvc
                .perform(get("/storage/files/{id}/download", rustfsId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void 路由到不存在对象按404() throws Exception {
        String token = rootToken();
        // 先真实上传一单（建桶），再查行内对象已不在的行：按 404 语义（区别于桶缺失的不可用）
        upload(token, TEST_PREFIX + "seed-for-missing.bin", new byte[] {1});
        StorageFile row = new StorageFile();
        row.setName("route-test-missing.txt");
        row.setStorageKey("209901/" + UUID.randomUUID() + ".txt");
        row.setExt("txt");
        row.setSize(1L);
        row.setMime("text/plain");
        row.setEngine("rustfs");
        row.setUploader("admin");
        storageFileMapper.insert(row);
        mockMvc
                .perform(get("/storage/files/{id}/download", row.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
