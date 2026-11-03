package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.common.translation.Translation;
import com.eladminplus.common.translation.TranslationMapper;
import com.eladminplus.system.domain.Job;
import com.eladminplus.system.domain.UserJob;
import com.eladminplus.system.mapper.JobMapper;
import com.eladminplus.system.mapper.UserJobMapper;
import com.eladminplus.system.dto.JobSaveRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 岗位域：分页筛选、多语言、CRUD、关联删除校验。 */
@SpringBootTest
@AutoConfigureMockMvc
class JobTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JobMapper jobMapper;
    @Autowired
    private UserJobMapper userJobMapper;
    @Autowired
    private TranslationMapper translationMapper;

    private String accessToken;
    private Long adminId;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        adminId = admin.getId();
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        List<Long> testIds = jobMapper.selectList(Wrappers.<Job>lambdaQuery().notIn(Job::getName, "开发工程师"))
                .stream()
                .map(Job::getId)
                .toList();
        for (Long id : testIds) {
            translationMapper.delete(Wrappers.<Translation>lambdaQuery()
                    .eq(Translation::getEntity, "job")
                    .eq(Translation::getEntityId, id));
            userJobMapper.delete(Wrappers.<UserJob>lambdaQuery().eq(UserJob::getJobId, id));
            jobMapper.deleteById(id);
        }
        userJobMapper.delete(Wrappers.<UserJob>lambdaQuery().eq(UserJob::getUserId, adminId));
    }

    private Long createJob(String name, String nameEn) throws Exception {
        MvcResult result = mockMvc.perform(post("/jobs")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobSaveRequest(null, name, nameEn, 5, 1))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    @Test
    void 分页与名称模糊筛选() throws Exception {
        createJob("测试岗位", "Test Job");

        MvcResult all = mockMvc.perform(get("/jobs")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(all.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertThat(data.get("page").asInt()).isZero(); // page 0 起
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(2);

        MvcResult filtered = mockMvc.perform(get("/jobs")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("name", "测试"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode filteredData =
                objectMapper.readTree(filtered.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .get("data");
        assertThat(filteredData.get("total").asLong()).isEqualTo(1);
        assertThat(filteredData.get("content").get(0).get("name").asText()).isEqualTo("测试岗位");
    }

    @Test
    void 多语言覆盖按AcceptLanguage生效() throws Exception {
        createJob("测试岗位", "Test Job");
        // 模糊检索只查默认语言列；en 请求展示覆盖名
        MvcResult en = mockMvc.perform(get("/jobs")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(en.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        JsonNode hit = null;
        for (JsonNode row : data.get("content")) {
            if (row.get("id").asLong() == 0) {
                continue;
            }
            if ("Test Job".equals(row.get("name").asText())) {
                hit = row;
            }
        }
        assertThat(hit).isNotNull();
    }

    @Test
    void 关联用户的岗位删除被拒() throws Exception {
        Long jobId = createJob("测试岗位", "Test Job");
        userJobMapper.insert(newFullUserJob(adminId, jobId));

        mockMvc.perform(delete("/jobs/{id}", jobId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("岗位下存在用户，不能删除"));

        userJobMapper.delete(Wrappers.<UserJob>lambdaQuery().eq(UserJob::getJobId, jobId));
        mockMvc.perform(delete("/jobs/{id}", jobId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(jobMapper.selectById(jobId)).isNull();
        // 覆盖行已级联清理
        Long overrideCount = translationMapper.selectCount(Wrappers.<Translation>lambdaQuery()
                .eq(Translation::getEntity, "job")
                .eq(Translation::getEntityId, jobId));
        assertThat(overrideCount).isZero();
    }

    private UserJob newFullUserJob(Long userId, Long jobId) {
        UserJob userJob = new UserJob();
        userJob.setUserId(userId);
        userJob.setJobId(jobId);
        return userJob;
    }
}
