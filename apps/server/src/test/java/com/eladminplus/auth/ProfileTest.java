package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 个人中心：资料改（登录即可、邮箱格式与唯一性）、头像上传写 id 且 me 响应含 avatar、头像可清空。 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileTest {

    /** 测试文件与临时用户前缀，清理定位用 */
    private static final String TEST_PREFIX = "profile-test-";

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

    private User adminSnapshot;

    @BeforeEach
    void snapshotAdmin() {
        adminSnapshot = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
    }

    @AfterEach
    void restoreAdminAndClean() {
        // admin 行资料还原（资料改与头像测试落的变化不外溢到他测）
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, adminSnapshot.getId())
                .set(User::getNickName, adminSnapshot.getNickName())
                .set(User::getEmail, adminSnapshot.getEmail())
                .set(User::getPhone, adminSnapshot.getPhone())
                .set(User::getGender, adminSnapshot.getGender())
                .set(User::getAvatar, adminSnapshot.getAvatar()));
        userMapper.delete(Wrappers.<User>lambdaQuery().likeRight(User::getUsername, TEST_PREFIX));
        storageFileMapper.delete(Wrappers.<StorageFile>lambdaQuery().likeRight(StorageFile::getName, TEST_PREFIX));
    }

    /** 无任何角色与权限点的会话：个人中心端点应放行（登录即可，不设权限点） */
    private String plainToken() {
        return tokenService.issue(adminSnapshot, List.of(), List.of()).accessToken();
    }

    private String rootToken() {
        return tokenService.issue(adminSnapshot, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    private JsonNode meUser(String token) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("user");
    }

    @Test
    void 资料改生效且登录即可() throws Exception {
        String token = plainToken();
        mockMvc
                .perform(put("/auth/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"管理员甲\",\"email\":\"" + TEST_PREFIX
                                + "admin@example.com\",\"phone\":\"13800000000\",\"gender\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        User user = userMapper.selectById(adminSnapshot.getId());
        assertThat(user.getNickName()).isEqualTo("管理员甲");
        assertThat(user.getEmail()).isEqualTo(TEST_PREFIX + "admin@example.com");
        assertThat(user.getPhone()).isEqualTo("13800000000");
        assertThat(user.getGender()).isEqualTo(1);
        // me 响应同步且 avatar 字段在册（未传头像为 null）
        JsonNode meUser = meUser(token);
        assertThat(meUser.get("nickName").asText()).isEqualTo("管理员甲");
        assertThat(meUser.get("avatarId")).isNotNull();
        assertThat(meUser.get("avatarId").isNull()).isTrue();
    }

    @Test
    void 邮箱格式非法422() throws Exception {
        mockMvc
                .perform(put("/auth/me/profile")
                        .header("Authorization", "Bearer " + plainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"系统管理员\",\"email\":\"not-an-email\",\"phone\":null,\"gender\":0}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value(422))
                .andExpect(jsonPath("$.message").value("邮箱格式不合法"));
    }

    @Test
    void 邮箱重复400() throws Exception {
        User other = new User();
        other.setUsername(TEST_PREFIX + "other");
        other.setPassword("stub");
        other.setEmail(TEST_PREFIX + "taken@example.com");
        userMapper.insert(other);

        mockMvc
                .perform(put("/auth/me/profile")
                        .header("Authorization", "Bearer " + plainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"系统管理员\",\"email\":\"" + TEST_PREFIX
                                + "taken@example.com\",\"phone\":null,\"gender\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱已存在"));
    }

    @Test
    void 头像上传写id且me响应含avatar可下载() throws Exception {
        String token = plainToken();
        byte[] content = "avatar png".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MvcResult result = mockMvc
                .perform(multipart("/auth/me/avatar")
                        .file(new MockMultipartFile("file", TEST_PREFIX + "avatar.png", "image/png", content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Long fileId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();

        // avatar 写文件 id 且文件行走存储域（上传人记自己）
        assertThat(userMapper.selectById(adminSnapshot.getId()).getAvatar()).isEqualTo(fileId);
        StorageFile row = storageFileMapper.selectById(fileId);
        assertThat(row.getUploader()).isEqualTo("admin");
        assertThat(row.getName()).isEqualTo(TEST_PREFIX + "avatar.png");

        // me 响应含 avatar 文件 id，头像展示（下载端点）登录即可
        assertThat(meUser(token).get("avatarId").asLong()).isEqualTo(fileId);
        MvcResult download = mockMvc
                .perform(get("/storage/files/{id}/download", fileId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);

        // 测试文件对象与行同清（复走存储删除端点）
        mockMvc
                .perform(delete("/storage/files/{id}", fileId).header("Authorization", "Bearer " + rootToken()))
                .andExpect(status().isOk());
    }

    @Test
    void 头像可清空恢复无头像态() throws Exception {
        String token = plainToken();
        MvcResult result = mockMvc
                .perform(multipart("/auth/me/avatar")
                        .file(new MockMultipartFile("file", TEST_PREFIX + "to-clear.png", "image/png", new byte[] {1}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Long fileId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();

        mockMvc
                .perform(delete("/auth/me/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(userMapper.selectById(adminSnapshot.getId()).getAvatar()).isNull();
        assertThat(meUser(token).get("avatarId").isNull()).isTrue();

        // 清空仅解除引用，文件行不删（由存储域自行管理）；测试内顺手清理
        assertThat(storageFileMapper.selectById(fileId)).isNotNull();
        mockMvc
                .perform(delete("/storage/files/{id}", fileId).header("Authorization", "Bearer " + rootToken()))
                .andExpect(status().isOk());
    }
}
