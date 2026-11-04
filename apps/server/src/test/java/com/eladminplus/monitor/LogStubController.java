package com.eladminplus.monitor;

import com.eladminplus.common.R;
import com.eladminplus.monitor.log.Log;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 测试专用控制器：@Log 落库链路载体——标注端点（JSON 体由测试自定）与未标注端点对照。
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
}
