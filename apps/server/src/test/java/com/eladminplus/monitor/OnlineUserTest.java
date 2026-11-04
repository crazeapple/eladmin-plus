package com.eladminplus.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.LoginTestSupport;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 在线用户：登录会话补落昵称 / IP / UA、列表五字段枚举与过期会话剔除、强退即时失效与索引清空、权限拦截。 */
@SpringBootTest
@AutoConfigureMockMvc
class OnlineUserTest {

    private static final String FORWARDED_FOR = "203.0.113.5";

    private static final String USER_AGENT = "TestAgent/1.0 (monitor-proposal)";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redis;

    private User admin;

    @BeforeEach
    void cleanSessions() {
        admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        // 会话状态归零：本类各用例从无会话基线出发
        tokenService.revokeAllForUser(admin.getId());
    }

    private JsonNode onlineRows(String token) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/online").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data");
    }

    private JsonNode findRow(JsonNode array, Long userId, String ip) {
        for (JsonNode row : array) {
            if (row.get("userId").asLong() == userId && row.get("ip").asText().equals(ip)) {
                return row;
            }
        }
        return null;
    }

    @Test
    void 登录后会话补落昵称ip与UA() throws Exception {
        MvcResult login = LoginTestSupport.fullLogin(mockMvc, redis, admin.getUsername(), "123456",
                FORWARDED_FOR, USER_AGENT);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String accessToken = LoginTestSupport.extractJsonField(
                login.getResponse().getContentAsString(StandardCharsets.UTF_8), "accessToken");

        var session = redis.opsForHash().entries(TokenService.TOKEN_PREFIX + accessToken);
        assertThat(session.get("nickname")).isEqualTo(admin.getNickName());
        assertThat(session.get("ip")).isEqualTo(FORWARDED_FOR);
        assertThat(session.get("userAgent")).isEqualTo(USER_AGENT);
    }

    @Test
    void 列表端点枚举五字段且过期会话不在列() throws Exception {
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of(), FORWARDED_FOR, USER_AGENT);
        String listerToken = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();

        JsonNode row = findRow(onlineRows(listerToken), admin.getId(), FORWARDED_FOR);
        assertThat(row).isNotNull();
        assertThat(row.get("nickName").asText()).isEqualTo(admin.getNickName());
        assertThat(row.get("username").asText()).isEqualTo(admin.getUsername());
        assertThat(row.get("browser").asText()).isEqualTo(USER_AGENT);
        assertThat(Instant.parse(row.get("loginAt").asText())).isNotNull();

        // 过期会话：键已删即不在列（TTL 到期同形；枚举行按会话并列，lister 行 ip 为空串不混入）
        redis.delete(TokenService.TOKEN_PREFIX + pair.accessToken());
        assertThat(findRow(onlineRows(listerToken), admin.getId(), FORWARDED_FOR)).isNull();
    }

    @Test
    void 强退后目标用户再访问401且索引清空() throws Exception {
        MvcResult login = LoginTestSupport.fullLogin(mockMvc, redis, admin.getUsername(), "123456", null, null);
        String targetToken = LoginTestSupport.extractJsonField(
                login.getResponse().getContentAsString(StandardCharsets.UTF_8), "accessToken");
        TokenPair kickerPair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        assertThat(redis.hasKey(TokenService.SESSION_INDEX_PREFIX + admin.getId())).isTrue();

        mockMvc.perform(delete("/online/{userId}", admin.getId())
                        .header("Authorization", "Bearer " + kickerPair.accessToken()))
                .andExpect(status().isOk());

        // 目标会话与发起会话（同用户）一并即时失效，再访问 401
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + kickerPair.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThat(redis.hasKey(TokenService.SESSION_INDEX_PREFIX + admin.getId())).isFalse();
    }

    @Test
    void 无list与del权限403() throws Exception {
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc.perform(get("/online").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/online/{userId}", admin.getId())
                        .header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }
}
