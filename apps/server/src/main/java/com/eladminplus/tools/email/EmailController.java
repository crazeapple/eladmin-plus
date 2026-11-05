package com.eladminplus.tools.email;

import com.eladminplus.common.R;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 邮件工具端点：发送（权限点 email:send，ROLE_ROOT 直通）；SMTP 未配置时报「邮件服务未启用」。 */
@RestController
@RequestMapping("/tools/email")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('email:send') || hasAuthority('ROLE_ROOT')")
    public R<Void> send(@RequestBody EmailSendRequest request) {
        emailService.send(request);
        return R.ok();
    }
}
