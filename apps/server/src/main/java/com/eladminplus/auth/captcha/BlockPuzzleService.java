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
 * 绘图维度程序化丰富：图案族库（≥ 8 族，族间算法不同）、形状库（≥ 5 种）、
 * 大小档位（≥ 3 档）随题随机，坐标随形状档位联动保证缺口完整落在画布内。
 */
@Service
public class BlockPuzzleService {

    public static final String PUZZLE_PREFIX = "auth:captcha:puzzle:";
    public static final String TICKET_PREFIX = "auth:captcha:ticket:";

    /** 缺口描边与画布边缘的安全边距（描边 2px + 抗锯齿余量） */
    static final int EDGE_MARGIN = 8;

    /** 拼块画布尺寸档位（升序；最小档不小于拖动命中容差 5px） */
    static final int[] SIZE_TIERS = {40, 48, 56};

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

    /** 拼块形状目录（拼块与缺口同形） */
    enum PuzzleShape {
        /** 圆角方块 */
        ROUNDED_SQUARE,
        /** 圆 */
        CIRCLE,
        /** 六边形 */
        HEXAGON,
        /** 凸凹拼图轮廓（顶部凸榫 + 右侧凹槽） */
        PUZZLE_TAB,
        /** 菱形 */
        DIAMOND
    }

    /** 背景图案族目录（族间算法不同，程序化绘制零图片资产） */
    enum PatternFamily {
        /** 波纹：横向正弦带堆叠 */
        RIPPLE,
        /** 点阵：规则网格圆点 */
        DOT_MATRIX,
        /** 网格：正交直线网 */
        GRID,
        /** 等高线：同心圆环 */
        CONTOUR,
        /** 斜纹：平行对角直线 */
        DIAGONAL_STRIPE,
        /** 蜂窝：平铺六边形轮廓 */
        HONEYCOMB,
        /** 同心圆：放射状整圆 */
        CONCENTRIC,
        /** 三角网格：格点连线的三角网 */
        TRIANGLE_MESH,
        /** 竖波：纵向折线波 */
        WAVY_LINES
    }

    /** 出图随机维度台账：图案族 / 形状 / 档位 / 联动后的缺口坐标 */
    record PuzzleLayout(PatternFamily family, PuzzleShape shape, int size, int x, int y) {}

    private final StringRedisTemplate redis;
    private final CaptchaProperties props;

    public BlockPuzzleService(StringRedisTemplate redis, CaptchaProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** 生成题目：程序化绘制背景与拼块，缺口横向位置（答案）只进 Redis */
    public BlockPuzzle generate() {
        java.util.Random random = new java.util.Random();
        int width = props.imageWidth();
        int height = props.imageHeight();
        PuzzleLayout layout = planLayout(width, height, random);

        BufferedImage back = drawBackground(width, height, layout.family(), random);
        BufferedImage jigsaw = new BufferedImage(layout.size(), layout.size(), BufferedImage.TYPE_INT_ARGB);
        cutSlider(back, jigsaw, layout);

        String token = UUID.randomUUID().toString();
        redis.opsForValue()
                .set(PUZZLE_PREFIX + token, String.valueOf(layout.x()), props.ttl().getSeconds(), TimeUnit.SECONDS);
        return new BlockPuzzle(token, toBase64Png(back), toBase64Png(jigsaw), layout.y(), width, height);
    }

    /**
     * 坐标联动（纯函数）：随机选图案族 / 形状 / 档位后，缺口位置按档位尺寸收缩取值域，
     * 保证含描边的缺口完整落在画布内；横向位置即校验答案。
     */
    static PuzzleLayout planLayout(int width, int height, java.util.Random random) {
        PatternFamily family = PatternFamily.values()[random.nextInt(PatternFamily.values().length)];
        PuzzleShape shape = PuzzleShape.values()[random.nextInt(PuzzleShape.values().length)];
        int size = SIZE_TIERS[random.nextInt(SIZE_TIERS.length)];
        int x = EDGE_MARGIN + random.nextInt(Math.max(1, width - size - EDGE_MARGIN * 2));
        int y = EDGE_MARGIN + random.nextInt(Math.max(1, height - size - EDGE_MARGIN * 2));
        return new PuzzleLayout(family, shape, size, x, y);
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

    /** 形状工厂：拼块与缺口共用，几何以拼块画布（size × size）为界内接 */
    private Shape puzzleShape(PuzzleShape shape, double x, double y, double size) {
        return switch (shape) {
            case CIRCLE -> new Ellipse2D.Double(x, y, size, size);
            case HEXAGON -> hexagon(x, y, size);
            case DIAMOND -> diamond(x, y, size);
            case PUZZLE_TAB -> puzzleTab(x, y, size);
            default -> new RoundRectangle2D.Double(
                    x, y, size, size, size * 0.25, size * 0.25);
        };
    }

    /** 平六边形（顶点内切于拼块画布） */
    private Path2D hexagon(double x, double y, double size) {
        double r = size / 2.0;
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

    /** 菱形（对角线内切于拼块画布） */
    private Path2D diamond(double x, double y, double size) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + size / 2, y);
        path.lineTo(x + size, y + size / 2);
        path.lineTo(x + size / 2, y + size);
        path.lineTo(x, y + size / 2);
        path.closePath();
        return path;
    }

    /**
     * 凸凹拼图轮廓：方形主体，顶边中央外凸半圆榫、右边中央内凹半圆槽，
     * 半圆以三次贝塞尔近似；榫槽均收在拼块画布内（主体内缩榫半径）。
     */
    private Path2D puzzleTab(double x, double y, double size) {
        double r = size * 0.2;
        double left = x + r;
        double right = x + size - r;
        double top = y + r;
        double bottom = y + size - r;
        double midX = x + size / 2;
        double midY = y + size / 2;
        // 贝塞尔控制点外扩系数：1.33 使三次曲线近似半圆周
        double k = r * 1.33;

        Path2D path = new Path2D.Double();
        path.moveTo(left, top);
        // 顶边：左段 → 凸榫（向外/向上） → 右段
        path.lineTo(midX - r, top);
        path.curveTo(midX - r, top - k, midX + r, top - k, midX + r, top);
        path.lineTo(right, top);
        // 右边：上段 → 凹槽（向内/向左） → 下段
        path.lineTo(right, midY - r);
        path.curveTo(right - k, midY - r * 0.55, right - k, midY + r * 0.55, right, midY + r);
        path.lineTo(right, bottom);
        // 底边与左边直线收口
        path.lineTo(left, bottom);
        path.lineTo(left, top);
        path.closePath();
        return path;
    }

    /** 背景绘制：对角渐变打底 → 图案族覆盖 → 细折线与噪点收质感 */
    private BufferedImage drawBackground(int width, int height, PatternFamily family, java.util.Random random) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 配色随机：渐变走对角线
        int[] palette = PALETTES[random.nextInt(PALETTES.length)];
        g.setPaint(new GradientPaint(0, 0, new Color(palette[0]), width, height, new Color(palette[1])));
        g.fillRect(0, 0, width, height);

        drawFamily(g, width, height, family, random);

        // 细折线
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 3; i++) {
            g.drawLine(
                    random.nextInt(width), random.nextInt(height),
                    random.nextInt(width), random.nextInt(height));
        }
        // 噪点：明暗随机的细碎颗粒，压掉大面积纯渐变的塑料感
        for (int i = 0; i < 320; i++) {
            g.setColor(new Color(255, 255, 255, random.nextInt(26)));
            g.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
        }
        g.dispose();
        return image;
    }

    /** 图案族分发：各族算法不同，半透明白色系绘制保证缺口可辨 */
    private void drawFamily(Graphics2D g, int width, int height, PatternFamily family, java.util.Random random) {
        switch (family) {
            case RIPPLE -> {
                // 横向正弦带：多行相位渐移的波浪线堆叠
                g.setColor(new Color(255, 255, 255, 60));
                g.setStroke(new BasicStroke(1.8f));
                int bands = 6 + random.nextInt(4);
                double amplitude = 6 + random.nextDouble() * 8;
                double phase = random.nextDouble() * Math.PI * 2;
                for (int row = 0; row < bands; row++) {
                    double baseY = (height / (double) bands) * row + 8;
                    Path2D wave = new Path2D.Double();
                    for (int px = 0; px <= width; px += 4) {
                        double py = baseY + amplitude * Math.sin(px / 28.0 + phase + row * 0.6);
                        if (px == 0) {
                            wave.moveTo(px, py);
                        } else {
                            wave.lineTo(px, py);
                        }
                    }
                    g.draw(wave);
                }
            }
            case DOT_MATRIX -> {
                // 点阵：规则网格圆点，行距列距固定、点径微变
                g.setColor(new Color(255, 255, 255, 66));
                int gap = 18;
                for (int py = gap; py < height; py += gap) {
                    for (int px = gap; px < width; px += gap) {
                        double d = 2 + random.nextDouble() * 2.5;
                        g.fill(new Ellipse2D.Double(px - d / 2, py - d / 2, d, d));
                    }
                }
            }
            case GRID -> {
                // 正交网格：竖横直线交错，间距随机定档
                g.setColor(new Color(255, 255, 255, 46));
                g.setStroke(new BasicStroke(1.2f));
                int gap = 20 + random.nextInt(12);
                for (int px = gap; px < width; px += gap) {
                    g.drawLine(px, 0, px, height);
                }
                for (int py = gap; py < height; py += gap) {
                    g.drawLine(0, py, width, py);
                }
            }
            case CONTOUR -> {
                // 等高线：同心圆环簇，环心随机、环距渐密
                g.setColor(new Color(255, 255, 255, 58));
                g.setStroke(new BasicStroke(1.4f));
                double cx = random.nextInt(width);
                double cy = random.nextInt(height);
                int rings = 5 + random.nextInt(4);
                for (int i = 1; i <= rings; i++) {
                    double r = i * (9 + random.nextDouble() * 4);
                    g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
                }
            }
            case DIAGONAL_STRIPE -> {
                // 斜纹：平行对角直线，方向与间距随机定档
                g.setColor(new Color(255, 255, 255, 50));
                g.setStroke(new BasicStroke(2f));
                boolean lean = random.nextBoolean();
                int gap = 16 + random.nextInt(10);
                for (int offset = -height; offset < width + height; offset += gap) {
                    if (lean) {
                        g.drawLine(offset, 0, offset + height, height);
                    } else {
                        g.drawLine(offset + height, 0, offset, height);
                    }
                }
            }
            case HONEYCOMB -> {
                // 蜂窝：平铺六边形轮廓（两行错位半格）
                g.setColor(new Color(255, 255, 255, 52));
                g.setStroke(new BasicStroke(1.2f));
                double cell = 22 + random.nextInt(8);
                double rowStep = cell * 0.75;
                for (int row = 0; row * rowStep < height + cell; row++) {
                    for (int col = 0; col * cell * 1.5 < width + cell; col++) {
                        double ox = col * cell * 1.5 + ((row & 1) == 1 ? cell * 0.75 : 0);
                        double oy = row * rowStep;
                        g.draw(hexagon(ox, oy, cell));
                    }
                }
            }
            case CONCENTRIC -> {
                // 同心圆：单心整圆放射，环距均匀
                g.setColor(new Color(255, 255, 255, 44));
                g.setStroke(new BasicStroke(1.6f));
                double cx = random.nextInt(width);
                double cy = random.nextInt(height);
                int rings = 4 + random.nextInt(4);
                int step = 16 + random.nextInt(10);
                for (int i = 1; i <= rings; i++) {
                    double r = i * step;
                    g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
                }
            }
            case TRIANGLE_MESH -> {
                // 三角网格：错半格的格点两两连线成三角网
                g.setColor(new Color(255, 255, 255, 46));
                g.setStroke(new BasicStroke(1.1f));
                int gap = 26 + random.nextInt(10);
                for (int py = 0; py <= height + gap; py += gap) {
                    for (int px = 0; px <= width + gap; px += gap) {
                        int shift = ((py / gap) & 1) == 1 ? gap / 2 : 0;
                        int x0 = px + shift;
                        if ((px / gap + py / gap) % 2 == 0) {
                            g.drawLine(x0, py, x0 + gap, py + gap);
                            g.drawLine(x0, py, x0 - gap, py + gap);
                        } else {
                            g.drawLine(x0, py + gap, x0 + gap, py);
                            g.drawLine(x0, py + gap, x0 - gap, py);
                        }
                    }
                }
            }
            default -> {
                // 竖波：纵向锯齿折线阵列（与波纹的横向正弦族区分）
                g.setColor(new Color(255, 255, 255, 54));
                g.setStroke(new BasicStroke(1.6f));
                int gap = 22 + random.nextInt(10);
                double amplitude = 5 + random.nextDouble() * 6;
                for (int baseX = gap; baseX < width; baseX += gap) {
                    Path2D zig = new Path2D.Double();
                    boolean up = random.nextBoolean();
                    for (int py = 0; py <= height; py += 6) {
                        double px = baseX + amplitude * (up ? 1 : -1) * ((py / 6) % 2);
                        if (py == 0) {
                            zig.moveTo(px, py);
                        } else {
                            zig.lineTo(px, py);
                        }
                    }
                    g.draw(zig);
                }
            }
        }
    }

    /** 从背景抠出拼块（形状与档位随题），背景区域挖出半透明阴影缺口 */
    private void cutSlider(BufferedImage back, BufferedImage jigsaw, PuzzleLayout layout) {
        int size = layout.size();
        int x = layout.x();
        int y = layout.y();
        Shape pieceAtOrigin = puzzleShape(layout.shape(), 0, 0, size);

        // 拼块画布按形状裁剪，把背景对应区域平移进来（clip 外保持透明）
        Graphics2D jg = jigsaw.createGraphics();
        jg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        jg.setClip(pieceAtOrigin);
        jg.drawImage(back, -x, -y, null);
        jg.dispose();

        // 背景缺口：同形同位，描边加深提示
        Shape holeInPlace = puzzleShape(layout.shape(), x, y, size);
        Graphics2D bg = back.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setColor(new Color(0, 0, 0, 90));
        bg.fill(holeInPlace);
        bg.setColor(new Color(255, 255, 255, 120));
        bg.setStroke(new BasicStroke(2f));
        bg.draw(holeInPlace);
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
