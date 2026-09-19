package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import net.ptcrys.fpsmatch.common.client.screen.hud.IHudRenderer;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpMatchEndS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;
import java.util.UUID;

/**
 * 团队竞技 HUD 模板 v6
 *
 * 顶部计分条（整体贴屏幕上沿，三行，整体缩放 HUD_SCALE）：
 *   ▌[蓝队头像组]        12 : 9        [红队头像组]▐
 *                       目标：30                      ← 小字
 *                        01:24                        ← 冒号严格对齐屏幕中线
 *
 * 开局前倒计时：屏幕中央大数字（倒计时结束才开始正式计时）
 * 击杀提示（右上，最多 5 条，5 秒消失）：▌击杀者 [枪包 slot 贴图] 被击杀者
 *
 * 注册方式见 TcpClientSetup：FPSMGameHudManager.INSTANCE.registerHud(TeamCompetitionMap.TYPE, new TcpHud())
 */
@OnlyIn(Dist.CLIENT)
public class TcpHud implements IHudRenderer {

    // 颜色（ARGB）
    private static final int C_BG = 0xB0101620;      // 深色半透明底
    private static final int C_RED = 0xFFE63946;     // 红队
    private static final int C_BLUE = 0xFF3B82F6;    // 蓝队
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFFB9C2CF;
    private static final int C_GOLD = 0xFFFFC93C;
    private static final int C_NEUTRAL = 0xFF9AA4B2; // 中立（生物等）
    private static final int C_BORDER = 0x663A465A;  // 面板描边（半透明）
    private static final int C_BORDER_GOLD = 0x66FFC93C;

    // 队伍代码（与服务端 TcpKillFeedS2CPacket 保持一致）
    private static final int TEAM_RED = 0;
    private static final int TEAM_BLUE = 1;
    private static final int TEAM_NONE = 2;

    // 尺寸（v6：整体缩小）
    private static final float HUD_SCALE = 0.8f;     // ← 顶部计分条整体缩放，觉得还大就调小
    private static final int BAR_TOP = 6;            // 计分条距屏幕顶
    private static final int ROW1_H = 20;            // 第一行（竖块+头像+比分）
    private static final int ROW_TARGET_H = 12;      // 第二行（目标 小字）
    private static final int ROW_TIME_H = 14;        // 第三行（时间）
    private static final float TARGET_FONT_SCALE = 0.75f;   // 目标那行的小字比例
    private static final int EDGE_BAR_W = 3;         // 左右竖块宽度
    private static final int PAD = 6;                // 段间距
    private static final int AVATAR = 11;            // 头像尺寸
    private static final int AVATAR_GAP = 2;         // 头像间距
    private static final int MAX_AVATARS = 10;       // 一队最多画几个头像（防爆宽）
    private static final boolean SHOW_COUNTDOWN_TIP = true;
    private static final float COUNTDOWN_SCALE = 3.0f;

    private static final int FEED_TOP = 44;          // 击杀提示起始 Y
    private static final int FEED_H = 18;            // 单条高度（击杀区未改）
    private static final int FEED_GAP = 2;
    private static final int FEED_BAR_W = 3;

    @Override
    public void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        // 可选：进对局后隐藏原版血条/饥饿（模板默认不动）
    }

    @Override
    public void onSpectatorRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        renderAll(g, screenWidth, screenHeight);
    }

    @Override
    public void onPlayerRender(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        renderAll(g, screenWidth, screenHeight);
    }

    private void renderAll(GuiGraphics g, int screenWidth, int screenHeight) {
        if (!TcpHudData.active()) return;
        Font font = Minecraft.getInstance().font;

        drawScoreBar(g, font, screenWidth);
        if (TcpHudData.showKillFeed()) {
            drawKillFeed(g, font, screenWidth, screenHeight);
        }
        drawCountdown(g, font, screenWidth, screenHeight);
        drawEndPanel(g, font, screenWidth, screenHeight);
    }

    // ==================== 顶部计分条（三行） ====================

    private void drawScoreBar(GuiGraphics g, Font font, int screenWidth) {
        List<TcpHudData.RosterEntry> blues = TcpHudData.blueRoster();
        List<TcpHudData.RosterEntry> reds = TcpHudData.redRoster();
        boolean avatars = TcpHudData.showAvatars();

        String blueScore = String.valueOf(TcpHudData.blue());
        String redScore = String.valueOf(TcpHudData.red());
        String sep = " : ";

        int scoreBlockW = font.width(blueScore) + font.width(sep) + font.width(redScore);
        int blueGroupW = avatars ? groupWidth(blues.size()) : 0;
        int redGroupW = avatars ? groupWidth(reds.size()) : 0;

        int total = EDGE_BAR_W + PAD + blueGroupW + PAD + scoreBlockW + PAD + redGroupW + PAD + EDGE_BAR_W;
        int half = total / 2;
        int contentH = ROW1_H + ROW_TARGET_H + ROW_TIME_H;

        // 整体缩放 + 以屏幕中线为原点
        g.pose().pushPose();
        g.pose().translate((float) screenWidth / 2f, (float) BAR_TOP, 0f);
        g.pose().scale(HUD_SCALE, HUD_SCALE, 1f);

        // v10：圆角面板
        UiDraw.panel(g, -half, -2, total, contentH + 2, UiDraw.RADIUS_MEDIUM, C_BORDER, C_BG);

        int row1Cy = ROW1_H / 2;
        int cur = -half;

        // 左：蓝色竖块 + 蓝队头像组
        UiDraw.fillRounded(g, cur, -1, EDGE_BAR_W, contentH, 2, C_BLUE);
        cur += EDGE_BAR_W + PAD;
        if (avatars) {
            drawAvatarGroup(g, blues, cur, row1Cy, C_BLUE);
            cur += blueGroupW + PAD;
        }

        // 中间：蓝分 : 红分
        int textY = row1Cy - 4;
        g.drawString(font, blueScore, cur, textY, C_BLUE, true);
        cur += font.width(blueScore);
        g.drawString(font, sep, cur, textY, C_DIM, true);
        cur += font.width(sep);
        g.drawString(font, redScore, cur, textY, C_RED, true);
        cur += font.width(redScore) + PAD;

        // 右：红队头像组 + 红色竖块
        if (avatars) {
            drawAvatarGroup(g, reds, cur, row1Cy, C_RED);
            cur += redGroupW + PAD;
        }
        UiDraw.fillRounded(g, cur, -1, EDGE_BAR_W, contentH, 2, C_RED);

        // 第二行：目标：30（小字，居中）
        String targetText = "目标：" + TcpHudData.target();
        g.pose().pushPose();
        g.pose().translate(0f, (float) ROW1_H + 1f, 0f);
        g.pose().scale(TARGET_FONT_SCALE, TARGET_FONT_SCALE, 1f);
        g.drawString(font, targetText, -font.width(targetText) / 2, 0, C_GOLD, true);
        g.pose().popPose();

        // 第三行：时间（冒号严格对齐屏幕中线 = 这里的 x=0）
        drawCenteredColonTime(g, font, 0, ROW1_H + ROW_TARGET_H + 2);

        g.pose().popPose();
    }

    /** 时间「MM:SS」画成冒号正对 centerX */
    private void drawCenteredColonTime(GuiGraphics g, Font font, int centerX, int y) {
        String[] parts = TcpHudData.timeParts(TcpHudData.displaySeconds());
        String mm = parts[0];
        String ss = parts[1];
        String colon = ":";
        int colonW = font.width(colon);

        int colonX = centerX - colonW / 2;
        g.drawString(font, mm, colonX - font.width(mm), y, C_TEXT, true);
        g.drawString(font, colon, colonX, y, C_DIM, true);
        g.drawString(font, ss, colonX + colonW, y, C_TEXT, true);
    }

    private int groupWidth(int count) {
        int n = Math.min(count, MAX_AVATARS);
        if (n <= 0) return 0;
        return n * AVATAR + (n - 1) * AVATAR_GAP;
    }

    private void drawAvatarGroup(GuiGraphics g, List<TcpHudData.RosterEntry> entries,
                                 int x, int centerY, int teamColor) {
        int ay = centerY - AVATAR / 2;
        int n = Math.min(entries.size(), MAX_AVATARS);
        for (int i = 0; i < n; i++) {
            TcpHudData.RosterEntry e = entries.get(i);
            int ax = x + i * (AVATAR + AVATAR_GAP);
            g.fill(ax - 1, ay - 1, ax + AVATAR + 1, ay + AVATAR + 1, teamColor);
            PlayerFaceRenderer.draw(g, skinOf(e.id()), ax, ay, AVATAR);
        }
    }

    private static ResourceLocation skinOf(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getSkinLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(id);
    }

    // ==================== 开局前倒计时 ====================

    private void drawCountdown(GuiGraphics g, Font font, int screenWidth, int screenHeight) {
        int cd = TcpHudData.countdown();
        if (cd <= 0) return;

        String num = String.valueOf(cd);
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 3;

        g.pose().pushPose();
        g.pose().translate((float) centerX, (float) centerY, 0f);
        g.pose().scale(COUNTDOWN_SCALE, COUNTDOWN_SCALE, 1f);
        g.drawString(font, num, -font.width(num) / 2, -4, C_GOLD, true);
        g.pose().popPose();

        if (SHOW_COUNTDOWN_TIP) {
            String tip = "对局即将开始";
            g.drawString(font, tip, centerX - font.width(tip) / 2,
                    centerY + (int) (8 * COUNTDOWN_SCALE) + 2, C_TEXT, true);
        }
    }

    // ==================== 击杀提示（未改） ====================

    // ==================== 对局结束面板（MVP / SVP） ====================

    private void drawEndPanel(GuiGraphics g, Font font, int screenWidth, int screenHeight) {
        TcpHudData.MatchEnd e = TcpHudData.matchEnd();
        if (e == null) return;

        int w = 330;
        int h = 132;
        int x = (screenWidth - w) / 2;
        int y = (screenHeight - h) / 2;

        g.fill(x, y, x + w, y + h, 0xE0101620);
        g.fill(x, y, x + w, y + 2, C_GOLD);

        String title = "对 局 结 束";
        g.drawString(font, title, x + (w - font.width(title)) / 2, y + 8, C_GOLD, true);

        // 比分
        String redS = String.valueOf(e.redScore());
        String blueS = String.valueOf(e.blueScore());
        String sep = "  :  ";
        int scoreW = font.width(redS) + font.width(sep) + font.width(blueS);
        int sx = x + (w - scoreW) / 2;
        int sy = y + 22;
        g.drawString(font, redS, sx, sy, C_RED, true);
        g.drawString(font, sep, sx + font.width(redS), sy, C_DIM, true);
        g.drawString(font, blueS, sx + font.width(redS) + font.width(sep), sy, C_BLUE, true);

        // MVP（胜者最佳） / SVP（败者最佳）
        drawAwardBlock(g, font, x + 16, y + 40, "MVP", e.mvp(), C_GOLD);
        drawAwardBlock(g, font, x + w / 2 + 6, y + 40, "SVP", e.svp(), 0xFFCFD8E3);

        // 底部提示
        int left = TcpHudData.matchEndSecondsLeft();
        String hint = left + " 秒后传送回出生点";
        g.drawString(font, hint, x + (w - font.width(hint)) / 2, y + h - 14, C_DIM, true);
    }

    private void drawAwardBlock(GuiGraphics g, Font font, int x, int y, String tag,
                               TcpMatchEndS2CPacket.PlayerLine line, int tagColor) {
        g.drawString(font, tag, x, y, tagColor, true);
        if (line == null) {
            g.drawString(font, "—", x, y + 14, C_DIM, true);
            return;
        }
        int color = teamColor(line.team());
        UUID id = TcpHudData.findIdByName(line.name());
        int headSize = 20;
        if (id != null) {
            g.fill(x - 1, y + 15, x + headSize + 1, y + 15 + headSize + 2, color);
            PlayerFaceRenderer.draw(g, skinOf(id), x, y + 16, headSize);
        }
        int tx = x + headSize + 6;
        g.drawString(font, line.name(), tx, y + 16, color, true);
        g.drawString(font, "击杀 " + line.kills() + "  死亡 " + line.deaths() + "  助攻 " + line.assists(),
                tx, y + 30, C_TEXT, true);
        g.drawString(font, "总伤害 " + line.damage(), tx, y + 42, C_DIM, true);
    }

    private void drawKillFeed(GuiGraphics g, Font font, int screenWidth, int screenHeight) {
        List<TcpHudData.KillLine> feed = TcpHudData.feed();
        if (feed.isEmpty()) return;

        int right = screenWidth - 8;
        int y = FEED_TOP;
        for (TcpHudData.KillLine line : feed) {
            String killer = line.killer();
            String victim = line.victim();
            ItemStack weapon = line.weapon();
            int killerColor = teamColor(line.killerTeam());
            int victimColor = line.victimTeam() == TEAM_NONE ? C_NEUTRAL : teamColor(line.victimTeam());

            int killerW = font.width(killer);
            int victimW = font.width(victim);
            int hasIcon = weapon.isEmpty() ? 0 : FEED_H;
            int w = FEED_BAR_W + 4 + killerW + 3 + hasIcon + 3 + victimW + 6;
            int x = right - w;

            if (y + FEED_H > screenHeight - 4) break;

            g.fill(x, y, right, y + FEED_H, C_BG);
            g.fill(x, y, x + FEED_BAR_W, y + FEED_H, killerColor);

            int textY = y + (FEED_H - 8) / 2;
            int cur = x + FEED_BAR_W + 4;
            g.drawString(font, killer, cur, textY, killerColor, true);
            cur += killerW + 3;
            if (!weapon.isEmpty()) {
                g.renderItem(weapon, cur, y + 1);
                cur += FEED_H;
            }
            g.drawString(font, victim, cur + 3, textY, victimColor, true);

            y += FEED_H + FEED_GAP;
        }
    }

    private static int teamColor(int team) {
        return switch (team) {
            case TEAM_RED -> C_RED;
            case TEAM_BLUE -> C_BLUE;
            default -> C_NEUTRAL;
        };
    }
}
