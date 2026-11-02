package com.eladminplus.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 受控异常回真实状态码且失败响应与成功同形() throws Exception {
        getJson("/test/boom")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数或操作不被接受"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 未知异常回500通用消息且不泄内部细节() throws Exception {
        getJson("/test/unknown")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务内部错误，请稍后再试"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("内部细节"))));
    }

    @Test
    void 成功响应为同形结构包裹负载数据() throws Exception {
        getJson("/test/ok")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value(""))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    private ResultActions getJson(String path) throws Exception {
        // 显式 zh：词条本地化后消息随请求语言，断言锚定中文文案
        return mockMvc.perform(get(path).header("Accept-Language", "zh-CN"));
    }
}
