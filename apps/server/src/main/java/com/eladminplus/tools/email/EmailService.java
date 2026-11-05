package com.eladminplus.tools.email;

import com.eladminplus.common.error.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 邮件工具：真实发送（spring mail）；SMTP 未配置快速失败报「邮件服务未启用」，不重试不落库。 */
@Service
public class EmailService {

    /** 收件人形态：与个人中心邮箱校验同形 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final EmailProperties properties;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider, EmailProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    public void send(EmailSendRequest request) {
        // 输入校验先于启用判定：收件人格式非法 422
        if (!StringUtils.hasText(request.to()) || !EMAIL_PATTERN.matcher(request.to()).matches()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "error.tools.email-recipient-invalid");
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.tools.email-not-enabled");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(request.to());
            helper.setSubject(StringUtils.hasText(request.subject()) ? request.subject() : "");
            helper.setText(StringUtils.hasText(request.content()) ? request.content() : "",
                    Boolean.TRUE.equals(request.html()));
            sender.send(message);
        } catch (MessagingException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "error.tools.email-send-failed");
        }
    }
}
