package com.github.pyzlwmn.filtdm.TeamCompetition;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpKillFeedS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpMatchEndS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpScoreS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.LoadoutManager;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.RefitSession;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.capability.CapabilityMap;
import net.ptcrys.fpsmatch.core.data.AreaData;
import net.ptcrys.fpsmatch.core.data.PlayerData;
import net.ptcrys.fpsmatch.core.data.Setting;
import net.ptcrys.fpsmatch.core.data.SpawnPointData;
import net.ptcrys.fpsmatch.core.map.BaseMap;
import net.ptcrys.fpsmatch.core.persistence.FPSMDataManager;
import net.ptcrys.fpsmatch.core.team.ServerTeam;
import net.ptcrys.fpsmatch.core.team.TeamData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamCompetitionMap extends BaseMap {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-TDM");

    public static final String TYPE = "tdm";

    public static final Codec<TeamCompetitionMap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("mapName").forGetter(TeamCompetitionMap::getMapName),
            AreaData.CODEC.fieldOf("mapArea").forGetter(TeamCompetitionMap::getMapArea),
            ResourceLocation.CODEC.fieldOf("serverLevel")
                    .forGetter(map -> map.getServerLevel().dimension().location()),
            CapabilityMap.Wrapper.DATA_CODEC.fieldOf("capabilities")
                    .forGetter(map -> map.getCapabilityMap().getData().data()),
            Codec.unboundedMap(Codec.STRING, CapabilityMap.Wrapper.CODEC)
                    .fieldOf("teams").forGetter(map -> map.getMapTeams().getData())
    ).apply(instance, TeamCompetitionMap::new));

    public static void save(FPSMDataManager manager) {
        FPSMCore.getInstance().getMapByClass(TeamCompetitionMap.class)
                .forEach(map -> {
                    map.saveConfig();
                    manager.saveData(map, map.getMapName(), false);
                });
    }


    private final Setting<Integer> teamLimit;

    private final Setting<Integer> targetKills;

    private final Setting<Integer> countdownSeconds;

    private final Setting<Integer> readyCountdownSeconds;

    private final Setting<Boolean> autoStartWhenFull;

    private final Setting<Integer> matchTimeLimit;

    private final Setting<Integer> spawnProtectionSeconds;

    private final Setting<Boolean> immediateRespawn;

    private final Setting<Integer> respawnDelaySeconds;

    private final Setting<Boolean> friendlyFire;

    private final Setting<Boolean> mobKillScore;

    private final Setting<Boolean> chatMessages;

    private final Setting<Boolean> showKillFeed;

    private final Setting<Boolean> showAvatars;


    private final Setting<String> lobbyDim;

    private final Setting<Integer> lobbyX;
    private final Setting<Integer> lobbyY;
    private final Setting<Integer> lobbyZ;
    private final Setting<Integer> lobbyYaw;

    private final Setting<Integer> mvpHoldSeconds;


    private final Setting<Boolean> loadoutEnabled;

    private final Setting<Boolean> loadoutClearOnStart;

    private final Setting<Boolean> loadoutRefillOnRespawn;

    private final Setting<Boolean> loadoutAllowEditInMatch;

    private final Setting<Boolean> loadoutAllowSwitchInMatch;

    private static final int DEFAULT_SPAWN_PER_TEAM = 3;

    private ServerTeam redTeam;
    private ServerTeam blueTeam;
    private boolean gameEnded = false;
    private boolean cleaningUp = false;

    private int preStartTicks = 0;
    private boolean preStartRunning = false;

    private boolean postMatch = false;
    private int postMatchTicks = 0;

    private boolean internalStarting = false;

    private boolean settingsSanitized = false;

    private final Map<UUID, Integer> protectUntilTick = new HashMap<>();

    private final Map<UUID, Integer> pendingRespawn = new HashMap<>();

    private final Map<UUID, Integer> respawnUnlockTick = new HashMap<>();

    private final Set<UUID> deathFrozen = new HashSet<>();

    private Boolean prevImmediateRespawn = null;

    private Boolean prevKeepInventory = null;

    private final Set<UUID> loadoutGiven = new HashSet<>();

    public TeamCompetitionMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);

        this.teamLimit = addSetting("team", "teamLimit", 5);
        this.targetKills = addSetting("match", "targetKills", 30);
        this.countdownSeconds = addSetting("match", "countdownSeconds", 10);
        this.readyCountdownSeconds = addSetting("match", "readyCountdownSeconds", 1);

        this.autoStartWhenFull = addSetting("match", "autoStartWhenFull", false);
        this.matchTimeLimit = addSetting("match", "matchTimeLimit", 300);
        this.spawnProtectionSeconds = addSetting("player", "spawnProtectionSeconds", 3);
        this.immediateRespawn = addSetting("player", "immediateRespawn", true);
        this.respawnDelaySeconds = addSetting("player", "respawnDelaySeconds", 5);
        this.friendlyFire = addSetting("combat", "friendlyFire", false);
        this.mobKillScore = addSetting("combat", "mobKillScore", true);
        this.chatMessages = addSetting("display", "chatMessages", false);
        this.showKillFeed = addSetting("display", "showKillFeed", true);
        this.showAvatars = addSetting("display", "showAvatars", true);

        this.lobbyDim = addSetting("lobby", "dim", "minecraft:overworld");
        this.lobbyX = addSetting("lobby", "x", 0);
        this.lobbyY = addSetting("lobby", "y", 64);
        this.lobbyZ = addSetting("lobby", "z", 0);
        this.lobbyYaw = addSetting("lobby", "yaw", 0);
        this.mvpHoldSeconds = addSetting("match", "mvpHoldSeconds", 6);

        this.loadoutEnabled = addSetting("loadout", "enabled", true);
        this.loadoutClearOnStart = addSetting("loadout", "clearOnStart", true);
        this.loadoutRefillOnRespawn = addSetting("loadout", "refillOnRespawn", true);
        this.loadoutAllowEditInMatch = addSetting("loadout", "allowEditInMatch", true);
        this.loadoutAllowSwitchInMatch = addSetting("loadout", "allowSwitchInMatch", true);

        this.autoStart.set(false);



        this.readyStartEnabled.set(true);
        this.readyStartTime.set(20);

        this.redTeam = addTeam(TeamData.of("red", teamLimit.get(), List.of(SpawnPointCapability.class)));
        this.blueTeam = addTeam(TeamData.of("blue", teamLimit.get(), List.of(SpawnPointCapability.class)));

        this.redTeam.setColor(new Vector3f(1.0f, 0.25f, 0.25f));
        this.blueTeam.setColor(new Vector3f(0.30f, 0.55f, 1.0f));
        this.redTeam.getPlayerTeam().setColor(ChatFormatting.RED);
        this.blueTeam.getPlayerTeam().setColor(ChatFormatting.BLUE);

        defineDefaultSpawnPoints();
        applyFriendlyFire();
    }

    private TeamCompetitionMap(String mapName, AreaData areaData, ResourceLocation serverLevel,
                               Map<String, JsonElement> capabilities,
                               Map<String, CapabilityMap.Wrapper> teams) {
        this(FPSMCore.getInstance().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, serverLevel)),
                mapName, areaData);
        this.getCapabilityMap().write(capabilities);
        this.getMapTeams().writeData(teams);
    }

    @Override
    public String getGameType() {
        return TYPE;
    }

    private void defineDefaultSpawnPoints() {
        ensureDefaultSpawnPoints(redTeam, true);
        ensureDefaultSpawnPoints(blueTeam, false);
    }

    private void ensureDefaultSpawnPoints(ServerTeam team, boolean red) {
        if (team == null) return;
        team.getCapabilityMap().get(SpawnPointCapability.class).ifPresent(cap -> {
            if (!cap.getSpawnPointsData().isEmpty()) return;

            AABB box = getMapArea().aabb();
            double x = red
                    ? box.minX + (box.maxX - box.minX) * 0.2
                    : box.minX + (box.maxX - box.minX) * 0.8;
            float yaw = red ? -90f : 90f;

            for (int i = 0; i < DEFAULT_SPAWN_PER_TEAM; i++) {
                double t = 0.3 + 0.2 * i;
                double z = box.minZ + (box.maxZ - box.minZ) * t;

                BlockPos base = BlockPos.containing(x, box.minY, z);
                BlockPos top = getServerLevel()
                        .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
                Vec3 pos = new Vec3(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                cap.addSpawnPointData(new SpawnPointData(getServerLevel().dimension(), pos, yaw, 0f));
                LOGGER.info("[TDM] 兜底出生点 {}：{}", red ? "红队" : "蓝队", pos);
            }
        });
    }

    private void addSpawnPointIfEmpty(ServerTeam team, SpawnPointData point) {
        if (team == null) return;
        team.getCapabilityMap().get(SpawnPointCapability.class).ifPresent(cap -> {
            if (cap.getSpawnPointsData().isEmpty()) {
                cap.addSpawnPointData(point);
            }
        });
    }


    @Override
    protected boolean canReadyStart() {
        return onlineCount() >= 1 && allNormalOnlinePlayersReady();
    }

    @Override
    protected boolean canAutoStart() {
        return false;
    }

    public boolean teamsFull() {
        int limit = Math.max(1, teamLimit.get());
        return onlineCount(redTeam) >= limit && onlineCount(blueTeam) >= limit;
    }

    private int onlineCount(ServerTeam team) {
        return team == null ? 0 : team.getOnline().size();
    }

    public int onlineCount() {
        int n = 0;
        for (ServerTeam t : getMapTeams().getNormalTeams()) {
            n += t.getOnline().size();
        }
        return n;
    }

    private int syncTimer = 0;

    private void sanitizeSettings() {
        if (settingsSanitized) return;
        settingsSanitized = true;
        if (readyStartTime.get() < 20) {
            LOGGER.warn("[TDM] readyStartTime={} 太小（会让 FPSMatch 大厅每 tick 空转、客户端准备文字卡住）→ 已修正为 20 tick",
                    readyStartTime.get());
            readyStartTime.set(20);
        }
        if (countdownSeconds.get() < 1) {
            LOGGER.warn("[TDM] countdownSeconds={} 非法 → 已修正为 10", countdownSeconds.get());
            countdownSeconds.set(10);
        }
        if (teamLimit.get() < 1) teamLimit.set(1);
        if (autoStartWhenFull.get()) {
            LOGGER.info("[TDM] 已取消「满人自动开」（v24）→ autoStartWhenFull 强制 false");
            autoStartWhenFull.set(false);
        }
        LOGGER.info("[TDM] 生效设置：开局倒计时={}s / 准备倒计时={}tick / 每队={}人 / 目标={} / 时限={}s / 秒回重生={} / 满员自动={}",
                countdownSeconds.get(), readyStartTime.get(), teamLimit.get(), targetKills.get(),
                matchTimeLimit.get(), immediateRespawn.get(), autoStartWhenFull.get());
    }

    @Override
    public void tick() {
        sanitizeSettings();

        if (postMatch) {
            if (postMatchTicks > 0) {
                postMatchTicks--;
            }
            if (++syncTimer >= 10) {
                syncTimer = 0;
                syncScores();
            }
            if (postMatchTicks <= 0) {
                postMatch = false;
                clearKills(redTeam);
                clearKills(blueTeam);
                sendEveryoneToLobby();
            }
            return;
        }

        if (isStart) {
            scanForDeaths();
            processDeathFreeze();
            processPendingRespawn();
            giveLoadoutToLateJoiners();
            int limit = matchTimeLimit.get();
            if (limit > 0 && getElapsedMatchSeconds() >= limit) {
                victory();
                return;
            }
            if (++syncTimer >= 10) {
                syncTimer = 0;
                syncScores();
            }
            return;
        }

        if (preStartRunning) {
            if (preStartTicks > 0) {
                preStartTicks--;
            }
            if (preStartTicks <= 0) {
                preStartRunning = false;
                preStartTicks = 0;
                beginMatch();
                return;
            }
        } else if (false && autoStartWhenFull.get() && teamsFull()) {
            beginPreStart();
        }

        if (++syncTimer >= 10) {
            syncTimer = 0;
            syncScores();
        }
    }

    public int countdownSecondsLeft() {
        if (!preStartRunning || preStartTicks <= 0) return 0;
        return (preStartTicks + 19) / 20;
    }

    public boolean isPreStarting() {
        return preStartRunning;
    }


    @Override
    protected void startGameWithAnnouncement() {
        if (isStart || preStartRunning) return;
        beginPreStart();
    }

    @Override
    public boolean start() {
        if (internalStarting) {
            return super.start();
        }
        if (!isStart && !preStartRunning) {
            LOGGER.info("[TDM] 收到 start()（可能是 /fpsm ... debug start）→ 进入开局前倒计时");
            beginPreStart();
            return true;
        }
        return super.start();
    }

    private void beginPreStart() {
        if (onlineCount() < 1) return;

        this.gameEnded = false;
        this.cleaningUp = false;
        this.protectUntilTick.clear();
        this.loadoutGiven.clear();
        this.preStartRunning = true;
        this.preStartTicks = Math.max(1, countdownSeconds.get()) * 20;

        for (ServerTeam team : getMapTeams().getNormalTeams()) {
            for (ServerPlayer player : new ArrayList<>(team.getOnline())) {
                teleportPlayerToReSpawnPoint(player);

                LoadoutManager.resetInventory(player);

                healFull(player);
            }
        }

        for (ServerPlayer p : getMapTeams().getOnlineWithSpec()) {
            RefitSession.clearAll("对局开始");
        }

        msg("§e对局即将开始：§c" + countdownSeconds.get() + " §e秒后正式计时（期间不掉血）");
        syncScores();
    }

    private void beginMatch() {
        if (onlineCount() < 1) return;

        super.startGameWithAnnouncement();

        this.isStart = true;
        this.gameEnded = false;
        this.cleaningUp = false;
        this.protectUntilTick.clear();
        applyFriendlyFire();

        applyKeepInventory(true);

        applyImmediateRespawn(respawnDelaySeconds.get() <= 0);

        msg("§6§l==== 对局开始 ====");
        msg("§e目标：" + targetKills.get() + " 击杀 ｜ 时限：" + matchTimeLimit.get() + " 秒");

        LOGGER.info("[TDM] 开局！出生点数量：红队 {} 个 / 蓝队 {} 个",
                spawnPointCount(redTeam), spawnPointCount(blueTeam));
        this.loadoutGiven.clear();
        for (ServerTeam team : getMapTeams().getNormalTeams()) {
            for (ServerPlayer player : new ArrayList<>(team.getOnline())) {
                sendToTeamSpawn(player, false);
                healFull(player);
                applyLoadoutTo(player);
                sendLoadoutPacket(player);
                loadoutGiven.add(player.getUUID());
            }
        }

        syncScores();
    }

    private void applyFriendlyFire() {
        boolean ff = friendlyFire.get();
        if (redTeam != null) redTeam.getPlayerTeam().setAllowFriendlyFire(ff);
        if (blueTeam != null) blueTeam.getPlayerTeam().setAllowFriendlyFire(ff);
    }

    @Override
    public boolean victoryGoal() {
        if (!isStart || gameEnded || cleaningUp) return false;
        int target = targetKills.get();
        if (target <= 0) return false;
        return teamKills(redTeam) >= target || teamKills(blueTeam) >= target;
    }

    @Override
    public void victory() {
        if (gameEnded || cleaningUp) return;
        this.gameEnded = true;

        int red = teamKills(redTeam);
        int blue = teamKills(blueTeam);
        ServerTeam winner = red >= blue ? redTeam : blueTeam;
        ServerTeam loser = winner == redTeam ? blueTeam : redTeam;
        TcpMatchEndS2CPacket.PlayerLine mvp = bestOf(winner);
        TcpMatchEndS2CPacket.PlayerLine svp = bestOf(loser);

        super.victory();

        msg("§6§l==== 对局结束 ====");
        msg("§a胜者：" + winner.getFixedName() + "  §7(红 " + red + " : " + blue + " 蓝)");

        this.isStart = false;
        this.preStartRunning = false;
        this.preStartTicks = 0;

        int hold = Math.max(1, mvpHoldSeconds.get());
        int winnerTeam = red == blue ? TcpMatchEndS2CPacket.TEAM_DRAW
                : (winner == redTeam ? TcpMatchEndS2CPacket.TEAM_RED : TcpMatchEndS2CPacket.TEAM_BLUE);
        TcpMatchEndS2CPacket packet = new TcpMatchEndS2CPacket(winnerTeam, red, blue, mvp, svp, hold);
        for (ServerPlayer p : getMapTeams().getOnlineWithSpec()) {
            TcpNetwork.sendToPlayer(p, packet);
        }

        this.postMatch = true;
        this.postMatchTicks = hold * 20;
        syncScores();
    }

    private TcpMatchEndS2CPacket.PlayerLine bestOf(ServerTeam team) {
        if (team == null) return null;
        PlayerData best = null;
        for (PlayerData d : team.getPlayersData()) {
            if (best == null
                    || d.getKills() > best.getKills()
                    || (d.getKills() == best.getKills() && d.getDamage() > best.getDamage())) {
                best = d;
            }
        }
        if (best == null) return null;
        return new TcpMatchEndS2CPacket.PlayerLine(
                best.name().getString(), teamCode(team),
                best.getKills(), best.getDeaths(), best.getAssists(), Math.round(best.getDamage()));
    }

    public void sendEveryoneToLobby() {
        this.cleaningUp = true;
        try {
            List<ServerPlayer> online = new ArrayList<>(getMapTeams().getOnlineWithSpec());
            ServerLevel lobby = lobbyLevel();

            int lx = lobbyX.get();
            int ly = lobbyY.get();
            int lz = lobbyZ.get();
            float yaw = lobbyYaw.get();
            for (ServerPlayer p : online) {
                p.teleportTo(lobby, lx + 0.5, ly, lz + 0.5, yaw, 0f);
                p.setRespawnPosition(lobby.dimension(), new BlockPos(lx, ly, lz), yaw, true, false);
                msgTo(p, "§7对局结束，已送回出生点");
            }

            for (ServerPlayer p : online) {
                leave(p);
            }
            protectUntilTick.clear();
            pendingRespawn.clear();
            respawnUnlockTick.clear();
            deathFrozen.clear();
            loadoutGiven.clear();
            applyImmediateRespawn(false);
            applyKeepInventory(false);
        } finally {
            this.cleaningUp = false;
        }
    }

    private void applyKeepInventory(boolean on) {
        try {
            GameRules rules = getServerLevel().getGameRules();
            GameRules.BooleanValue rule = rules.getRule(GameRules.RULE_KEEPINVENTORY);
            if (on) {
                if (prevKeepInventory == null) prevKeepInventory = rule.get();
                if (!rule.get()) {
                    rule.set(true, null);
                    LOGGER.info("[TDM] 已开启死亡不掉落（keepInventory=true，原值 {}）", prevKeepInventory);
                }
            } else if (prevKeepInventory != null) {
                if (rule.get() != prevKeepInventory) {
                    rule.set(prevKeepInventory, null);
                    LOGGER.info("[TDM] 已还原死亡掉落规则（keepInventory={}）", prevKeepInventory);
                }
                prevKeepInventory = null;
            }
        } catch (RuntimeException ignored) {

        }
    }

    private void applyImmediateRespawn(boolean on) {
        try {
            GameRules rules = getServerLevel().getGameRules();
            GameRules.BooleanValue rule = rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
            if (on) {
                if (prevImmediateRespawn == null) prevImmediateRespawn = rule.get();
                rule.set(true, null);
            } else if (prevImmediateRespawn != null) {
                rule.set(prevImmediateRespawn, null);
                prevImmediateRespawn = null;
            }
        } catch (RuntimeException ignored) {

        }
    }

    private ServerLevel lobbyLevel() {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(lobbyDim.get());
            if (rl != null) {
                ServerLevel level = getServerLevel().getServer()
                        .getLevel(ResourceKey.create(Registries.DIMENSION, rl));
                if (level != null) return level;
            }
        } catch (RuntimeException ignored) {

        }
        return getServerLevel();
    }

    public void respawnAtSpawnPoint(ServerPlayer player) {
        if (!isStart) return;
        teleportPlayerToReSpawnPoint(player);
        markRespawn(player);
    }


    public void markPendingRespawn(ServerPlayer player) {
        pendingRespawn.put(player.getUUID(), getServerLevel().getServer().getTickCount());
        markRespawn(player);
    }

    private void scanForDeaths() {
        int now = getServerLevel().getServer().getTickCount();
        for (ServerTeam team : getMapTeams().getNormalTeams()) {
            for (ServerPlayer player : team.getOnline()) {
                if (!player.isAlive() || player.isDeadOrDying()) {
                    if (!pendingRespawn.containsKey(player.getUUID())) {
                        LOGGER.info("[TDM] 检测到 {} 死亡 → 安排送回本队出生点", player.getName().getString());
                    }
                    pendingRespawn.put(player.getUUID(), now);
                    freezeOnDeath(player);
                }
            }
        }
    }


    private void freezeOnDeath(ServerPlayer player) {
        int delay = respawnDelaySeconds.get();
        if (delay <= 0) return;
        UUID id = player.getUUID();
        int now = getServerLevel().getServer().getTickCount();

        if (deathFrozen.add(id)) {
            LOGGER.info("[TDM] {} 死亡 → 相机固定死亡点 {} 秒后复活", player.getName().getString(), delay);

            com.github.pyzlwmn.filtdm.TeamCompetition.spectator.TcpDeathPose.record(player);
            com.github.pyzlwmn.filtdm.TeamCompetition.spectator.TcpDeathPose.get(id).ifPresent(pose ->
                    TcpNetwork.sendToPlayer(player, com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpDeathCameraS2CPacket
                            .start(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch())));

            try {
                player.setGameMode(GameType.SPECTATOR);
            } catch (RuntimeException ignored) {
            }
            respawnUnlockTick.put(id, now + delay * 20);
        } else {

            respawnUnlockTick.putIfAbsent(id, now + delay * 20);
        }
    }

    private void processDeathFreeze() {
        if (respawnUnlockTick.isEmpty()) return;
        int now = getServerLevel().getServer().getTickCount();
        for (Map.Entry<UUID, Integer> e : new ArrayList<>(respawnUnlockTick.entrySet())) {
            UUID id = e.getKey();
            ServerPlayer player = onlinePlayer(id);
            if (!isStart) {
                respawnUnlockTick.clear();
                deathFrozen.clear();
                return;
            }
            if (now < e.getValue()) continue;

            respawnUnlockTick.remove(id);
            deathFrozen.remove(id);
            if (player == null) continue;
            try {

                TcpNetwork.sendToPlayer(player, com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpDeathCameraS2CPacket.stop());
                player.setGameMode(GameType.SURVIVAL);
                if (player.isDeadOrDying()) {
                    player.respawn();
                }
                LOGGER.info("[TDM] {} 复活延迟结束 → 放回复活", player.getName().getString());
            } catch (RuntimeException ex) {
                LOGGER.warn("[TDM] {} 放回复活失败：{}", player.getName().getString(), ex.toString());
            }
        }
    }

    private boolean stillFrozen(UUID id) {
        Integer unlock = respawnUnlockTick.get(id);
        return unlock != null && getServerLevel().getServer().getTickCount() < unlock;
    }

    private ServerPlayer onlinePlayer(UUID id) {
        ServerPlayer player = getServerLevel().getServer().getPlayerList().getPlayer(id);
        if (player != null) return player;
        return getPlayerByUUID(id).orElse(null);
    }

    private void processPendingRespawn() {
        if (pendingRespawn.isEmpty()) return;
        defineDefaultSpawnPoints();

        int now = getServerLevel().getServer().getTickCount();
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(pendingRespawn.entrySet())) {
            if (!isStart) {
                pendingRespawn.clear();
                return;
            }
            UUID id = entry.getKey();
            ServerPlayer player = onlinePlayer(id);
            if (player == null) {

                if (now - entry.getValue() > 400L) {
                    LOGGER.warn("[TDM] 放弃：{} 20 秒内没能回到出生点（玩家对象拿不到）", id);
                    pendingRespawn.remove(id);
                }
                continue;
            }
            if (!player.isAlive() || player.isDeadOrDying()) {
                continue;
            }
            if (stillFrozen(id)) {
                continue;
            }
            respawnBackToSpawn(player);
            pendingRespawn.remove(id);
        }
    }

    private void respawnBackToSpawn(ServerPlayer player) {

        try {
            TcpNetwork.sendToPlayer(player, com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpDeathCameraS2CPacket.stop());
        } catch (RuntimeException ignored) {
        }
        com.github.pyzlwmn.filtdm.TeamCompetition.spectator.TcpDeathPose.clear(player.getUUID());

        getMapTeams().getPlayerData(player).ifPresent(d -> d.setLiving(true));
        player.setGameMode(GameType.ADVENTURE);
        healFull(player);
        sendToTeamSpawn(player, true);

        LoadoutManager.resetInventory(player);

        if (loadoutEnabled.get() && loadoutRefillOnRespawn.get()) {
            LoadoutManager.refillAmmo(player);
            LoadoutManager.refillMissing(player);
        }
        if (loadoutEnabled.get() && LoadoutManager.emptyLoadout(player)) {
            applyLoadoutTo(player);
            loadoutGiven.add(player.getUUID());
        }
    }

    private void sendToTeamSpawn(ServerPlayer player, boolean withProtection) {
        SpawnPointData point = pickSpawnPoint(player);
        if (point == null) {
            LOGGER.warn("[TDM] {} 送点失败：所在队伍没有任何出生点（请用 FPSMatch 出生点工具摆点）",
                    player.getName().getString());
            return;
        }
        player.setRespawnPosition(point.getDimension(), point.getBlockPos(), point.getYaw(), true, false);
        boolean ok = teleportToPoint(player, point);
        LOGGER.info("[TDM] 传送 {} → {} {}", player.getName().getString(), point.getBlockPos(),
                ok ? "成功" : "失败（点位不可用？）");
        if (withProtection) markRespawn(player);
    }

    private int spawnPointCount(ServerTeam team) {
        if (team == null) return 0;
        return team.getCapabilityMap().get(SpawnPointCapability.class)
                .map(cap -> cap.getSpawnPointsData().size()).orElse(0);
    }

    private SpawnPointData pickSpawnPoint(ServerPlayer player) {
        ServerTeam team = getMapTeams().getTeamByPlayer(player).orElse(null);
        if (team == null) return null;
        return team.getCapabilityMap().get(SpawnPointCapability.class).map(cap -> {
            List<SpawnPointData> points = cap.getSpawnPointsData();
            if (points.isEmpty()) return null;
            return points.get(getRandom().nextInt(points.size()));
        }).orElse(null);
    }

    public void markRespawn(ServerPlayer player) {
        int sec = spawnProtectionSeconds.get();
        if (sec > 0) {
            protectUntilTick.put(player.getUUID(),
                    getServerLevel().getServer().getTickCount() + sec * 20);
        }
    }

    public boolean isProtected(UUID id) {
        Integer until = protectUntilTick.get(id);
        return until != null && getServerLevel().getServer().getTickCount() < until;
    }


    @Override
    public void leave(ServerPlayer player) {
        super.leave(player);
        if (cleaningUp) return;
        ServerLevel lobby = lobbyLevel();
        int lx = lobbyX.get();
        int ly = lobbyY.get();
        int lz = lobbyZ.get();
        float yaw = lobbyYaw.get();
        try {
            player.teleportTo(lobby, lx + 0.5, ly, lz + 0.5, yaw, 0f);
            player.setRespawnPosition(lobby.dimension(), new BlockPos(lx, ly, lz), yaw, true, false);
            msgTo(player, "§7已离开对局，送回出生点");
            LOGGER.info("[TDM] {} 离队 → 送回出生点 ({}, {}, {})", player.getName().getString(), lx, ly, lz);
        } catch (RuntimeException e) {
            LOGGER.warn("[TDM] {} 离队传送失败：{}", player.getName().getString(), e.toString());
        }
    }

    @Override
    public void reset() {
        if (isStart || postMatch || preStartRunning) {
            LOGGER.info("[TDM] 收到 reset → 直接结束对局");
            endMatchNow();
        }
        super.reset();
    }

    private void endMatchNow() {
        this.isStart = false;
        this.preStartRunning = false;
        this.preStartTicks = 0;
        this.postMatch = false;
        this.postMatchTicks = 0;
        this.gameEnded = true;
        clearKills(redTeam);
        clearKills(blueTeam);
        pendingRespawn.clear();
        protectUntilTick.clear();
        respawnUnlockTick.clear();
        deathFrozen.clear();
        loadoutGiven.clear();
        applyImmediateRespawn(false);
        applyKeepInventory(false);
        sendEveryoneToLobby();
        syncScores();
    }


    public boolean isStarted() {
        return isStart;
    }

    public boolean canEditLoadout() {
        if (!loadoutEnabled.get()) return false;
        if (isStart) return false;
        return true;
    }

    public boolean canSwitchPreset() {
        if (!loadoutEnabled.get()) return false;
        if (!isStart) return true;
        return loadoutAllowSwitchInMatch.get();
    }

    private static void healFull(ServerPlayer player) {
        if (player == null) return;
        try {
            float max = player.getMaxHealth();
            player.setHealth(max);
            player.heal(max);
            player.removeAllEffects();
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
            player.setRemainingFireTicks(0);
            player.clearFire();
        } catch (RuntimeException e) {
            LOGGER.warn("[TDM] {} 回满血失败：{}", player.getName().getString(), e.toString());
        }
    }

    public void applyLoadoutTo(ServerPlayer player) {

        LoadoutManager.resetInventory(player);

        healFull(player);
        if (!loadoutEnabled.get()) return;
        LoadoutManager.giveLoadout(player, loadoutClearOnStart.get());
    }

    public void sendLoadoutPacket(ServerPlayer player) {
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, isStart, canEditLoadout()));
    }

    private void giveLoadoutToLateJoiners() {
        if (!loadoutEnabled.get()) return;
        for (ServerTeam team : getMapTeams().getNormalTeams()) {
            for (ServerPlayer player : team.getOnline()) {
                if (!player.isAlive()) continue;
                if (loadoutGiven.add(player.getUUID())) {
                    LOGGER.info("[TDM·背包] 中途加入：给 {} 发装备", player.getName().getString());
                    applyLoadoutTo(player);
                    sendLoadoutPacket(player);
                }
            }
        }
    }

    public int teamKills(ServerTeam team) {
        if (team == null) return 0;
        return team.getPlayersData().stream().mapToInt(PlayerData::getKills).sum();
    }

    public void addPlayerKill(ServerPlayer player) {
        getMapTeams().getPlayerData(player).ifPresent(d -> d.setKills(d.getKills() + 1));
    }

    private void clearKills(ServerTeam team) {
        if (team == null) return;
        team.getPlayersData().forEach(d -> {
            d.setKills(0);
            d.setHeadshotKills(0);
        });
    }

    public ServerTeam getRedTeam() { return redTeam; }

    public ServerTeam getBlueTeam() { return blueTeam; }

    public boolean isMobKillScoreEnabled() { return mobKillScore.get(); }

    public void syncScores() {
        int flags = 0;
        if (showKillFeed.get()) flags |= TcpScoreS2CPacket.FLAG_KILL_FEED;
        if (showAvatars.get()) flags |= TcpScoreS2CPacket.FLAG_AVATARS;
        if (isStart) flags |= TcpScoreS2CPacket.FLAG_PLAYING;
        if (postMatch) flags |= TcpScoreS2CPacket.FLAG_POST_MATCH;

        List<TcpScoreS2CPacket.Roster> roster = new ArrayList<>();
        collectRoster(blueTeam, false, roster);
        collectRoster(redTeam, true, roster);

        TcpScoreS2CPacket packet = new TcpScoreS2CPacket(
                teamKills(redTeam), teamKills(blueTeam), targetKills.get(),
                getElapsedMatchSeconds(), matchTimeLimit.get(),
                countdownSecondsLeft(), flags, roster);

        for (ServerPlayer p : getMapTeams().getOnlineWithSpec()) {
            TcpNetwork.sendToPlayer(p, packet);
        }
    }

    private void collectRoster(ServerTeam team, boolean red, List<TcpScoreS2CPacket.Roster> out) {
        if (team == null) return;
        for (ServerPlayer p : team.getOnline()) {
            out.add(new TcpScoreS2CPacket.Roster(p.getUUID(), p.getGameProfile().getName(), red));
        }
    }

    public void sendKillFeed(ServerPlayer killer, LivingEntity victim, String chatMsg) {
        if (chatMsg != null) msg(chatMsg);
        if (!showKillFeed.get()) return;

        int killerTeam = teamCode(getMapTeams().getTeamByPlayer(killer).orElse(null));
        int victimTeam = victim instanceof ServerPlayer sp
                ? teamCode(getMapTeams().getTeamByPlayer(sp).orElse(null))
                : TcpKillFeedS2CPacket.TEAM_NONE;

        ItemStack weapon = killer.getMainHandItem().copy();

        TcpKillFeedS2CPacket packet = new TcpKillFeedS2CPacket(
                killer.getName().getString(), killerTeam,
                victim.getName().getString(), victimTeam, weapon);
        for (ServerPlayer p : getMapTeams().getOnlineWithSpec()) {
            TcpNetwork.sendToPlayer(p, packet);
        }
    }

    public int teamCode(ServerTeam team) {
        if (team == null) return TcpKillFeedS2CPacket.TEAM_NONE;
        if (team == redTeam) return TcpKillFeedS2CPacket.TEAM_RED;
        if (team == blueTeam) return TcpKillFeedS2CPacket.TEAM_BLUE;
        return TcpKillFeedS2CPacket.TEAM_NONE;
    }

    public void msg(String text) {
        if (!chatMessages.get()) return;
        Component c = Component.literal(text);
        getMapTeams().getTeamsWithSpectator().forEach(t -> t.sendMessage(c, false));
    }

    public void msgTo(ServerPlayer player, String text) {
        if (!chatMessages.get()) return;
        player.displayClientMessage(Component.literal(text), false);
    }
}
