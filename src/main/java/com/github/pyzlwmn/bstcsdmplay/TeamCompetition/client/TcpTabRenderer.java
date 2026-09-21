package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.TeamCompetitionMap;
import net.ptcrys.fpsmatch.common.client.tab.TabRenderer;
import net.ptcrys.fpsmatch.core.data.PlayerData;
import net.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 团队竞技 Tab 面板（v31：按 v1 效果图重做）
 *
 * ┌──────────────────────────────────────────────────────────┐
 * │ 团队竞技            12 : 9            剩余 03:42          │
 * │ 目标 30 分                       [████████░░░░]          │
 * ├───────────────────────────┬──────────────────────────────┤
 * │ ● 红队              12 分 │ ● 蓝队                 9 分  │
 * │ [████████░░░░░░░░░░░░░░░] │ [██████░░░░░░░░░░░░░░░░░]    │
 * │  玩家        K   D   A  伤害  K/D  │ 同上               │
 * │  头像 名字…                        │                     │
 * └───────────────────────────┴──────────────────────────────┘
 *               按 TAB 关闭
 *
 * 与 v9 的差别：上下堆叠 → 左右分栏；顶部大比分 + 时间进度条 + 目标分；
 * 每队进度条；伤害条；K/D 列；自己高亮；队内 MVP 三角标；数字右对齐等宽。
 *
 * ⚠️ 图标规范（主人 2026-09-21）：**不用 emoji**（MC 字体渲染成豆腐块），
 *    只用 ASCII/汉字，关键图形一律用 g.fill / UiDraw 画。
 */
@OnlyIn(Dist.CLIENT)
public class TcpTabRenderer implements TabRenderer {

    // ===== 配色 =====
    private static final int C_RED = 0xFFE63946;
    private static final int C_BLUE = 0xFF3B82F6;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFFB9C2CF;
    private static final int C_MUTE = 0xFF8B9BB0;
    private static final int C_GOLD = 0xFFFFC93C;
    private static final int C_ALT = 0x14FFFFFF;
    private static final int C_TRACK = 0x33FFFFFF;
    /** ★v50：阵亡行的文字色（变灰） */
    private static final int C_DEAD = 0xFF6E7C8C;

    /** 面板底色（半透明，游戏画面还能透出来一点） */
    private static final int C_BG = 0xE6101620;
    /** 队伍栏底色 */
    private static final int C_COL_BG = 0x66000000;

    /** 基准缩放（想整体更大就调这里；内容超屏会自动缩小） */
    private static final float TAB_SCALE = 1.0f;

    // ===== v39：比分变化时的「弹一下」动画 =====
    private int lastRed = Integer.MIN_VALUE;
    private int lastBlue = Integer.MIN_VALUE;
    private long scorePopAt = 0L;

    // ===== 尺寸 =====
    private static final int PAD = 8;
    private static final int AVATAR = 12;
    private static final int COL_NAME = 96;
    private static final int COL_NUM = 28;
    private static final int COL_DMG = 44;
    private static final int COL_KD = 34;
    private static final int ROW_H = 12;
    private static final int ROW_GAP = 1;
    private static final int COL_GAP = 10;
    private static final int BAR_H = 4;
    private static final int HEAD_H = 36;       // 顶栏（模式/目标 | 大比分 | 时间+进度）
    private static final int TEAM_HEAD_H = 26;  // 队头（队名 + 队分 + 队进度条）
    private static final int COLS_H = 11;       // 列名行
    private static final int FOOT_H = 12;

    @Override
    public String getGameType() {
        return TeamCompetitionMap.TYPE;
    }

    @Override
    public void render(GuiGraphics g, int windowWidth, List<PlayerInfo> players, Scoreboard scoreboard, Objective objective) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int screenW = windowWidth;
        int screenH = mc.getWindow().getGuiScaledHeight();

        Map<String, List<PlayerInfo>> byTeam = RenderUtil.getTeamsPlayerInfo(players);

        List<PlayerInfo> reds = new ArrayList<>(byTeam.getOrDefault("red", List.of()));
        List<PlayerInfo> blues = new ArrayList<>(byTeam.getOrDefault("blue", List.of()));
        if (reds.isEmpty() && blues.isEmpty()) {
            reds.addAll(players);                    // 队名兜底
        }

        Comparator<PlayerInfo> cmp = Comparator
                .comparingInt((PlayerInfo p) -> -stat(p, "kills"))
                .thenComparingInt(p -> -stat(p, "damage"));
        reds.sort(cmp);
        blues.sort(cmp);

        int rows = Math.max(1, Math.max(reds.size(), blues.size()));
        int colW = AVATAR + 4 + COL_NAME + COL_NUM * 3 + COL_DMG + COL_KD + 12;
        int colH = TEAM_HEAD_H + COLS_H + rows * (ROW_H + ROW_GAP) + PAD;
        int panelW = colW * 2 + COL_GAP + PAD * 2;
        int panelH = HEAD_H + colH + FOOT_H;

        // 动态缩放：内容超过屏幕 92% 就等比缩小
        float scale = Math.min(TAB_SCALE,
                Math.min(screenW * 0.92f / Math.max(1, panelW), screenH * 0.92f / Math.max(1, panelH)));
        scale = Math.max(0.5f, scale);

        // 面板以屏幕中心为原点绘制
        g.pose().pushPose();
        g.pose().translate(screenW / 2f, screenH / 2f, 0f);
        g.pose().scale(scale, scale, 1f);

        int x = -panelW / 2;
        int y = -panelH / 2;

        g.fill(x, y, x + panelW, y + panelH, C_BG);
        // v38：投影 + 渐变 + 描边 + 顶部金条（现代化质感）
        UiDraw.panelEx(g, x, y, panelW, panelH, UiDraw.RADIUS_LARGE,
                0xFF2A3A4A, 0xF0141D2A, 0xE60C121B, C_GOLD);

        Font font = mc.font;
        int target = Math.max(1, TcpHudData.target());

        // ================== 顶栏 ==================
        int hY = y + PAD;
        g.drawString(font, "团队竞技", x + PAD, hY + 1, C_DIM, false);
        UiDraw.iconCrosshair(g, x + PAD + 5, hY + 18, 5, 0xCCFFC93C);      // 准星 = 目标
        g.drawString(font, "目标 " + TcpHudData.target() + " 分", x + PAD + 14, hY + 14, C_GOLD, false);

        // 中间大比分（2 倍字号）
        int redScore = TcpHudData.red();
        int blueScore = TcpHudData.blue();
        String rs = String.valueOf(redScore);
        String bs = String.valueOf(blueScore);
        float big = 1.8f;
        int rsW = (int) (font.width(rs) * big);
        int bsW = (int) (font.width(bs) * big);
        int sepW = (int) (font.width(":") * big) + 12;
        int totalW = rsW + bsW + sepW;
        int bx = -totalW / 2;
        int by = hY - 1;
        float pop = 1f;
        if (redScore != lastRed || blueScore != lastBlue) {
            lastRed = redScore;
            lastBlue = blueScore;
            scorePopAt = System.currentTimeMillis();
        }
        float popT = Math.min(1f, (System.currentTimeMillis() - scorePopAt) / 320f);
        pop = 1f + 0.32f * (1f - popT) * (1f - popT);      // 1.32 → 1.00 回弹
        drawScaled(g, font, rs, bx, by, C_RED, big * pop);
        drawScaled(g, font, ":", bx + rsW + 6, by + 2, C_MUTE, big * 0.8f);
        drawScaled(g, font, bs, bx + rsW + sepW, by, C_BLUE, big * pop);

        // 右侧：剩余时间 + 进度条
        String time = "剩余 " + TcpHudData.formatTime(TcpHudData.displaySeconds());
        g.drawString(font, time, x + panelW - PAD - font.width(time), hY + 1, C_GOLD, false);
        int barW = 120;
        int barX = x + panelW - PAD - barW;
        int barY = hY + 14;
        g.fill(barX, barY, barX + barW, barY + BAR_H, C_TRACK);
        int limit = TcpHudData.timeLimit() > 0 ? TcpHudData.timeLimit() : Math.max(1, TcpHudData.displaySeconds());
        float pct = Math.max(0f, Math.min(1f, (float) TcpHudData.displaySeconds() / limit));
        g.fill(barX, barY, barX + Math.round(barW * pct), barY + BAR_H, C_GOLD);
        UiDraw.iconClock(g, barX - 8, barY + 2, 5, 0xCCFFC93C);            // 时钟 = 剩余时间

        // ================== 两栏 ==================
        int colY = y + HEAD_H;
        int maxDmg = 0;
        for (PlayerInfo p : reds) maxDmg = Math.max(maxDmg, stat(p, "damage"));
        for (PlayerInfo p : blues) maxDmg = Math.max(maxDmg, stat(p, "damage"));

        drawTeam(g, font, mc, x + PAD, colY, colW, colH, reds, C_RED, "红队", redScore, target, maxDmg);
        drawTeam(g, font, mc, x + PAD + colW + COL_GAP, colY, colW, colH, blues, C_BLUE, "蓝队", blueScore, target, maxDmg);

        // ================== 底栏（只保留两队总伤害；那行长提示已按主人要求删除）==================
        int redDmg = 0, blueDmg = 0;
        for (PlayerInfo p : reds) redDmg += stat(p, "damage");
        for (PlayerInfo p : blues) blueDmg += stat(p, "damage");
        String foot = "红队总伤害 " + redDmg + "  ·  蓝队总伤害 " + blueDmg;
        g.drawString(font, foot, x + PAD, y + panelH - FOOT_H + 1, C_MUTE, false);

        g.pose().popPose();
    }

    /** 画一支队伍的一栏 */
    private void drawTeam(GuiGraphics g, Font font, Minecraft mc, int colX, int colY, int colW, int colH,
                          List<PlayerInfo> team, int color, String teamName, int score, int target, int maxDmg) {
        // 栏底：v38 描边 + 渐变卡片
        UiDraw.fillRounded(g, colX - 1, colY - 1, colW + 2, colH + 2, UiDraw.RADIUS_SMALL + 1, 0xFF25313D);
        UiDraw.fillVGradient(g, colX, colY, colW, colH, UiDraw.RADIUS_SMALL, 0x6B131C27, 0x6B0A0F16);

        // 队头：色点 + 队名 + 队分
        UiDraw.iconDot(g, colX + 10, colY + 10, 3, color);
        g.drawString(font, teamName, colX + 17, colY + 6, color, false);
        String sc = score + " 分";
        g.drawString(font, sc, colX + colW - 7 - font.width(sc), colY + 6, C_TEXT, false);

        // 队进度条（离目标分还差多少）
        int tw = colW - 14;
        int tx = colX + 7;
        int ty = colY + 17;
        g.fill(tx, ty, tx + tw, ty + BAR_H, C_TRACK);
        float tp = Math.max(0f, Math.min(1f, (float) score / Math.max(1, target)));
        g.fill(tx, ty, tx + Math.round(tw * tp), ty + BAR_H, color);

        // 列名行（右对齐数字列）
        int nameY = colY + TEAM_HEAD_H;
        int cx = colX + 7;
        g.drawString(font, "玩家", cx + AVATAR + 4, nameY, C_MUTE, false);
        cx += AVATAR + 4 + COL_NAME;
        g.drawString(font, "K", cx + COL_NUM - font.width("K"), nameY, C_MUTE, false);
        cx += COL_NUM;
        g.drawString(font, "D", cx + COL_NUM - font.width("D"), nameY, C_MUTE, false);
        cx += COL_NUM;
        g.drawString(font, "A", cx + COL_NUM - font.width("A"), nameY, C_MUTE, false);
        cx += COL_NUM;
        g.drawString(font, "伤害", cx + COL_DMG - font.width("伤害"), nameY, C_MUTE, false);
        cx += COL_DMG;
        g.drawString(font, "K/D", cx + COL_KD - font.width("K/D"), nameY, C_MUTE, false);

        // 玩家行
        UUID self = mc.player == null ? null : mc.player.getUUID();
        int ry = nameY + COLS_H;
        for (int i = 0; i < team.size(); i++) {
            PlayerInfo info = team.get(i);
            boolean isSelf = self != null && self.equals(info.getProfile().getId());
            // ★ v50：阵亡（未复活）的行整体变灰
            PlayerData pd = dataOf(info).orElse(null);
            boolean dead = pd != null && !pd.isLiving();
            if (dead && !isSelf) {
                g.fill(colX + 2, ry, colX + colW - 2, ry + ROW_H, 0x33000000);
            }
            if (isSelf) {
                g.fill(colX + 2, ry, colX + colW - 2, ry + ROW_H, 0x33FFC93C);
                g.fill(colX + 2, ry, colX + 4, ry + ROW_H, C_GOLD);
            } else if (i % 2 == 1) {
                g.fill(colX + 2, ry, colX + colW - 2, ry + ROW_H, C_ALT);
            }

            PlayerFaceRenderer.draw(g, info.getSkinLocation(), colX + 7, ry, AVATAR);

            int kills = stat(info, "kills");
            int deaths = stat(info, "deaths");
            int assists = stat(info, "assists");
            int dmg = stat(info, "damage");

            if (dead) {
                // 头像盖一层灰（半透明黑），文字用暗色
                g.fill(colX + 7, ry, colX + 7 + AVATAR, ry + AVATAR, 0x77000000);
            }
            int tx2 = colX + 7 + AVATAR + 4;
            g.drawString(font, info.getProfile().getName(), tx2, ry + 2, dead ? C_DEAD : (isSelf ? C_GOLD : C_TEXT), false);

            // 队内第一：v38 自绘皇冠（不用 emoji）
            if (i == 0) {
                UiDraw.iconCrown(g, tx2 + COL_NAME - 9, ry + 4, 8, 5, C_GOLD);
            }

            int numX = tx2 + COL_NAME;
            g.drawString(font, String.valueOf(kills), numX + COL_NUM - font.width(String.valueOf(kills)), ry + 2, dead ? C_DEAD : C_TEXT, false);
            numX += COL_NUM;
            g.drawString(font, String.valueOf(deaths), numX + COL_NUM - font.width(String.valueOf(deaths)), ry + 2, C_DIM, false);
            numX += COL_NUM;
            g.drawString(font, String.valueOf(assists), numX + COL_NUM - font.width(String.valueOf(assists)), ry + 2, 0xFF9AD0A0, false);
            numX += COL_NUM;

            // 伤害：数字 + 底条（一眼看出谁在输出）
            String ds = String.valueOf(dmg);
            g.drawString(font, ds, numX + COL_DMG - font.width(ds), ry + 2, C_GOLD, false);
            if (maxDmg > 0 && dmg > 0) {
                int bw = Math.max(1, Math.round(COL_DMG * (float) dmg / maxDmg));
                g.fill(numX, ry + ROW_H - 2, numX + bw, ry + ROW_H - 1, 0x99FFC93C);
            }
            numX += COL_DMG;

            // K/D 比
            String kd = String.format(java.util.Locale.ROOT, "%.2f", kills / (float) Math.max(1, deaths));
            g.drawString(font, kd, numX + COL_KD - font.width(kd), ry + 2, C_DIM, false);

            ry += ROW_H + ROW_GAP;
        }
    }

    /** 2× 缩放的文字（大比分用） */
    private void drawScaled(GuiGraphics g, Font font, String s, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, color, true);
        g.pose().popPose();
    }

    private static Optional<PlayerData> dataOf(PlayerInfo info) {
        return RenderUtil.getPlayerData(info);
    }

    private static int stat(PlayerInfo info, String key) {
        PlayerData d = dataOf(info).orElse(null);
        if (d == null) return 0;
        return switch (key) {
            case "kills" -> d.getKills();
            case "deaths" -> d.getDeaths();
            case "assists" -> d.getAssists();
            case "scores" -> d.getScores();
            case "damage" -> Math.round(d.getDamage());
            default -> 0;
        };
    }
}
