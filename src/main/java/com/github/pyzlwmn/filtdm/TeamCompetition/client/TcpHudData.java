package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpScoreS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpMatchEndS2CPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class TcpHudData {

    private static final long TIMEOUT_MS = 3000L;

    private static final long FEED_LIFE_MS = 5000L;

    private static final int FEED_MAX = 5;

    public record RosterEntry(UUID id, String name) {
    }

    public record KillLine(String killer, int killerTeam, String victim, int victimTeam,
                           ItemStack weapon, long at) {
    }

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

    public static void addKill(String killer, int killerTeam, String victim, int victimTeam, ItemStack weapon) {
        lastUpdate = System.currentTimeMillis();
        FEED.addLast(new KillLine(killer, killerTeam, victim, victimTeam,
                weapon == null ? ItemStack.EMPTY : weapon, lastUpdate));
        while (FEED.size() > FEED_MAX) FEED.removeFirst();
    }

    public static void setMatchEnd(int winnerTeam, int redScore, int blueScore,
                                   TcpMatchEndS2CPacket.PlayerLine mvp,
                                   TcpMatchEndS2CPacket.PlayerLine svp,
                                   int holdSeconds) {
        long now = System.currentTimeMillis();
        matchEnd = new MatchEnd(winnerTeam, redScore, blueScore, mvp, svp, holdSeconds, now);
        lastUpdate = now;
    }

    public static MatchEnd matchEnd() {
        if (matchEnd == null) return null;
        long life = (matchEnd.holdSeconds() + 1L) * 1000L;
        if (System.currentTimeMillis() - matchEnd.at() > life) return null;
        return matchEnd;
    }

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

    public static int countdown() {
        if (isPlaying() || postMatch()) return 0;
        if (countdown <= 0) return 0;
        if (System.currentTimeMillis() - countdownAt > 1500L) return 0;
        return countdown;
    }

    public static boolean postMatch() {
        return (flags & TcpScoreS2CPacket.FLAG_POST_MATCH) != 0;
    }

    public static boolean showKillFeed() { return (flags & TcpScoreS2CPacket.FLAG_KILL_FEED) != 0; }

    public static boolean showAvatars() { return (flags & TcpScoreS2CPacket.FLAG_AVATARS) != 0; }

    public static boolean isPlaying() { return (flags & TcpScoreS2CPacket.FLAG_PLAYING) != 0; }

    public static List<RosterEntry> redRoster() { return redRoster; }

    public static List<RosterEntry> blueRoster() { return blueRoster; }

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

    public static int displaySeconds() {
        if (timeLimit > 0) {
            return Math.max(0, timeLimit - seconds);
        }
        return seconds;
    }

    public static List<KillLine> feed() {
        long now = System.currentTimeMillis();
        List<KillLine> out = new ArrayList<>();
        for (KillLine line : FEED) {
            if (now - line.at() < FEED_LIFE_MS) out.add(line);
        }
        Collections.reverse(out);
        return out;
    }

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

    public static String formatTime(int sec) {
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    public static String[] timeParts(int sec) {
        return new String[]{String.format("%02d", sec / 60), String.format("%02d", sec % 60)};
    }
}
