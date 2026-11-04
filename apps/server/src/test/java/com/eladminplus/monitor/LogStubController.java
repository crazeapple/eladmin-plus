package com.eladminplus.monitor;

import com.eladminplus.common.R;
import com.eladminplus.monitor.log.Log;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 测试专用控制器：@Log 落库链路载体——标注端点（JSON 体由测试自定）与未标注端点对照；
 * 异常日志钩子载体——唯一标记冒烟异常、超长消息异常与深栈异常。
 */
@RestController
public class LogStubController {

    @PostMapping("/test/log/write")
    @Log("沙箱写入")
    public R<Void> write(@RequestBody JsonNode body) {
        return R.ok();
    }

    @PostMapping("/test/log/silent")
    public R<Void> silent(@RequestBody JsonNode body) {
        return R.ok();
    }

    /** 带唯一标记的 5xx 冒烟异常（异步落表断言按标记定位行） */
    @GetMapping("/test/log/boom")
    public String boom(@RequestParam String marker) {
        throw new IllegalStateException("冒烟-" + marker + "-异常");
    }

    /** 超长消息异常（message 列截断入库语义验证载体） */
    @GetMapping("/test/log/long-message")
    public String longMessage(@RequestParam String marker) {
        throw new IllegalStateException("长消息-" + marker + "-" + "长".repeat(3000));
    }

    /** 深栈异常（stack_trace 列截断入库语义验证载体） */
    @GetMapping("/test/log/deep-stack")
    public String deepStack(@RequestParam String marker) {
        return recurse("深栈-" + marker, 1_500);
    }

    private String recurse(String marker, int depth) {
        if (depth == 0) {
            throw new IllegalStateException(marker + "-触底抛出");
        }
        return recurse(marker, depth - 1);
    }
}
