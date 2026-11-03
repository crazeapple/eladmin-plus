package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.domain.Dict;
import com.eladminplus.system.domain.DictDetail;
import com.eladminplus.system.dto.DictDetailSaveRequest;
import com.eladminplus.system.dto.DictSaveRequest;
import com.eladminplus.system.mapper.DictDetailMapper;
import com.eladminplus.system.mapper.DictMapper;
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

/** 字典管理 CRUD：两级全链、唯一约束 409、label JSON 校验 422、字典删除级联明细、label 原样往返。 */
@SpringBootTest
@AutoConfigureMockMvc
class DictCrudTest {

    /** 测试字典名前缀，清理定位用 */
    private static final String TEST_NAME_PREFIX = "testdict.";

    /** 双语言 label 样本：键序 zh-CN 在前、en 在后，原样往返断言用 */
    private static final String LABEL_ZH_FIRST = "{\"zh-CN\":\"启用\",\"en\":\"Enabled\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DictMapper dictMapper;
    @Autowired
    private DictDetailMapper dictDetailMapper;

    private String accessToken;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        List<Long> testDictIds = dictMapper
                .selectList(Wrappers.<Dict>lambdaQuery().likeRight(Dict::getName, TEST_NAME_PREFIX))
                .stream()
                .map(Dict::getId)
                .toList();
        for (Long dictId : testDictIds) {
            dictDetailMapper.delete(Wrappers.<DictDetail>lambdaQuery().eq(DictDetail::getDictId, dictId));
            dictMapper.deleteById(dictId);
        }
    }

    private Long createDict(String name, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/dicts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DictSaveRequest(null, name, description))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private Long createDetail(Long dictId, String label, String value, int sort) throws Exception {
        MvcResult result = mockMvc.perform(post("/dicts/{dictId}/details", dictId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DictDetailSaveRequest(null, label, value, sort))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private JsonNode listDetails(Long dictId) throws Exception {
        String body = mockMvc
                .perform(get("/dicts/{dictId}/details", dictId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data");
    }

    @Test
    void 建字典加明细回读全字段一致且label原样往返() throws Exception {
        Long dictId = createDict(TEST_NAME_PREFIX + "toggle", "测试开关字典");
        createDetail(dictId, LABEL_ZH_FIRST, "1", 2);
        createDetail(dictId, "{\"en\":\"Disabled\",\"zh-CN\":\"停用\"}", "0", 1);

        JsonNode details = listDetails(dictId);
        assertThat(details.size()).isEqualTo(2);
        // sort 小者在前；label 为 JSON 文本原样下发，键序保留、不做解析重组
        JsonNode first = details.get(0);
        assertThat(first.get("value").asText()).isEqualTo("0");
        assertThat(first.get("sort").asInt()).isEqualTo(1);
        assertThat(first.get("label").asText()).isEqualTo("{\"en\":\"Disabled\",\"zh-CN\":\"停用\"}");
        JsonNode second = details.get(1);
        assertThat(second.get("value").asText()).isEqualTo("1");
        assertThat(second.get("label").asText()).isEqualTo(LABEL_ZH_FIRST);
    }

    @Test
    void 重名字典409且同字典重value409() throws Exception {
        createDict(TEST_NAME_PREFIX + "dup", null);
        mockMvc.perform(post("/dicts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DictSaveRequest(null, TEST_NAME_PREFIX + "dup", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("字典名已存在"));

        Long dictId = createDict(TEST_NAME_PREFIX + "values", null);
        createDetail(dictId, LABEL_ZH_FIRST, "same", 1);
        mockMvc.perform(post("/dicts/{dictId}/details", dictId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DictDetailSaveRequest(null, LABEL_ZH_FIRST, "same", 2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("同字典下字典值已存在"));
    }

    @Test
    void 非法label直拒422() throws Exception {
        Long dictId = createDict(TEST_NAME_PREFIX + "label", null);
        // 非法 JSON 文本
        mockMvc.perform(post("/dicts/{dictId}/details", dictId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DictDetailSaveRequest(null, "not-a-json", "1", 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("标签必须是合法的 JSON 对象文本"));
        // 合法 JSON 但非对象形态（标量与数组同样拒绝，护住 locale 映射消费契约）
        mockMvc.perform(post("/dicts/{dictId}/details", dictId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DictDetailSaveRequest(null, "\"plain text\"", "2", 1))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/dicts/{dictId}/details", dictId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DictDetailSaveRequest(null, "[\"array\"]", "3", 1))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 删字典级联明细零残留且跨字典同value合法() throws Exception {
        Long dictA = createDict(TEST_NAME_PREFIX + "del-a", null);
        Long dictB = createDict(TEST_NAME_PREFIX + "del-b", null);
        createDetail(dictA, LABEL_ZH_FIRST, "male", 1);
        createDetail(dictB, LABEL_ZH_FIRST, "male", 1); // 跨字典同 value 合法

        mockMvc.perform(delete("/dicts/{id}", dictA).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(dictMapper.selectById(dictA)).isNull();
        Long leftover =
                dictDetailMapper.selectCount(Wrappers.<DictDetail>lambdaQuery().eq(DictDetail::getDictId, dictA));
        assertThat(leftover).isZero();
        assertThat(dictMapper.selectById(dictB)).isNotNull();
    }

    @Test
    void 明细编辑与单删生效() throws Exception {
        Long dictId = createDict(TEST_NAME_PREFIX + "edit", null);
        Long detailId = createDetail(dictId, LABEL_ZH_FIRST, "1", 5);

        mockMvc.perform(put("/dicts/{dictId}/details/{detailId}", dictId, detailId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DictDetailSaveRequest(null, "{\"zh-CN\":\"开启\",\"en\":\"On\"}", "1", 3))))
                .andExpect(status().isOk());
        JsonNode details = listDetails(dictId);
        assertThat(details.size()).isEqualTo(1);
        assertThat(details.get(0).get("label").asText()).isEqualTo("{\"zh-CN\":\"开启\",\"en\":\"On\"}");
        assertThat(details.get(0).get("sort").asInt()).isEqualTo(3);

        mockMvc.perform(delete("/dicts/{dictId}/details/{detailId}", dictId, detailId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(listDetails(dictId).size()).isZero();
    }
}
