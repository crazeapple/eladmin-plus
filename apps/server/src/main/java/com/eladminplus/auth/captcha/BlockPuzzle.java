package com.eladminplus.auth.captcha;

/** 滑块题目：背景图 / 拼块图（PNG base64）、拼块纵向位置与题目标识。answer 不下发，只进 Redis。 */
public record BlockPuzzle(String token, String back, String jigsaw, int jigsawY) {}
