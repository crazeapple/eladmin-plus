package com.eladminplus.auth.captcha;

import com.eladminplus.common.error.BusinessException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 滑块验证码（AJ-Captcha blockPuzzle 同语义实现）：
 * 题目（缺口位置）只进 Redis 不下发；校验通过即焚题并签发一次性票据，
 * 登录端点消费票据——二次缓存与票据一次性语义均在 Redis。
 */
@Service
public class BlockPuzzleService {

    public static final String PUZZLE_PREFIX = "auth:captcha:puzzle:";
    public static final String TICKET_PREFIX = "auth:captcha:ticket:";

    private static final int SLIDER_SIZE = 44;
    private static final int SLIDER_RADIUS = 10;

    /** token 仅接受 UUID 形态（与 keyId 同策略，防 Redis key 探测） */
    private static final java.util.regex.Pattern TOKEN_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final StringRedisTemplate redis;
    private final CaptchaProperties props;

    public BlockPuzzleService(StringRedisTemplate redis, CaptchaProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** 生成题目：程序化绘制背景与拼块，缺口横向位置（答案）只进 Redis */
    public BlockPuzzle generate() {
        int width = props.imageWidth();
        int height = props.imageHeight();
        int sliderX = width / 4 + (int) (Math.random() * (width * 0.55));
        int sliderY = (int) (Math.random() * (height - SLIDER_SIZE - 20)) + 10;

        BufferedImage back = drawBackground(width, height);
        BufferedImage jigsaw = new BufferedImage(SLIDER_SIZE, SLIDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        cutSlider(back, jigsaw, sliderX, sliderY);

        String token = UUID.randomUUID().toString();
        redis.opsForValue()
                .set(PUZZLE_PREFIX + token, String.valueOf(sliderX), props.ttl().getSeconds(), TimeUnit.SECONDS);
        return new BlockPuzzle(token, toBase64Png(back), toBase64Png(jigsaw), sliderY);
    }

    /**
     * 校验拖动距离：命中即焚题并签发一次性票据；偏离容差或题目已失效（含重复提交）一律拒绝。
     * 取题与焚题原子完成（GETDEL）——并发同 token 只有一个调用方能观察到活题。
     */
    public String check(String token, int moveDistance) {
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.auth.captcha-invalid");
        }
        // 题目无论对错即焚（防观察重放），失败由前端换题重来；差值用 long 展开，杜绝 int 溢出绕过
        String answer = redis.opsForValue().getAndDelete(PUZZLE_PREFIX + token);
        if (answer == null || Math.abs(Long.parseLong(answer) - (long) moveDistance) > props.tolerance()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.auth.captcha-invalid");
        }
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(TICKET_PREFIX + ticket, "1", props.ttl().getSeconds(), TimeUnit.SECONDS);
        return ticket;
    }

    /** 登录消费票据：存在即通过并立即焚毁（一次性），不存在或格式非法即无效或重放 */
    public boolean consumeTicket(String ticket) {
        if (ticket == null || !TOKEN_PATTERN.matcher(ticket).matches()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.delete(TICKET_PREFIX + ticket));
    }

    // —— 绘图 ——

    private BufferedImage drawBackground(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x3A6EA5), width, height, new Color(0x6FA8DC)));
        g.fillRect(0, 0, width, height);
        // 随机半透明干扰元素：圆与折线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(255, 255, 255, 30 + (int) (Math.random() * 30)));
            int diameter = 20 + (int) (Math.random() * 50);
            g.fillOval((int) (Math.random() * width), (int) (Math.random() * height), diameter, diameter);
        }
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 3; i++) {
            g.drawLine(
                    (int) (Math.random() * width), (int) (Math.random() * height),
                    (int) (Math.random() * width), (int) (Math.random() * height));
        }
        g.dispose();
        return image;
    }

    /** 从背景抠出圆角拼块，背景区域挖出半透明阴影缺口 */
    private void cutSlider(BufferedImage back, BufferedImage jigsaw, int x, int y) {
        // 拼块画布按圆角形状裁剪，把背景对应区域平移进来（clip 外保持透明）
        Graphics2D jg = jigsaw.createGraphics();
        jg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        jg.setClip(new RoundRectangle2D.Float(0, 0, SLIDER_SIZE, SLIDER_SIZE, SLIDER_RADIUS * 2, SLIDER_RADIUS * 2));
        jg.drawImage(back, -x, -y, null);
        jg.dispose();

        // 背景缺口：描边加深提示
        Graphics2D bg = back.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setColor(new Color(0, 0, 0, 90));
        bg.fill(new RoundRectangle2D.Float(x, y, SLIDER_SIZE, SLIDER_SIZE, SLIDER_RADIUS * 2, SLIDER_RADIUS * 2));
        bg.setColor(new Color(255, 255, 255, 120));
        bg.setStroke(new BasicStroke(2f));
        bg.draw(new RoundRectangle2D.Float(x, y, SLIDER_SIZE, SLIDER_SIZE, SLIDER_RADIUS * 2, SLIDER_RADIUS * 2));
        bg.dispose();
    }

    private String toBase64Png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("验证码图片编码失败", e);
        }
    }
}
