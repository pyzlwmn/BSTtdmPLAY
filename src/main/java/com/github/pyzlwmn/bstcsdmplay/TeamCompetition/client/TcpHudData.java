package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpScoreS2CPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpMatchEndS2CPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * 客户端缓存：HUD 画什么全看这里
 * （服务端通过 TcpScoreS2CPacket / TcpKillFeedS2CPacket 推过来，客户端只读）
 */
@OnlyIn(Dist.CLIENT)
public final class TcpHudData {

    /** 收到最后一个包的时间，超过 3 秒没新包就认为不在局内 → HUD 自动隐藏 */
    private static final long TIMEOUT_MS = 3000L;
    /** 击杀提示保留时长 */
    private static final long FEED_LIFE_MS = 5000L;
    /** 击杀提示最多留几条 */
    private static final int FEED_MAX = 5;

    /** 名单条目（画头像用） */
    public record RosterEntry(UUID id, String name) {
    }

    /** 一条击杀提示 */
    public record KillLine(String killer, int killerTeam, String victim, int victimTeam,
                           ItemStack weapon, long at) {
    }

    /** 对局结束面板数据（MVP / SVP） */
    public record MatchEnd(int winnerTeam, int redScore, int blueScore,
                           TcpMatchEndS2CPacket.PlayerLine mvp,
                           TcpMatchEndS2CPacket.PlayerLine svp,
                           int holdSeconds, long at) {
    }

    private static int red = 0;
    private static int blue = 0;
    private static int target = 30;
    private static int seconds = 0;
    private static int timeLimit = 0;
    private static int countdown = 0;
    private static long countdownAt = 0L;
    private static int flags = TcpScoreS2CPacket.FLAG_KILL_FEED | TcpScoreS2CPacket.FLAG_AVATARS;
    private static long lastUpdate = 0L;

    private static List<RosterEntry> redRoster = List.of();
    private static List<RosterEntry> blueRoster = List.of();

    private static final Deque<KillLine> FEED = new ArrayDeque<>();

    private static MatchEnd matchEnd = null;

    private TcpHudData() {
    }

    /** 比分 / 时长 / 倒计时 / 开关 / 名单（每 0.5 秒一次） */
    public static void update(int r, int b, int t, int s, int limit, int cd, int f,
                              List<TcpScoreS2CPacket.Roster> roster) {
        red = r;
        blue = b;
        target = t;
        seconds = s;
        timeLimit = limit;
        countdown = cd;
        countdownAt = lastUpdate;
        flags = f;
        lastUpdate = System.currentTimeMillis();
        applyRoster(roster);
    }

    private static void applyRoster(List<TcpScoreS2CPacket.Roster> roster) {
        if (roster == null) return;
        List<RosterEntry> reds = new ArrayList<>();
        List<RosterEntry> blues = new ArrayList<>();
        for (TcpScoreS2CPacket.Roster r : roster) {
            if (r.red()) {
                reds.add(new RosterEntry(r.id(), r.name()));
            } else {
                blues.add(new RosterEntry(r.id(), r.name()));
            }
        }
        redRoster = reds;
        blueRoster = blues;
    }

    /** 收到一条击杀提示（服务端包处理里调） */
    public static void addKill(String killer, int killerTeam, String victim, int victimTeam, ItemStack weapon) {
        lastUpdate = System.currentTimeMillis();
        FEED.addLast(new KillLine(killer, killerTeam, victim, victimTeam,
                weapon == null ? ItemStack.EMPTY : weapon, lastUpdate));
        while (FEED.size() > FEED_MAX) FEED.removeFirst();
    }

    /** 收到对局结束面板（MVP / SVP） */
    public static void setMatchEnd(int winnerTeam, int redScore, int blueScore,
                                   TcpMatchEndS2CPacket.PlayerLine mvp,
                                   TcpMatchEndS2CPacket.PlayerLine svp,
                                   int holdSeconds) {
        long now = System.currentTimeMillis();
        matchEnd = new MatchEnd(winnerTeam, redScore, blueScore, mvp, svp, holdSeconds, now);
        lastUpdate = now;
    }

    /** 当前要显示的对局结束面板（过了 holdSeconds 就返回 null） */
    public static MatchEnd matchEnd() {
        if (matchEnd == null) return null;
        long life = (matchEnd.holdSeconds() + 1L) * 1000L;
        if (System.currentTimeMillis() - matchEnd.at() > life) return null;
        return matchEnd;
    }

    /** 结束面板还能显示几秒（拿来写"X 秒后传送"） */
    public static int matchEndSecondsLeft() {
        MatchEnd e = matchEnd();
        if (e == null) return 0;
        long leftMs = (e.holdSeconds() * 1000L) - (System.currentTimeMillis() - e.at());
        return (int) Math.max(0, (leftMs + 999) / 1000);
    }

    public static boolean active() {
        return System.currentTimeMillis() - lastUpdate < TIMEOUT_MS;
    }

    public static int red() { return red; }

    public static int blue() { return blue; }

    public static int target() { return target; }

    public static int seconds() { return seconds; }

    public static int timeLimit() { return timeLimit; }

    /** 倒计时秒数；超过 1.5 秒没收到新包就当没有（防止服务端停推后文字卡在屏幕上） */
    public static int countdown() {
        if (isPlaying() || postMatch()) return 0;   // 对局中/结算中 → 倒计时文字必须消失
        if (countdown <= 0) return 0;
        if (System.currentTimeMillis() - countdownAt > 1500L) return 0;
        return countdown;
    }

    /** 服务端说"对局已结束，正在显示 MVP/SVP 面板" */
    public static boolean postMatch() {
        return (flags & TcpScoreS2CPacket.FLAG_POST_MATCH) != 0;
    }

    public static boolean showKillFeed() { return (flags & TcpScoreS2CPacket.FLAG_KILL_FEED) != 0; }

    public static boolean showAvatars() { return (flags & TcpScoreS2CPacket.FLAG_AVATARS) != 0; }

    /** 服务端说"对局进行中" */
    public static boolean isPlaying() { return (flags & TcpScoreS2CPacket.FLAG_PLAYING) != 0; }

    public static List<RosterEntry> redRoster() { return redRoster; }

    public static List<RosterEntry> blueRoster() { return blueRoster; }

    /** 按玩家名在名单里找 UUID（结束面板画头像用） */
    public static UUID findIdByName(String name) {
        if (name == null) return null;
        for (RosterEntry e : redRoster) {
            if (name.equals(e.name())) return e.id();
        }
        for (RosterEntry e : blueRoster) {
            if (name.equals(e.name())) return e.id();
        }
        return null;
    }

    /** HUD 上要显示的时间：有对局时限就显示剩余，否则显示已用时 */
    public static int displaySeconds() {
        if (timeLimit > 0) {
            return Math.max(0, timeLimit - seconds);
        }
        return seconds;
    }

    /** 取当前还没过期的击杀提示（新→旧） */
    public static List<KillLine> feed() {
        long now = System.currentTimeMillis();
        List<KillLine> out = new ArrayList<>();
        for (KillLine line : FEED) {
            if (now - line.at() < FEED_LIFE_MS) out.add(line);
        }
        Collections.reverse(out);   // 新的排前面
        return out;
    }

    /** 退出对局 / 换服时清一下 */
    public static void clear() {
        red = blue = 0;
        seconds = 0;
        countdown = 0;
        countdownAt = 0L;
        lastUpdate = 0L;
        redRoster = List.of();
        blueRoster = List.of();
        FEED.clear();
        matchEnd = null;
    }

    /** MM:SS */
    public static String formatTime(int sec) {
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    /** 只要分和秒两段（倒计时数要单独排版让冒号居中） */
    public static String[] timeParts(int sec) {
        return new String[]{String.format("%02d", sec / 60), String.format("%02d", sec % 60)};
    }
}
