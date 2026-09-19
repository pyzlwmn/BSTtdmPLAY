package com.github.pyzlwmn.bstcsdmplay.TeamCompetition;

import net.ptcrys.fpsmatch.common.event.FPSMapEvent;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.map.BaseMap;
import net.ptcrys.fpsmatch.core.team.ServerTeam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * 团队竞技 · 战斗/重生监听
 *
 * ① 玩家击杀玩家 → 只推 HUD 击杀提示（FPSMatch 死亡管线已自动记 kills）
 * ② 击杀生物     → 加分 + HUD 击杀提示（由 combat/mobKillScore 设置控制）
 * ③ 玩家重生     → 送回本队出生点 + 进入重生保护
 * ④ 重生保护中   → 免疫伤害
 *
 * ⚠️ modid 改成你自己的（下面按 bstcsdmplay 写的）
 */
@Mod.EventBusSubscriber(modid = "bstcsdmplay", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamCompetitionCombatListener {

    private TeamCompetitionCombatListener() {
    }

    /**
     * 找玩家所在的本玩法地图：先走 FPSMatch 的缓存，缓存没有就遍历我们的地图兜底
     * （缓存有时会在死亡/换实例的瞬间查不到，导致重生逻辑整段不生效）
     */
    private static TeamCompetitionMap mapOf(ServerPlayer player) {
        Optional<BaseMap> cached = FPSMCore.getInstance().getMapByPlayer(player);
        if (cached.isPresent() && cached.get() instanceof TeamCompetitionMap tcp) {
            return tcp;
        }
        for (TeamCompetitionMap map : FPSMCore.getInstance().getMapByClass(TeamCompetitionMap.class)) {
            if (map.checkGameHasPlayer(player) || map.isStart()) {
                return map;
            }
        }
        return null;
    }

    /** ① 玩家击杀玩家 */
    @SubscribeEvent
    public static void onPlayerKill(FPSMapEvent.PlayerEvent.KillEvent event) {
        if (!(event.getMap() instanceof TeamCompetitionMap map)) return;
        if (!map.isStart()) return;

        ServerPlayer killer = event.getPlayer();
        ServerPlayer dead = event.getDead();

        Optional<ServerTeam> killerTeam = map.getMapTeams().getTeamByPlayer(killer);
        Optional<ServerTeam> deadTeam = map.getMapTeams().getTeamByPlayer(dead);
        if (killerTeam.isEmpty()) return;

        if (deadTeam.isPresent() && deadTeam.get() == killerTeam.get()) {   // 队友误伤不计分
            map.msg("§7[误伤] " + dead.getName().getString()
                    + " 被队友 " + killer.getName().getString() + " 击杀（不计分）");
            return;
        }

        // HUD 击杀提示：▌击杀者 [枪包图标] 被击杀者（图标取击杀时手里的物品）
        map.sendKillFeed(killer, dead,
                "§c☠ " + killer.getName().getString()
                        + (event.isHeadshot() ? " §6[爆头]" : "")
                        + " §7击杀了 §f" + dead.getName().getString()
                        + " §7(" + killerTeam.get().getFixedName() + " "
                        + map.teamKills(killerTeam.get()) + "/" + map.getMapTeams().getOnline().size() + ")");
    }

    /** ② 击杀生物：加分 + 击杀提示（可用 combat/mobKillScore 关掉加分） */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer dead) {
            // 玩家死亡：先标记「重生后送回出生点」
            // （PlayerRespawnEvent 在某些情况下不够可靠，这里再兜一层；标记是幂等的）
            TeamCompetitionMap tcp = mapOf(dead);
            if (tcp != null && tcp.isStart()) {
                tcp.markPendingRespawn(dead);
            }
            return;                                      // 玩家走 ① 的击杀逻辑，避免重复计分
        }
        if (victim.level().isClientSide()) return;

        ServerPlayer killer = null;
        if (event.getSource().getEntity() instanceof ServerPlayer sp) {
            killer = sp;
        } else if (victim.getKillCredit() instanceof ServerPlayer credit) {
            killer = credit;                             // 枪械/投掷物兜底
        }
        if (killer == null) return;

        Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(killer);
        if (opt.isEmpty() || !(opt.get() instanceof TeamCompetitionMap map) || !map.isStart()) return;

        if (map.isMobKillScoreEnabled()) {
            map.addPlayerKill(killer);                   // 写进 PlayerData.kills，和胜利判定同口径
        }
        map.sendKillFeed(killer, victim,
                "§7☠ " + killer.getName().getString()
                        + " 击杀了 " + victim.getName().getString()
                        + (map.isMobKillScoreEnabled() ? " §7(+1)" : ""));
    }

    /**
     * ③ 玩家重生：标记「下一 tick 送回本队出生点」+ 上重生保护
     *
     * ⚠️ v9 修复：不要在这里直接传送 —— 原版在 PlayerRespawnEvent 之后还会再落一次位置，
     *    直接传送会被覆盖（表现就是"被杀了不会回到重生点"）。交给地图 tick() 里延迟 1 tick 传送。
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        TeamCompetitionMap tcp = mapOf(player);
        if (tcp != null && tcp.isStart()) {
            tcp.markPendingRespawn(player);
        }
    }

    /**
     * ④ 免疫伤害：
     *    - 对局未开始（含开局前倒计时阶段）→ 全员免疫（防倒计时里互刷）
     *    - 对局中且处于重生保护期 → 免疫
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        TeamCompetitionMap tcp = mapOf(player);
        if (tcp != null && (!tcp.isStart() || tcp.isProtected(player.getUUID()))) {
            event.setCanceled(true);
        }
    }
}
