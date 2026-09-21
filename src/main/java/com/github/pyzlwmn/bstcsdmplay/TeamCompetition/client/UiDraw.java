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

    // ================== v38：渐变 / 阴影 / 自绘图标 ==================

    /** 颜色线性插值（含 alpha） */
    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t), rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t), rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** 某一行距离圆角区顶/底 d 像素时的水平内缩量 */
    private static int cornerInset(int dTop, int dBottom, int r) {
        int best = 0;
        if (dTop < r) {
            double dy = r - 1 - dTop;
            best = Math.max(best, (int) Math.ceil(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy))));
        }
        if (dBottom < r) {
            double dy = r - 1 - dBottom;
            best = Math.max(best, (int) Math.ceil(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy))));
        }
        return best;
    }

    /** 竖向渐变圆角矩形（★v43：逐行 1px 抗锯齿，不再是阶梯像素） */
    public static void fillVGradient(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        for (int dy = 0; dy < h; dy++) {
            float t = h <= 1 ? 0f : (float) dy / (h - 1);
            int c = lerpColor(top, bottom, t);
            int a = (c >>> 24) & 0xFF;
            if (a <= 0) continue;
            int rgb = c & 0xFFFFFF;

            double dyc = dy + 0.5;
            double d = Math.min(dyc, h - dyc);
            double inset = 0.0;
            if (r > 0 && d < r) {
                double k = r - d;
                inset = r - Math.sqrt(Math.max(0.0, (double) r * r - k * k));
            }
            double xl = x + inset;
            int ix0 = (int) Math.floor(xl);
            double frac = 1.0 - (xl - ix0);
            if (ix0 < x) { ix0 = x; frac = 1.0; }
            int rightEdge = x + w - (ix0 - x);
            if (rightEdge > x + w) rightEdge = x + w;

            if (rightEdge - 1 > ix0 + 1) {
                g.fill(ix0 + 1, y + dy, rightEdge - 1, y + dy + 1, c);
            }
            if (frac > 0.02) {
                int ca = (int) Math.round(a * frac);
                if (ca > 0) {
                    int cc = (ca << 24) | rgb;
                    g.fill(ix0, y + dy, ix0 + 1, y + dy + 1, cc);
                    g.fill(rightEdge - 1, y + dy, rightEdge, y + dy + 1, cc);
                }
            }
        }
    }

    /** 投影（面板后面叠几层扩散的半透明黑圆角） */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius, int spread) {
        for (int s = spread; s >= 1; s--) {
            int a = 6 + 10 * (spread - s) / Math.max(1, spread);
            fillRounded(g, x - s, y - s + 3, w + s * 2, h + s * 2, radius + s, a << 24);
        }
    }

    /** 圆角面板「豪华版」：投影 + 渐变底 + 1px 描边 + 顶部高光条 */
    public static void panelEx(GuiGraphics g, int x, int y, int w, int h, int radius,
                               int border, int gradTop, int gradBottom, int accent) {
        shadow(g, x, y, w, h, radius, 4);
        fillRounded(g, x - 1, y - 1, w + 2, h + 2, radius + 1, border);
        fillVGradient(g, x, y, w, h, radius, gradTop, gradBottom);
        if (accent != 0) topBarRounded(g, x, y, w, 2, radius, accent);
    }

    /** 准星图标（圆环 + 十字） */
    public static void iconCrosshair(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int yy = -r; yy <= r; yy++) {
            for (int xx = -r; xx <= r; xx++) {
                double d2 = (double) xx * xx + (double) yy * yy;
                if (d2 <= (double) r * r && d2 >= (double) (r - 1) * (r - 1)) {
                    g.fill(cx + xx, cy + yy, cx + xx + 1, cy + yy + 1, color);
                }
            }
        }
        g.fill(cx - r - 2, cy, cx - 2, cy + 1, color);
        g.fill(cx + 3, cy, cx + r + 3, cy + 1, color);
        g.fill(cx, cy - r - 2, cx + 1, cy - 2, color);
        g.fill(cx, cy + 3, cx + 1, cy + r + 3, color);
    }

    /** 时钟图标（圆环 + 指针） */
    public static void iconClock(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int yy = -r; yy <= r; yy++) {
            for (int xx = -r; xx <= r; xx++) {
                double d2 = (double) xx * xx + (double) yy * yy;
                if (d2 <= (double) r * r && d2 >= (double) (r - 1) * (r - 1)) {
                    g.fill(cx + xx, cy + yy, cx + xx + 1, cy + yy + 1, color);
                }
            }
        }
        for (int i = 1; i <= r - 1; i++) g.fill(cx, cy - i, cx + 1, cy - i + 1, color);
        for (int i = 1; i <= r - 2; i++) g.fill(cx + i, cy, cx + i + 1, cy + 1, color);
    }

    /** 皇冠图标（三尖 + 底座） */
    public static void iconCrown(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 5 || h < 4) return;
        g.fill(x, y + h - 1, x + w, y + h, color);
        int step = Math.max(1, w / 4);
        g.fill(x, y + h - 1 - step, x + step, y + h - 1, color);
        g.fill(x + (w - step) / 2, y, x + (w + step) / 2, y + h - 1, color);
        g.fill(x + w - step, y + h - 1 - step, x + w, y + h - 1, color);
    }

    /** 实心圆点 */
    public static void iconDot(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int yy = -r; yy <= r; yy++) {
            for (int xx = -r; xx <= r; xx++) {
                if ((double) xx * xx + (double) yy * yy <= (double) r * r + 0.25) {
                    g.fill(cx + xx, cy + yy, cx + xx + 1, cy + yy + 1, color);
                }
            }
        }
    }
}
