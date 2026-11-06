package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.TeamCompetitionMap;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ★ v69：对战类玩法（solo 1v1 / pair 2v2）专用 Tab 面板。
 *
 * <p>和 tdm 的表格版完全分开，不复用布局：
 * <ul>
 *   <li>solo：左右两张选手卡（大头像 + 名字 + 状态徽章 + K/D/A/伤害），中间 VS 比分轴</li>
 *   <li>pair：左右两张队伍卡，卡内是 2 行队员条</li>
 * </ul>
 *
 * <p>修的两个 bug：
 * <ol>
 *   <li>原版把队伍 key 写死成 "red"/"blue"，而 pair/solo 的队伍名是「红队/蓝队」「选手A/选手B」
 *       → 取不到就 fallback 把所有人塞进一列</li>
 *   <li>fallback 直接把整个 Tab 玩家列表倒进红队 → 服务器里所有玩家（含不在对局的）都显示出来</li>
 * </ol>
 * 现在：按真实队伍名取 key，取不到就留空，绝不回退到全量玩家。
 */
@OnlyIn(Dist.CLIENT)
public class TcpVersusTabRenderer implements TabRenderer {

    private static final int C_RED = 0xFFE63946;
    private static final int C_BLUE = 0xFF3B82F6;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFFB9C2CF;
    private static final int C_MUTE = 0xFF8B9BB0;
    private static final int C_GOLD = 0xFFFFC93C;
    private static final int C_DEAD = 0xFF6E7C8C;
    private static final int C_TRACK = 0x33FFFFFF;

    private static final float TAB_SCALE = 1.0f;

    private static final int PAD = 12;
    private static final int HEAD_H = 42;
    private static final int FOOT_H = 16;
    private static final int CARD_MIN_W = 250;
    private static final int CARD_GAP = 22;
    private static final int AVATAR_SOLO = 40;
    private static final int AVATAR_PAIR = 18;
    private static final int ROW_H = 22;
    private static final int BAR_H = 5;

    private final String gameType;
    private final String redKey;
    private final String blueKey;
    private final String title;
    private final boolean solo;
    private final int avatarSize;

    public TcpVersusTabRenderer(String gameType) {
        this.gameType = gameType == null ? TeamCompetitionMap.TYPE_SOLO : gameType;
        this.redKey = TeamCompetitionMap.redTeamNameOf(this.gameType);
        this.blueKey = TeamCompetitionMap.blueTeamNameOf(this.gameType);
        this.title = TeamCompetitionMap.displayNameOf(this.gameType);
        this.solo = TeamCompetitionMap.TYPE_SOLO.equals(this.gameType);
        this.avatarSize = this.solo ? AVATAR_SOLO : AVATAR_PAIR;
    }

    @Override
    public String getGameType() {
        return this.gameType;
    }

    /** 本渲染器专用统计：先从 FPSMatch PlayerData 取，取不到返回 null（不画 0 假数据） */
    private static PlayerData dataOf(PlayerInfo info) {
        return RenderUtil.getPlayerData(info).orElse(null);
    }

    /**
     * ★ v70 修 bug：存活状态不能直接信 PlayerData#isLiving。
     * <p>上游只会在「死亡 / 掉线」时把 isLiving 置 false 并同步给客户端，
     * 复活时因为 isOnline() 纯服务端方法会在客户端抛异常，永远发不出 true →
     * 一旦死过就永远显示「已阵亡」。这里优先用客户端本地实体事实。
     */
    private static boolean isDead(PlayerInfo info, PlayerData pd) {
        Boolean local = TcpLivingFixer.isAliveCached(info.getProfile().getId());
        if (local != null) return !local;
        return pd != null && !pd.isLiving();
    }

    /**
     * 按真实队伍名取分组成员。
     * 只认本图的两支队伍，绝不回退到 players 全量 —— 避免把服务器其他玩家画进榜单。
     */
    private Map<String, List<PlayerInfo>> splitByTeam(List<PlayerInfo> players, String redName, String blueName) {
        Map<String, List<PlayerInfo>> byTeam = RenderUtil.getTeamsPlayerInfo(players);
        Map<String, List<PlayerInfo>> out = new LinkedHashMap<>();
        out.put("red", new ArrayList<>(byTeam.getOrDefault(redName, List.of())));
        out.put("blue", new ArrayList<>(byTeam.getOrDefault(blueName, List.of())));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int windowWidth, List<PlayerInfo> players, Scoreboard scoreboard, Objective objective) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int screenW = windowWidth;
        int screenH = mc.getWindow().getGuiScaledHeight();

        Map<String, List<PlayerInfo>> byTeam = splitByTeam(players, this.redKey, this.blueKey);
        List<PlayerInfo> reds = byTeam.getOrDefault("red", List.of());
        List<PlayerInfo> blues = byTeam.getOrDefault("blue", List.of());

        int cardW = solo ? CARD_MIN_W : Math.max(CARD_MIN_W, 300);
        int rows = solo ? 1 : 2;
        int cardH = solo
                ? (PAD * 2 + avatarSize + 8 + 30 + 22 + 24)
                : (HEAD_H - 18 + rows * (ROW_H + 2) + BAR_H + 24);
        int panelW = cardW * 2 + CARD_GAP + PAD * 2;
        int panelH = HEAD_H + Math.max(cardH, 90) + FOOT_H;

        float scale = Math.min(TAB_SCALE, Math.min(
                screenW * 0.94f / Math.max(1, panelW),
                screenH * 0.94f / Math.max(1, panelH)));
        scale = Math.max(0.45f, scale);

        g.pose().pushPose();
        g.pose().translate(screenW / 2f, screenH / 2f, 0f);
        g.pose().scale(scale, scale, 1f);

        int x = -panelW / 2;
        int y = -panelH / 2;

        UiDraw.panelEx(g, x, y, panelW, panelH, UiDraw.RADIUS_LARGE,
                0xFF2A3A4A, 0xF0141D2A, 0xE60C121B, C_GOLD);

        Font font = mc.font;
        int target = Math.max(1, TcpHudData.target());
        int redScore = TcpHudData.red();
        int blueScore = TcpHudData.blue();

        // ── 顶部：模式名 + 目标 + 比分 + 倒计时 ─────────────────────────
        int hY = y + PAD;
        g.drawString(font, this.title, x + PAD, hY, C_GOLD, false);
        UiDraw.iconCrosshair(g, x + PAD + font.width(this.title) + 8, hY + 4, 5, 0xCCFFC93C);
        g.drawString(font, "目标 " + TcpHudData.target() + " 分",
                x + PAD + font.width(this.title) + 18, hY + 1, C_DIM, false);

        drawScoreCenter(g, font, -panelW / 2, panelW, hY - 3, redScore, blueScore);

        String time = "剩余 " + TcpHudData.formatTime(TcpHudData.displaySeconds());
        g.drawString(font, time, x + panelW - PAD - font.width(time), hY + 1, C_GOLD, false);
        int barW = 110;
        int barX = x + panelW - PAD - barW;
        int barY = hY + 15;
        g.fill(barX, barY, barX + barW, barY + BAR_H, C_TRACK);
        int limit = TcpHudData.timeLimit() > 0 ? TcpHudData.timeLimit() : Math.max(1, TcpHudData.displaySeconds());
        float pct = Math.max(0f, Math.min(1f, (float) TcpHudData.displaySeconds() / limit));
        g.fill(barX, barY, barX + Math.round(barW * pct), barY + BAR_H, C_GOLD);
        UiDraw.iconClock(g, barX - 8, barY + 2, 5, 0xCCFFC93C);

        // ── 两张卡 ───────────────────────────────────────────────────
        int cardY = y + HEAD_H;
        int leftX = x + PAD;
        int rightX = leftX + cardW + CARD_GAP;

        drawCard(g, font, mc, leftX, cardY, cardW, cardH,
                reds, this.redKey, C_RED, redScore, target, solo);
        drawCard(g, font, mc, rightX, cardY, cardW, cardH,
                blues, this.blueKey, C_BLUE, blueScore, target, solo);

        // 中间的 VS
        int vsX = leftX + cardW + CARD_GAP / 2;
        int vsY = cardY + cardH / 2;
        g.drawString(font, "VS", vsX - font.width("VS") / 2, vsY - 4, C_MUTE, true);

        // ── 底部 ─────────────────────────────────────────────────────
        String foot = solo ? "1v1 · 个人成军" : "2v2 · 双人成行";
        g.drawString(font, foot, x + PAD, y + panelH - FOOT_H + 2, C_MUTE, false);

        g.pose().popPose();
    }

    private void drawScoreCenter(GuiGraphics g, Font font, int panelLeft, int panelW, int y,
                                 int redScore, int blueScore) {
        String rs = String.valueOf(redScore);
        String bs = String.valueOf(blueScore);
        float big = 1.7f;
        int rsW = (int) (font.width(rs) * big);
        int bsW = (int) (font.width(bs) * big);
        int sepW = (int) (font.width(":") * big) + 12;
        int totalW = rsW + bsW + sepW;
        int bx = panelLeft + (panelW - totalW) / 2;
        drawScaled(g, font, rs, bx, y, C_RED, big);
        drawScaled(g, font, ":", bx + rsW + 6, y + 3, C_MUTE, big * 0.8f);
        drawScaled(g, font, bs, bx + rsW + sepW, y, C_BLUE, big);
    }

    /** 一张阵营卡：solo = 大选手卡，pair = 队伍卡 + 2 行队员 */
    private void drawCard(GuiGraphics g, Font font, Minecraft mc,
                          int cx, int cy, int cw, int ch,
                          List<PlayerInfo> team, String teamName, int color,
                          int score, int target, boolean soloCard) {

        UiDraw.fillRounded(g, cx - 1, cy - 1, cw + 2, ch + 2, UiDraw.RADIUS_MEDIUM + 1, 0xFF25313D);
        UiDraw.fillVGradient(g, cx, cy, cw, ch, UiDraw.RADIUS_MEDIUM, 0x7A131C27, 0x7A0A0F16);

        if (soloCard) {
            drawSoloCard(g, font, mc, cx, cy, cw, ch, team, teamName, color, score);
        } else {
            drawPairCard(g, font, mc, cx, cy, cw, ch, team, teamName, color, score, target);
        }
    }

    private void drawSoloCard(GuiGraphics g, Font font, Minecraft mc,
                              int cx, int cy, int cw, int ch,
                              List<PlayerInfo> team, String teamName, int color, int score) {
        PlayerInfo info = team.isEmpty() ? null : team.get(0);
        PlayerData pd = info == null ? null : dataOf(info);
        boolean dead = info != null && isDead(info, pd);

        int ax = cx + (cw - avatarSize) / 2;
        int ay = cy + PAD;

        if (info == null) {
            UiDraw.fillRounded(g, ax, ay, avatarSize, avatarSize, UiDraw.RADIUS_SMALL, 0x33FFFFFF);
            g.drawString(font, "空位", ax + (avatarSize - font.width("空位")) / 2,
                    ay + avatarSize / 2 - 4, C_MUTE, false);
        } else {
            PlayerFaceRenderer.draw(g, info.getSkinLocation(), ax, ay, avatarSize);
            if (dead) {
                g.fill(ax, ay, ax + avatarSize, ay + avatarSize, 0x77000000);
            }
            int frameColor = dead ? C_DEAD : color;
            UiDraw.fillRounded(g, ax - 2, ay - 2, avatarSize + 4, 2, 1, frameColor);
            UiDraw.fillRounded(g, ax - 2, ay + avatarSize, avatarSize + 4, 2, 1, frameColor);
            UiDraw.fillRounded(g, ax - 2, ay - 2, 2, avatarSize + 4, 1, frameColor);
            UiDraw.fillRounded(g, ax + avatarSize, ay - 2, 2, avatarSize + 4, 1, frameColor);

            int nameY = ay + avatarSize + 8;
            String name = info.getProfile().getName();
            g.drawString(font, name, cx + (cw - font.width(name)) / 2, nameY,
                    dead ? C_DEAD : C_TEXT, false);

            if (dead) {
                String badge = "已阵亡";
                int bw = font.width(badge) + 8;
                UiDraw.fillRounded(g, cx + (cw - bw) / 2, nameY + 12, bw, 12, 3, 0x66E63946);
                g.drawString(font, badge, cx + (cw - bw) / 2 + 4, nameY + 15, C_RED, false);
            } else {
                String badge = "存活";
                int bw = font.width(badge) + 8;
                UiDraw.fillRounded(g, cx + (cw - bw) / 2, nameY + 12, bw, 12, 3, 0x6640C463);
                g.drawString(font, badge, cx + (cw - bw) / 2 + 4, nameY + 15, 0xFF78D89A, false);
            }

            int k = pd == null ? 0 : pd.getKills();
            int d = pd == null ? 0 : pd.getDeaths();
            int a = pd == null ? 0 : pd.getAssists();
            int dmg = pd == null ? 0 : Math.round(pd.getDamage());

            String stats = "K " + k + "  D " + d + "  A " + a;
            int sy = ay + avatarSize + 34;
            g.drawString(font, stats, cx + (cw - font.width(stats)) / 2, sy, C_DIM, false);

            String dmgS = "伤害 " + dmg;
            g.drawString(font, dmgS, cx + (cw - font.width(dmgS)) / 2, sy + 13, C_GOLD, false);
        }

        // 底部比分条
        int barX = cx + PAD;
        int barW = cw - PAD * 2;
        int barY = cy + ch - 16;
        g.fill(barX, barY, barX + barW, barY + BAR_H, C_TRACK);
        float p = Math.max(0f, Math.min(1f, score / (float) Math.max(1, TcpHudData.target())));
        g.fill(barX, barY, barX + Math.round(barW * p), barY + BAR_H, color);

        String sc = teamName + "  " + score + " 分";
        g.drawString(font, sc, cx + (cw - font.width(sc)) / 2, barY - 14, color, false);
    }

    private void drawPairCard(GuiGraphics g, Font font, Minecraft mc,
                              int cx, int cy, int cw, int ch,
                              List<PlayerInfo> team, String teamName, int color, int score, int target) {
        UiDraw.iconDot(g, cx + 10, cy + 10, 3, color);
        g.drawString(font, teamName, cx + 17, cy + 6, color, false);
        String sc = score + " 分";
        g.drawString(font, sc, cx + cw - 8 - font.width(sc), cy + 6, C_TEXT, false);

        int tw = cw - 16;
        int tx = cx + 8;
        int ty = cy + 19;
        g.fill(tx, ty, tx + tw, ty + BAR_H - 1, C_TRACK);
        float tp = Math.max(0f, Math.min(1f, score / (float) Math.max(1, target)));
        g.fill(tx, ty, tx + Math.round(tw * tp), ty + BAR_H - 1, color);

        int ry = cy + HEAD_H - 18;
        for (int i = 0; i < 2; i++) {
            PlayerInfo info = i < team.size() ? team.get(i) : null;
            int rowX = cx + 8;

            UiDraw.fillRounded(g, rowX, ry, cw - 16, ROW_H - 2, UiDraw.RADIUS_SMALL,
                    info == null ? 0x14FFFFFF : 0x1FFFFFFF);

            if (info == null) {
                g.drawString(font, "空位", rowX + 8, ry + 5, C_MUTE, false);
            } else {
                PlayerData pd = dataOf(info);
                boolean dead = isDead(info, pd);
                PlayerFaceRenderer.draw(g, info.getSkinLocation(), rowX + 3, ry + 2, avatarSize);
                if (dead) {
                    g.fill(rowX + 3, ry + 2, rowX + 3 + avatarSize, ry + 2 + avatarSize, 0x77000000);
                }

                String name = info.getProfile().getName();
                g.drawString(font, name, rowX + avatarSize + 8, ry + 5, dead ? C_DEAD : C_TEXT, false);

                int k = pd == null ? 0 : pd.getKills();
                int d = pd == null ? 0 : pd.getDeaths();
                int dmg = pd == null ? 0 : Math.round(pd.getDamage());
                String stats = "K " + k + " · D " + d + " · " + dmg + " 伤";
                g.drawString(font, stats, rowX + cw - 24 - font.width(stats), ry + 5,
                        dead ? C_DEAD : C_DIM, false);
            }
            ry += ROW_H;
        }
    }

    private void drawScaled(GuiGraphics g, Font font, String s, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, color, true);
        g.pose().popPose();
    }
}
