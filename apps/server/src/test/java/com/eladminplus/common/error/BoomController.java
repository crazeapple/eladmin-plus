package com.eladminplus.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用控制器：触发受控异常与未知异常，供 MockMvc 断言错误结构。
 */
@RestController
public class BoomController {

    @GetMapping("/test/boom")
    public String boom() {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "error.bad");
    }

    @GetMapping("/test/unknown")
    public String unknown() {
        throw new IllegalStateException("内部细节不应外泄");
    }

    @GetMapping("/test/ok")
    public String ok() {
        return "ok";
    }
}
