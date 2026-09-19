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

/**
 * 团队竞技 Tab 面板（v9）
 *
 * 进对局后按 TAB 显示：比分 / 剩余时间 / 头像 / 玩家名 / 击杀 / 死亡 / 助攻 / 伤害
 *
 * v9：
 *  - 面板**上下左右居中**（屏幕正中间）
 *  - **动态调整**：列宽/行高/面板尺寸按内容算，超出屏幕 92% 自动等比缩小（TAB_SCALE 是基准缩放）
 */
@OnlyIn(Dist.CLIENT)
public class TcpTabRenderer implements TabRenderer {

    private static final int C_RED = 0xFFE63946;
    private static final int C_BLUE = 0xFF3B82F6;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFFB9C2CF;
    private static final int C_GOLD = 0xFFFFC93C;
    private static final int C_BG = 0xE0101620;

    // 基准尺寸（都会随 TAB_SCALE 一起缩放）
    private static final float TAB_SCALE = 1.0f;     // ← 想要更大的 Tab 面板就调这里
    private static final int AVATAR = 12;
    private static final int COL_NAME = 110;
    private static final int COL_NUM = 32;
    private static final int COL_DMG = 48;
    private static final int ROW_H = 14;
    private static final int ROW_GAP = 2;
    private static final int HEADER_H = 14;          // 标题行高（比分行 / 列名行各一条）
    private static final int TEAM_GAP = 8;
    private static final int PADDING = 10;

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

        int rows = reds.size() + blues.size();
        int tableW = AVATAR + 4 + COL_NAME + COL_NUM * 3 + COL_DMG;
        int panelW = tableW + PADDING * 2;
        int panelH = HEADER_H * 2 + rows * (ROW_H + ROW_GAP) + TEAM_GAP * 2 + PADDING * 2;

        // ★ 动态缩放：内容超过屏幕 92% 就等比缩小
        float scale = Math.min(TAB_SCALE,
                Math.min(screenW * 0.92f / Math.max(1, panelW), screenH * 0.92f / Math.max(1, panelH)));
        scale = Math.max(0.5f, scale);

        // ★ 上下左右居中：平移到屏幕中心，面板以中心为原点画
        g.pose().pushPose();
        g.pose().translate(screenW / 2f, screenH / 2f, 0f);
        g.pose().scale(scale, scale, 1f);

        int x = -panelW / 2;
        int y = -panelH / 2;

        g.fill(x, y, x + panelW, y + panelH, C_BG);
        g.fill(x, y, x + panelW, y + 2, C_GOLD);

        Font font = mc.font;

        // ===== 标题行：比分 + 剩余时间 =====
        String redScore = String.valueOf(TcpHudData.red());
        String blueScore = String.valueOf(TcpHudData.blue());
        String sep = "  :  ";
        int cursorX = x + PADDING;
        int titleY = y + PADDING - 2;

        g.drawString(font, "红队", cursorX, titleY, C_RED, true);
        cursorX += font.width("红队") + 4;
        g.drawString(font, redScore, cursorX, titleY, C_RED, true);
        cursorX += font.width(redScore);
        g.drawString(font, sep, cursorX, titleY, C_DIM, true);
        cursorX += font.width(sep);
        g.drawString(font, blueScore, cursorX, titleY, C_BLUE, true);
        cursorX += font.width(blueScore) + 4;
        g.drawString(font, "蓝队", cursorX, titleY, C_BLUE, true);

        String time = "剩余 " + TcpHudData.formatTime(TcpHudData.displaySeconds());
        g.drawString(font, time, x + panelW - PADDING - font.width(time), titleY, C_GOLD, true);

        // ===== 列名行 =====
        int colY = y + PADDING + HEADER_H;
        int colX = x + PADDING + AVATAR + 4;
        g.drawString(font, "玩家", colX, colY, C_DIM, true);
        colX += COL_NAME;
        g.drawString(font, "击杀", colX, colY, C_DIM, true);
        colX += COL_NUM;
        g.drawString(font, "死亡", colX, colY, C_DIM, true);
        colX += COL_NUM;
        g.drawString(font, "助攻", colX, colY, C_DIM, true);
        colX += COL_NUM;
        g.drawString(font, "伤害", colX, colY, C_DIM, true);

        int rowY = colY + HEADER_H;
        rowY = drawTeam(g, font, x, PADDING, rowY, reds, C_RED);
        rowY += TEAM_GAP;
        drawTeam(g, font, x, PADDING, rowY, blues, C_BLUE);

        g.pose().popPose();
    }

    private int drawTeam(GuiGraphics g, Font font, int panelX, int padding, int y,
                         List<PlayerInfo> team, int color) {
        for (PlayerInfo info : team) {
            int rowX = panelX + padding;
            int textY = y + (ROW_H - 8) / 2;

            UiDraw.fillRounded(g, rowX - 1, y, AVATAR + 2, ROW_H - 1, 2, color);
            PlayerFaceRenderer.draw(g, info.getSkinLocation(), rowX, y + 1, AVATAR);

            int colX = rowX + AVATAR + 4;
            g.drawString(font, info.getProfile().getName(), colX, textY, C_TEXT, true);
            colX += COL_NAME;
            g.drawString(font, String.valueOf(stat(info, "kills")), colX, textY, C_TEXT, true);
            colX += COL_NUM;
            g.drawString(font, String.valueOf(stat(info, "deaths")), colX, textY, C_DIM, true);
            colX += COL_NUM;
            g.drawString(font, String.valueOf(stat(info, "assists")), colX, textY, C_DIM, true);
            colX += COL_NUM;
            g.drawString(font, String.valueOf(stat(info, "damage")), colX, textY, C_GOLD, true);

            y += ROW_H + ROW_GAP;
        }
        return y;
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
