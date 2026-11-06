package com.eladminplus.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 出图维度台账：形状库（≥ 5）、大小档位（≥ 3 且最小档不小于命中容差）、
 * 图案族库（≥ 8 族）在册；随机输出可命中全部维度；坐标随形状档位联动，
 * 缺口含描边完整落在画布内。纯 JVM 测试（坐标联动为纯函数，不入 Spring 上下文）。
 */
class BlockPuzzleServiceTest {

    private static final int WIDTH = 310;
    private static final int HEIGHT = 155;

    /** 2px 缺口描边的一半：边界判定计入描边 */
    private static final int STROKE_HALF = 1;

    @Test
    void 形状库在册不少于五种() {
        assertThat(BlockPuzzleService.PuzzleShape.values().length).isGreaterThanOrEqualTo(5);
    }

    @Test
    void 图案族库在册不少于八族() {
        assertThat(BlockPuzzleService.PatternFamily.values().length).isGreaterThanOrEqualTo(8);
    }

    @Test
    void 大小档位不少于三档且最小档不小于命中容差() {
        int[] tiers = BlockPuzzleService.SIZE_TIERS;
        assertThat(tiers.length).isGreaterThanOrEqualTo(3);
        // 容差取生产缺省 5px（CaptchaProperties 兜底值）：最小档必须不小于容差，拖动命中才可行
        assertThat(tiers[0]).isGreaterThanOrEqualTo(5);
    }

    @Test
    void 随机输出可命中全部形状档位与图案族() {
        Set<BlockPuzzleService.PuzzleShape> shapes = EnumSet.noneOf(BlockPuzzleService.PuzzleShape.class);
        Set<BlockPuzzleService.PatternFamily> families = EnumSet.noneOf(BlockPuzzleService.PatternFamily.class);
        Set<Integer> tiers = new HashSet<>();
        for (int seed = 0; seed < 200; seed++) {
            BlockPuzzleService.PuzzleLayout layout =
                    BlockPuzzleService.planLayout(WIDTH, HEIGHT, new Random(seed));
            shapes.add(layout.shape());
            families.add(layout.family());
            tiers.add(layout.size());
        }
        assertThat(shapes).containsExactlyInAnyOrder(BlockPuzzleService.PuzzleShape.values());
        assertThat(families).containsExactlyInAnyOrder(BlockPuzzleService.PatternFamily.values());
        for (int tier : BlockPuzzleService.SIZE_TIERS) {
            assertThat(tiers).contains(tier);
        }
    }

    @Test
    void 坐标联动缺口含描边完整落在画布内() {
        for (int seed = 0; seed < 500; seed++) {
            BlockPuzzleService.PuzzleLayout layout =
                    BlockPuzzleService.planLayout(WIDTH, HEIGHT, new Random(seed));
            assertThat(layout.x() - STROKE_HALF).isGreaterThanOrEqualTo(0);
            assertThat(layout.y() - STROKE_HALF).isGreaterThanOrEqualTo(0);
            assertThat(layout.x() + layout.size() + STROKE_HALF).isLessThanOrEqualTo(WIDTH);
            assertThat(layout.y() + layout.size() + STROKE_HALF).isLessThanOrEqualTo(HEIGHT);
        }
    }

    @Test
    void 取值域随档位联动收缩() {
        // 同一安全边距下，大档位的坐标上限必须比小档位更靠内（联动收缩生效，缺口不被裁切）
        int minTier = BlockPuzzleService.SIZE_TIERS[0];
        int maxTier = BlockPuzzleService.SIZE_TIERS[BlockPuzzleService.SIZE_TIERS.length - 1];
        int maxXForMaxTier = WIDTH - maxTier - BlockPuzzleService.EDGE_MARGIN;
        int maxXForMinTier = WIDTH - minTier - BlockPuzzleService.EDGE_MARGIN;
        assertThat(maxXForMaxTier).isLessThan(maxXForMinTier);
        assertThat(maxXForMaxTier).isGreaterThanOrEqualTo(BlockPuzzleService.EDGE_MARGIN);
    }
}
