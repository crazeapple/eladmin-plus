package com.eladminplus.auth.captcha;

import com.eladminplus.common.R;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 滑块验证码端点：GET 出题、POST 校验签发一次性票据（登录消费票据）。 */
@RestController
@RequestMapping("/auth/captcha")
public class CaptchaController {

    private final BlockPuzzleService blockPuzzleService;

    public CaptchaController(BlockPuzzleService blockPuzzleService) {
        this.blockPuzzleService = blockPuzzleService;
    }

    @GetMapping
    public R<BlockPuzzle> captcha() {
        return R.ok(blockPuzzleService.generate());
    }

    @PostMapping("/check")
    public R<Map<String, String>> check(@RequestBody CaptchaCheckRequest request) {
        String ticket = blockPuzzleService.check(request.token(), request.moveDistance());
        return R.ok(Map.of("ticket", ticket));
    }

    public record CaptchaCheckRequest(String token, int moveDistance) {}
}
