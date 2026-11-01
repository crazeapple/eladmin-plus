package com.eladminplus.common.error;

import com.eladminplus.common.R;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用控制器：成功、受控异常、未知异常三态，供 MockMvc 断言同形响应结构。
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
    public R<String> ok() {
        return R.ok("ok");
    }
}
