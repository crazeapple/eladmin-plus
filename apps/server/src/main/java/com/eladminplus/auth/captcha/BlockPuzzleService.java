package com.eladminplus.auth.captcha;

import com.eladminplus.common.error.BusinessException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
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

    /** 背景渐变配色（成对取用，出题随机选一组） */
    private static final int[][] PALETTES = {
        {0x3A6EA5, 0x6FA8DC},
        {0x2E8B57, 0x8FBC8F},
        {0x6D5BD0, 0xB7A8F0},
        {0xB45309, 0xF5C563},
        {0x0F766E, 0x5EEAD4},
        {0xBE4A6A, 0xF0A3B8},
    };

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
        int shapeType = (int) (Math.random() * 3);
        cutSlider(back, jigsaw, shapeType, sliderX, sliderY);

        String token = UUID.randomUUID().toString();
        redis.opsForValue()
                .set(PUZZLE_PREFIX + token, String.valueOf(sliderX), props.ttl().getSeconds(), TimeUnit.SECONDS);
        return new BlockPuzzle(token, toBase64Png(back), toBase64Png(jigsaw), sliderY, width, height);
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

    /** 拼块形状随机（圆角方块 / 圆 / 六边形），拼块与缺口同形，避免永远同形同大 */
    private Shape puzzleShape(int shapeType, double x, double y) {
        return switch (shapeType) {
            case 1 -> new Ellipse2D.Double(x, y, SLIDER_SIZE, SLIDER_SIZE);
            case 2 -> hexagon(x, y);
            default -> new RoundRectangle2D.Double(
                    x, y, SLIDER_SIZE, SLIDER_SIZE, SLIDER_RADIUS * 2, SLIDER_RADIUS * 2);
        };
    }

    /** 平六边形（顶点内切于拼块画布） */
    private Path2D hexagon(double x, double y) {
        double r = SLIDER_SIZE / 2.0;
        double cx = x + r;
        double cy = y + r;
        Path2D path = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            double px = cx + r * Math.cos(angle);
            double py = cy + r * Math.sin(angle);
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.closePath();
        return path;
    }

    private BufferedImage drawBackground(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 配色随机：渐变走对角线
        int[] palette = PALETTES[(int) (Math.random() * PALETTES.length)];
        g.setPaint(new GradientPaint(0, 0, new Color(palette[0]), width, height, new Color(palette[1])));
        g.fillRect(0, 0, width, height);
        // 半透明大形状干扰：圆与圆角块交错
        for (int i = 0; i < 7; i++) {
            g.setColor(new Color(255, 255, 255, 24 + (int) (Math.random() * 34)));
            if ((i & 1) == 0) {
                int diameter = 20 + (int) (Math.random() * 56);
                g.fillOval((int) (Math.random() * width), (int) (Math.random() * height), diameter, diameter);
            } else {
                int side = 26 + (int) (Math.random() * 60);
                g.fillRoundRect(
                        (int) (Math.random() * width), (int) (Math.random() * height), side, side, 18, 18);
            }
        }
        // 细折线
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 3; i++) {
            g.drawLine(
                    (int) (Math.random() * width), (int) (Math.random() * height),
                    (int) (Math.random() * width), (int) (Math.random() * height));
        }
        // 噪点：明暗随机的细碎颗粒，压掉大面积纯渐变的塑料感
        for (int i = 0; i < 320; i++) {
            g.setColor(new Color(255, 255, 255, (int) (Math.random() * 26)));
            g.fillRect((int) (Math.random() * width), (int) (Math.random() * height), 1, 1);
        }
        g.dispose();
        return image;
    }

    /** 从背景抠出拼块（形状随机），背景区域挖出半透明阴影缺口 */
    private void cutSlider(BufferedImage back, BufferedImage jigsaw, int shapeType, int x, int y) {
        // 拼块画布按形状裁剪，把背景对应区域平移进来（clip 外保持透明）
        Graphics2D jg = jigsaw.createGraphics();
        jg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        jg.setClip(puzzleShape(shapeType, 0, 0));
        jg.drawImage(back, -x, -y, null);
        jg.dispose();

        // 背景缺口：同形，描边加深提示
        Graphics2D bg = back.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setColor(new Color(0, 0, 0, 90));
        bg.fill(puzzleShape(shapeType, x, y));
        bg.setColor(new Color(255, 255, 255, 120));
        bg.setStroke(new BasicStroke(2f));
        bg.draw(puzzleShape(shapeType, x, y));
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
