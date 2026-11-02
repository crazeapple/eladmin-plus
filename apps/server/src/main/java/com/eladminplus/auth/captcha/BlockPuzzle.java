package com.eladminplus.auth.captcha;

/**
 * 滑块题目：背景图 / 拼块图（PNG base64）、拼块纵向位置、画布尺寸与题目标识。
 * answer 不下发，只进 Redis；尺寸随题下发供前端按比例换算拖动距离与拼块定位（渲染宽度不固定）。
 */
public record BlockPuzzle(
        String token, String back, String jigsaw, int jigsawY, int imageWidth, int imageHeight) {}
