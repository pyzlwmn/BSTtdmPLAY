package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UiDraw {

    public static final int RADIUS_SMALL = 3;
    public static final int RADIUS_MEDIUM = 5;
    public static final int RADIUS_LARGE = 8;

    private UiDraw() {
    }

    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        for (int j = 0; j < r; j++) {
            for (int i = 0; i < r; i++) {
                int cov = coverage(i, j, r);
                if (cov <= 0) continue;
                int alpha = baseAlpha * cov / 16;
                if (alpha <= 0) continue;
                int c = (alpha << 24) | rgb;
                g.fill(x + i, y + j, x + i + 1, y + j + 1, c);
                g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, c);
                g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, c);
                g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, c);
            }
        }
    }

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

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius,
                             int borderColor, int bgColor) {
        fillRounded(g, x - 1, y - 1, w + 2, h + 2, radius + 1, borderColor);
        fillRounded(g, x, y, w, h, radius, bgColor);
    }

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


    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t), rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t), rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

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

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius, int spread) {
        for (int s = spread; s >= 1; s--) {
            int a = 6 + 10 * (spread - s) / Math.max(1, spread);
            fillRounded(g, x - s, y - s + 3, w + s * 2, h + s * 2, radius + s, a << 24);
        }
    }

    public static void panelEx(GuiGraphics g, int x, int y, int w, int h, int radius,
                               int border, int gradTop, int gradBottom, int accent) {
        shadow(g, x, y, w, h, radius, 4);
        fillRounded(g, x - 1, y - 1, w + 2, h + 2, radius + 1, border);
        fillVGradient(g, x, y, w, h, radius, gradTop, gradBottom);
        if (accent != 0) topBarRounded(g, x, y, w, 2, radius, accent);
    }

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

    public static void iconCrown(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 5 || h < 4) return;
        g.fill(x, y + h - 1, x + w, y + h, color);
        int step = Math.max(1, w / 4);
        g.fill(x, y + h - 1 - step, x + step, y + h - 1, color);
        g.fill(x + (w - step) / 2, y, x + (w + step) / 2, y + h - 1, color);
        g.fill(x + w - step, y + h - 1 - step, x + w, y + h - 1, color);
    }

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
