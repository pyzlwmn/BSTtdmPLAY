package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * UI 绘制小工具（v11：抗锯齿圆角）
 *
 * Minecraft 的 GuiGraphics 没有矢量圆角 API（FPSMatch 的界面用 Modern UI 的
 * {@code setCornerRadius} 画真矢量圆角，但我们的 HUD 走的是 Forge 的 GuiGraphics 通道，
 * 拿不到 Modern UI 的画布），所以这里用**逐像素覆盖率**做抗锯齿：
 * 每个圆角像素用 4x4 超采样算出"被圆覆盖的比例"，按比例给 alpha → 边缘平滑，不是像素阶梯。
 *
 * 用法：
 *   UiDraw.panel(g, x, y, w, h, 6, 0xFF2A3342, 0xB0101620);   // 描边圆角面板
 *   UiDraw.fillRounded(g, x, y, w, h, 3, color);              // 纯圆角矩形
 */
@OnlyIn(Dist.CLIENT)
public final class UiDraw {

    /** 常用圆角半径（对应 Modern UI 里的 setCornerRadius） */
    public static final int RADIUS_SMALL = 3;    // 击杀提示
    public static final int RADIUS_MEDIUM = 5;   // 顶部计分条
    public static final int RADIUS_LARGE = 8;    // Tab / 结算面板（和 FPSM 界面一致用 8）

    private UiDraw() {
    }

    /** 圆角矩形（四角抗锯齿） */
    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        // 主体与直边（直边不需要抗锯齿）
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        // 四个圆角：按覆盖率给 alpha
        for (int j = 0; j < r; j++) {
            for (int i = 0; i < r; i++) {
                int cov = coverage(i, j, r);
                if (cov <= 0) continue;
                int alpha = baseAlpha * cov / 16;
                if (alpha <= 0) continue;
                int c = (alpha << 24) | rgb;
                g.fill(x + i, y + j, x + i + 1, y + j + 1, c);                             // 左上
                g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, c);                     // 右上
                g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, c);                     // 左下
                g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, c);             // 右下
            }
        }
    }

    /** 像素 (i,j) 落在半径 r 的圆角内（圆心 (r,r)）的覆盖率，0~16（4x4 超采样） */
    private static int coverage(int i, int j, int r) {
        int inside = 0;
        for (int sy = 0; sy < 4; sy++) {
            for (int sx = 0; sx < 4; sx++) {
                double px = i + (sx + 0.5) / 4.0;
                double py = j + (sy + 0.5) / 4.0;
                double dx = px - r;
                double dy = py - r;
                if (dx * dx + dy * dy <= (double) r * r) inside++;
            }
        }
        return inside;
    }

    /** 带 1px 描边的圆角面板（先画外圈描边，再盖一层内圈背景） */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius,
                             int borderColor, int bgColor) {
        fillRounded(g, x - 1, y - 1, w + 2, h + 2, radius + 1, borderColor);
        fillRounded(g, x, y, w, h, radius, bgColor);
    }

    /** 顶部圆角细条（标题装饰），只圆上面两角 */
    public static void topBarRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.min(radius, Math.min(w / 2, h));
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h, color);
        g.fill(x + w - r, y + r, x + w, y + h, color);

        for (int j = 0; j < r; j++) {
            for (int i = 0; i < r; i++) {
                int cov = coverage(i, j, r);
                if (cov <= 0) continue;
                int alpha = baseAlpha * cov / 16;
                if (alpha <= 0) continue;
                int c = (alpha << 24) | rgb;
                g.fill(x + i, y + j, x + i + 1, y + j + 1, c);
                g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, c);
            }
        }
    }
}
