package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eladminplus.auth.captcha.BlockPuzzleService;
import com.eladminplus.auth.captcha.CaptchaController;
import com.eladminplus.common.error.BusinessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 滑块验证码：出题、校验签票、票据一次性、题目即焚。 */
@SpringBootTest
@AutoConfigureMockMvc
class CaptchaTest {

    @Autowired
    private BlockPuzzleService blockPuzzleService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private int answerOf(String token) {
        return Integer.parseInt(redis.opsForValue().get(BlockPuzzleService.PUZZLE_PREFIX + token));
    }

    @Test
    void 出题端点返回图与token且答案只进Redis() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.back").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.data.jigsaw").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String token = data.get("token").asText();
        // 答案在 Redis，且数值在画布横向范围内
        assertThat(answerOf(token)).isBetween(0, 310);
        // 拼块纵向位置已下发
        assertThat(data.get("jigsawY").asInt()).isBetween(0, 155);
    }

    @Test
    void 校验命中签发票据且题目即焚() throws Exception {
        var puzzle = blockPuzzleService.generate();
        int answer = answerOf(puzzle.token());

        MvcResult result = mockMvc.perform(postCheck(puzzle.token(), answer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String ticket = data.get("ticket").asText();

        // 票据在 Redis 且题目已被焚（防重放观察）
        assertThat(redis.hasKey(BlockPuzzleService.TICKET_PREFIX + ticket)).isTrue();
        assertThat(redis.opsForValue().get(BlockPuzzleService.PUZZLE_PREFIX + puzzle.token())).isNull();
    }

    @Test
    void 校验偏离容差拒签且题目同样焚毁() throws Exception {
        var puzzle = blockPuzzleService.generate();
        int wrong = answerOf(puzzle.token()) + 100;

        mockMvc.perform(postCheck(puzzle.token(), wrong)).andExpect(status().isBadRequest());
        assertThat(redis.opsForValue().get(BlockPuzzleService.PUZZLE_PREFIX + puzzle.token())).isNull();
    }

    @Test
    void 票据一次性消费() {
        var puzzle = blockPuzzleService.generate();
        String ticket = blockPuzzleService.check(puzzle.token(), answerOf(puzzle.token()));

        assertThat(blockPuzzleService.consumeTicket(ticket)).isTrue();
        // 第二次消费即重放拒绝
        assertThat(blockPuzzleService.consumeTicket(ticket)).isFalse();
    }

    @Test
    void 失效题目校验拒绝() {
        String gone = blockPuzzleService.generate().token();
        redis.delete(BlockPuzzleService.PUZZLE_PREFIX + gone);
        assertThatThrownBy(() -> blockPuzzleService.check(gone, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.auth.captcha-invalid");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postCheck(
            String token, int moveDistance) {
        try {
            String body = objectMapper.writeValueAsString(new CaptchaController.CaptchaCheckRequest(token, moveDistance));
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/auth/captcha/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException(e);
        }
    }
}
