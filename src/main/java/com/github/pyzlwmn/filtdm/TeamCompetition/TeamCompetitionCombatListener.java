package com.github.pyzlwmn.filtdm.TeamCompetition;

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

@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamCompetitionCombatListener {

    private TeamCompetitionCombatListener() {
    }

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

    @SubscribeEvent
    public static void onPlayerKill(FPSMapEvent.PlayerEvent.KillEvent event) {
        if (!(event.getMap() instanceof TeamCompetitionMap map)) return;
        if (!map.isStart()) return;

        ServerPlayer killer = event.getPlayer();
        ServerPlayer dead = event.getDead();

        Optional<ServerTeam> killerTeam = map.getMapTeams().getTeamByPlayer(killer);
        Optional<ServerTeam> deadTeam = map.getMapTeams().getTeamByPlayer(dead);
        if (killerTeam.isEmpty()) return;

        if (deadTeam.isPresent() && deadTeam.get() == killerTeam.get()) {
            map.msg("§7[误伤] " + dead.getName().getString()
                    + " 被队友 " + killer.getName().getString() + " 击杀（不计分）");
            return;
        }

        map.sendKillFeed(killer, dead,
                "§c☠ " + killer.getName().getString()
                        + (event.isHeadshot() ? " §6[爆头]" : "")
                        + " §7击杀了 §f" + dead.getName().getString()
                        + " §7(" + killerTeam.get().getFixedName() + " "
                        + map.teamKills(killerTeam.get()) + "/" + map.getMapTeams().getOnline().size() + ")");
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer dead) {

            TeamCompetitionMap tcp = mapOf(dead);
            if (tcp != null && tcp.isStart()) {
                tcp.markPendingRespawn(dead);
            }
            return;
        }
        if (victim.level().isClientSide()) return;

        ServerPlayer killer = null;
        if (event.getSource().getEntity() instanceof ServerPlayer sp) {
            killer = sp;
        } else if (victim.getKillCredit() instanceof ServerPlayer credit) {
            killer = credit;
        }
        if (killer == null) return;

        Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(killer);
        if (opt.isEmpty() || !(opt.get() instanceof TeamCompetitionMap map) || !map.isStart()) return;

        if (map.isMobKillScoreEnabled()) {
            map.addPlayerKill(killer);
        }
        map.sendKillFeed(killer, victim,
                "§7☠ " + killer.getName().getString()
                        + " 击杀了 " + victim.getName().getString()
                        + (map.isMobKillScoreEnabled() ? " §7(+1)" : ""));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        TeamCompetitionMap tcp = mapOf(player);
        if (tcp != null && tcp.isStart()) {
            tcp.markPendingRespawn(player);
        }
    }

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
