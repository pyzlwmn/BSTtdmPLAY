package com.github.pyzlwmn.bstcsdmplay.TeamCompetition;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpKillFeedS2CPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpMatchEndS2CPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpNetwork;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpScoreS2CPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.LoadoutManager;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.RefitSession;
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

/**
 * 团队竞技 · 可玩版 v6
 *
 * 建队 / 双方出生点 / 人数满自动·不满手动 / 开局前 10s 倒计时(倒计时完才正式计时)
 * / 计分 / 判定胜负 / 结束对局(退队+传送大厅) / HUD 同步 / 地图存档
 *
 * v6：
 *  - 修：只有 1 个人也会自动开局（原因是 canReadyStart 的宽松覆写绕过了"全员准备"判定）
 *  - 开局统一走「开局前倒计时」：满员自动 或 手动触发 → 屏幕显示 N 秒倒计时 → 倒计时结束后才开始正式计时
 *  - 对局时长默认 5 分钟；倒计时秒数 / 目标击杀 / 每队人数 / 时限 全部是地图设置
 *  - 新增地图存档（CODEC + SaveHolder），设置与队伍/出生点一起落盘
 *
 * 结构参考 BlockOffensive (GPL-3.0)
 */
public class TeamCompetitionMap extends BaseMap {

    private static final Logger LOGGER = LoggerFactory.getLogger("BST-TDM");

    /** 玩法（游戏类型）英文名：正式改为 tdm；类名保持 TeamCompetition* 不变 */
    public static final String TYPE = "tdm";

    /** 地图存档（含队伍 / 能力 / 出生点）；设置另走 saveConfig() */
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

    /** 供 FPSMatch 存档系统调用：设置落盘 + 地图数据落盘 */
    public static void save(FPSMDataManager manager) {
        FPSMCore.getInstance().getMapByClass(TeamCompetitionMap.class)
                .forEach(map -> {
                    map.saveConfig();
                    manager.saveData(map, map.getMapName(), false);
                });
    }

    /** ====== 地图设置（FPSMatch 地图配置界面可改，随存档保存） ====== */
    /** 每队人数上限（满员判定线） */
    private final Setting<Integer> teamLimit;
    /** 目标击杀数（全队合计，0 = 不限） */
    private final Setting<Integer> targetKills;
    /** 开局前倒计时秒数（倒计时结束才开始正式计时） */
    private final Setting<Integer> countdownSeconds;
    /** 全员 /ready 后 FPSMatch 自己的准备倒计时（秒）。⚠️ 别设 0，会引发大厅每 tick 空转 */
    private final Setting<Integer> readyCountdownSeconds;
    /** 满员是否自动开局（false = 一律手动） */
    private final Setting<Boolean> autoStartWhenFull;
    /** 对局时限秒数（0 = 不限；>0 时 HUD 显示剩余时间，到点按分数结算） */
    private final Setting<Integer> matchTimeLimit;
    /** 重生保护秒数（0 = 不保护） */
    private final Setting<Integer> spawnProtectionSeconds;
    /** 死后立刻重生（跳过死亡界面），死亡竞技体验用 */
    private final Setting<Boolean> immediateRespawn;
    /** 友军伤害 */
    private final Setting<Boolean> friendlyFire;
    /** 击杀生物是否加分 */
    private final Setting<Boolean> mobKillScore;
    /** 聊天栏文字播报（默认关） */
    private final Setting<Boolean> chatMessages;
    /** HUD 显示击杀提示 */
    private final Setting<Boolean> showKillFeed;
    /** HUD 显示头像组 */
    private final Setting<Boolean> showAvatars;

    /** ====== 出生点（对局结束后所有人被传送到这里，全部可设置） ====== */
    /** 出生点维度（如 minecraft:overworld） */
    private final Setting<String> lobbyDim;
    /** 出生点坐标 + 朝向 */
    private final Setting<Integer> lobbyX;
    private final Setting<Integer> lobbyY;
    private final Setting<Integer> lobbyZ;
    private final Setting<Integer> lobbyYaw;
    /** 结束面板（MVP/SVP）显示秒数，之后才传送回出生点 */
    private final Setting<Integer> mvpHoldSeconds;

    /** ====== 背包系统（v17）：对局自动发枪 + 玩家 UI 选枪 ====== */
    /** 是否启用背包系统（关掉就不发枪、UI 只读） */
    private final Setting<Boolean> loadoutEnabled;
    /** 开局发枪前是否清空背包 */
    private final Setting<Boolean> loadoutClearOnStart;
    /** 重生时是否补满弹药 */
    private final Setting<Boolean> loadoutRefillOnRespawn;
    /** 对局进行中是否允许改装备 */
    private final Setting<Boolean> loadoutAllowEditInMatch;

    /** 每队默认补几个出生点（仅当该队一个点都没有时才补） */
    private static final int DEFAULT_SPAWN_PER_TEAM = 3;

    private ServerTeam redTeam;
    private ServerTeam blueTeam;
    private boolean gameEnded = false;
    private boolean cleaningUp = false;

    /** 开局前倒计时（tick）：倒计时结束才 isStart=true、才开始正式计时 */
    private int preStartTicks = 0;
    private boolean preStartRunning = false;

    /** 对局结束展示阶段（显示 MVP/SVP，可以开枪但打不出伤害） */
    private boolean postMatch = false;
    private int postMatchTicks = 0;

    /** 我们自己调用 super.startGameWithAnnouncement() 时置位，防止 start() 覆写递归 */
    private boolean internalStarting = false;
    /** 配置修正只跑一次 */
    private boolean settingsSanitized = false;

    /** 重生保护：玩家 → 保护到期 tick */
    private final Map<UUID, Integer> protectUntilTick = new HashMap<>();

    /** 待传送回出生点的玩家 → 标记时刻（游戏 tick）；死后每 tick 重试直到真的活过来 */
    private final Map<UUID, Integer> pendingRespawn = new HashMap<>();

    /** 进入对局前的 immediateRespawn 规则原值（对局结束还原） */
    private Boolean prevImmediateRespawn = null;
    /** ★ v37：对局期间的 keepInventory 原值（对局结束还原） */
    private Boolean prevKeepInventory = null;

    /** 本局已经发过装备的玩家（中途加入的也发一次） */
    private final Set<UUID> loadoutGiven = new HashSet<>();

    // ================== 构造器 ==================

    public TeamCompetitionMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);

        // ★ 地图设置（分类 + 名字 + 默认值）
        this.teamLimit = addSetting("team", "teamLimit", 5);
        this.targetKills = addSetting("match", "targetKills", 30);
        this.countdownSeconds = addSetting("match", "countdownSeconds", 10);
        this.readyCountdownSeconds = addSetting("match", "readyCountdownSeconds", 1);
        // 取消201c满人自动开201d
        this.autoStartWhenFull = addSetting("match", "autoStartWhenFull", false);
        this.matchTimeLimit = addSetting("match", "matchTimeLimit", 300);      // 5 分钟
        this.spawnProtectionSeconds = addSetting("player", "spawnProtectionSeconds", 3);
        this.immediateRespawn = addSetting("player", "immediateRespawn", true);
        this.friendlyFire = addSetting("combat", "friendlyFire", false);
        this.mobKillScore = addSetting("combat", "mobKillScore", true);
        this.chatMessages = addSetting("display", "chatMessages", false);
        this.showKillFeed = addSetting("display", "showKillFeed", true);
        this.showAvatars = addSetting("display", "showAvatars", true);

        // 出生点（结束后传送目标）+ 结束面板时长
        this.lobbyDim = addSetting("lobby", "dim", "minecraft:overworld");
        this.lobbyX = addSetting("lobby", "x", 0);
        this.lobbyY = addSetting("lobby", "y", 64);
        this.lobbyZ = addSetting("lobby", "z", 0);
        this.lobbyYaw = addSetting("lobby", "yaw", 0);
        this.mvpHoldSeconds = addSetting("match", "mvpHoldSeconds", 6);

        // ★ 背包系统（枪械配置在 config/bstcsdmplay/loadout.json，这里只管开关）
        this.loadoutEnabled = addSetting("loadout", "enabled", true);
        this.loadoutClearOnStart = addSetting("loadout", "clearOnStart", true);
        this.loadoutRefillOnRespawn = addSetting("loadout", "refillOnRespawn", true);
        this.loadoutAllowEditInMatch = addSetting("loadout", "allowEditInMatch", true);

        // ★ 开局：满员才自动（我们自己算倒计时）；FPSMatch 大厅的自动开始关掉，避免两套逻辑打架
        this.autoStart.set(false);
        // 人数不满 → 手动开局：全员 /ready → FPSMatch 自己的准备倒计时 → 进我们的开局前倒计时
        // ⚠️ readyStartTime 绝不能是 0：MapLobbyController.handleReadyCountdown 里
        //    `readyCountdownTimer >= totalTicks` 在 totalTicks=0 时每 tick 都成立 →
        //    每 tick 都调 startGameWithAnnouncement() + 狂发 RoomReadyState 包 →
        //    客户端的「准备开始」UI 会一直卡着不消失（主人实测到的就是这个）
        this.readyStartEnabled.set(true);
        this.readyStartTime.set(20);   // 1 秒（真正好看的 10 秒倒计时由我们自己画）

        this.redTeam = addTeam(TeamData.of("red", teamLimit.get(), List.of(SpawnPointCapability.class)));
        this.blueTeam = addTeam(TeamData.of("blue", teamLimit.get(), List.of(SpawnPointCapability.class)));

        this.redTeam.setColor(new Vector3f(1.0f, 0.25f, 0.25f));
        this.blueTeam.setColor(new Vector3f(0.30f, 0.55f, 1.0f));
        this.redTeam.getPlayerTeam().setColor(ChatFormatting.RED);
        this.blueTeam.getPlayerTeam().setColor(ChatFormatting.BLUE);

        defineDefaultSpawnPoints();
        applyFriendlyFire();
    }

    /** 存档反序列化用（private）：先按普通构造建图，再把能力和队伍数据写回去 */
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

    /**
     * 给两队补默认出生点（红队靠 pos1 一侧、蓝队靠 pos2 一侧）。
     * 正式图建议进游戏用 FPSMatch 的出生点工具（OP 2 级）摆好点覆盖，那时本方法自动跳过。
     *
     * ⚠️ v9 修复：原来「每加一个点都判断一次队伍是否为空」，第一个点加完队伍就不空了，
     *    后面两个点全被跳过 → 每队其实只有 1 个出生点，重生很容易出问题。
     *    现在改成「队伍一个点都没有时，一次性补齐整队」。
     */
    private void defineDefaultSpawnPoints() {
        ensureDefaultSpawnPoints(redTeam, true);
        ensureDefaultSpawnPoints(blueTeam, false);
    }

    private void ensureDefaultSpawnPoints(ServerTeam team, boolean red) {
        if (team == null) return;
        team.getCapabilityMap().get(SpawnPointCapability.class).ifPresent(cap -> {
            if (!cap.getSpawnPointsData().isEmpty()) return;   // 已经有点（工具摆过/存档里有）就不动

            AABB box = getMapArea().aabb();
            double x = red
                    ? box.minX + (box.maxX - box.minX) * 0.2
                    : box.minX + (box.maxX - box.minX) * 0.8;
            float yaw = red ? -90f : 90f;

            for (int i = 0; i < DEFAULT_SPAWN_PER_TEAM; i++) {
                double t = 0.3 + 0.2 * i;
                double z = box.minZ + (box.maxZ - box.minZ) * t;
                // 放到地表上（原来用 box.minY+1 很容易埋进地里 → teleportToPoint 直接失败）
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

    // ================== 开局条件 ==================

    /**
     * ⚠️ v6 修复点：这里必须是「全员都按了准备」才允许走 FPSMatch 的准备开局。
     * v5 写成 onlineCount() >= 1 会导致一个人在线就直接进倒计时自动开局。
     */
    @Override
    protected boolean canReadyStart() {
        return onlineCount() >= 1 && allNormalOnlinePlayersReady();
    }

    /** 满人自动开局已取消（v24）：开局只能靠全员 /ready，或 OP 的 debug start */
    @Override
    protected boolean canAutoStart() {
        return false;
    }

    /** 两队是否各有 teamLimit 人 */
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

    // ================== tick：开局前倒计时 / 时限 / HUD 同步 ==================

    private int syncTimer = 0;

    /** 只跑一次的配置修正：把会引发 bug 的旧存档配置拉回安全值，并打出生效设置 */
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
        // ① 对局结束展示阶段：显示 MVP/SVP，等 mvpHoldSeconds 秒后传送回出生点
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
            scanForDeaths();                  // ★ 每 tick 自查：谁死了就打重生标记（不依赖事件）
            processPendingRespawn();          // ★ 复活后送回本队出生点
            giveLoadoutToLateJoiners();       // ★ 中途加入的人也要发枪
            int limit = matchTimeLimit.get();
            if (limit > 0 && getElapsedMatchSeconds() >= limit) {
                victory();
                return;
            }
            if (++syncTimer >= 10) {          // 每 10 tick = 0.5 秒推一次
                syncTimer = 0;
                syncScores();
            }
            return;
        }

        // 未开局
        if (preStartRunning) {
            if (preStartTicks > 0) {
                preStartTicks--;
            }
            if (preStartTicks <= 0) {         // 倒计时结束 → 正式开始（正式计时从现在起算）
                preStartRunning = false;
                preStartTicks = 0;
                beginMatch();
                return;
            }
        } else if (false && autoStartWhenFull.get() && teamsFull()) {
            beginPreStart();                  // v24：已取消满人自动开，这行不再生效
        }
        // 人数不满时什么都不做：等全员 /ready（手动开局）

        if (++syncTimer >= 10) {
            syncTimer = 0;
            syncScores();
        }
    }

    /** 开局前倒计时剩余秒数（0 = 没在倒计时） */
    public int countdownSecondsLeft() {
        if (!preStartRunning || preStartTicks <= 0) return 0;
        return (preStartTicks + 19) / 20;
    }

    /** 是否处于「开局前倒计时」（这段时间内不记录正式计时、免疫伤害） */
    public boolean isPreStarting() {
        return preStartRunning;
    }

    // ================== 开局流程 ==================

    /**
     * FPSMatch 大厅（全员 /ready 或管理员命令）触发开局时走这里。
     * 我们不直接开始，而是进入「开局前倒计时」。
     */
    @Override
    protected void startGameWithAnnouncement() {
        if (isStart || preStartRunning) return;
        beginPreStart();
    }

    /**
     * v13：让 `/fpsm map modify <gameType> <map> debug start` 也能开局。
     *
     * 那个 debug 子命令走的是 `map.start()`（只 post StartEvent + 清时钟），**不会**置 isStart，
     * 玩法自己不管就完全开不起来。这里把它也接进我们的「开局前倒计时」流程。
     *
     * ⚠️ 注意别递归：beginMatch() 里会调 super.startGameWithAnnouncement() → super.start()，
     *    那时用 internalStarting 放行到原生实现。
     */
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

    /** 进入开局前倒计时：送出生点 + 屏幕倒计时 + 免疫伤害 */
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
            }
        }

        // v25：如果有人还卡在改装台，先把背包还给他
        for (ServerPlayer p : getMapTeams().getOnlineWithSpec()) {
            RefitSession.clearAll("对局开始");
        }

        msg("§e对局即将开始：§c" + countdownSeconds.get() + " §e秒后正式计时（期间不掉血）");
        syncScores();
    }

    /** 倒计时结束：真正开始对局（从这里开始计正式时长） */
    private void beginMatch() {
        if (onlineCount() < 1) return;

        super.startGameWithAnnouncement();    // post StartEvent + resetMatchClock + clearReadyPlayers

        this.isStart = true;
        this.gameEnded = false;
        this.cleaningUp = false;
        this.protectUntilTick.clear();
        applyFriendlyFire();
        // ★ v37：对局中死亡不掉落（keepInventory=true），结束后还原
        applyKeepInventory(true);

        msg("§6§l==== 对局开始 ====");
        msg("§e目标：" + targetKills.get() + " 击杀 ｜ 时限：" + matchTimeLimit.get() + " 秒");

        // ★ 正式开局后，把两队玩家送回各自队伍出生点（自己选点 + 自己传送 + 打日志）
        LOGGER.info("[TDM] 开局！出生点数量：红队 {} 个 / 蓝队 {} 个",
                spawnPointCount(redTeam), spawnPointCount(blueTeam));
        this.loadoutGiven.clear();
        for (ServerTeam team : getMapTeams().getNormalTeams()) {
            for (ServerPlayer player : new ArrayList<>(team.getOnline())) {
                sendToTeamSpawn(player, false);
                applyLoadoutTo(player);            // ★ 按玩家选择发枪（槽位1~5）
                sendLoadoutPacket(player);         // ★ 顺手把 UI 数据推给他
                loadoutGiven.add(player.getUUID());
            }
        }

        syncScores();
    }

    /** 友军伤害应用到 MC 计分板队伍 */
    private void applyFriendlyFire() {
        boolean ff = friendlyFire.get();
        if (redTeam != null) redTeam.getPlayerTeam().setAllowFriendlyFire(ff);
        if (blueTeam != null) blueTeam.getPlayerTeam().setAllowFriendlyFire(ff);
    }

    // ================== 判定胜负 + 结束对局 ==================

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

        // ① 先把统计取出来（reset() 之后就没了）
        int red = teamKills(redTeam);
        int blue = teamKills(blueTeam);
        ServerTeam winner = red >= blue ? redTeam : blueTeam;      // 平局算红赢
        ServerTeam loser = winner == redTeam ? blueTeam : redTeam;
        TcpMatchEndS2CPacket.PlayerLine mvp = bestOf(winner);
        TcpMatchEndS2CPacket.PlayerLine svp = bestOf(loser);

        super.victory();

        msg("§6§l==== 对局结束 ====");
        msg("§a胜者：" + winner.getFixedName() + "  §7(红 " + red + " : " + blue + " 蓝)");

        // ② 停止对局：isStart=false → 还能开枪，但监听器会取消伤害
        this.isStart = false;
        this.preStartRunning = false;
        this.preStartTicks = 0;

        // ③ 推结束面板（MVP/SVP）→ 进入展示阶段，等 mvpHoldSeconds 秒再传送回出生点
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

    /** 队内最佳（先比击杀，再比总伤害）；MVP=胜方最佳，SVP=败方最佳 */
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
            loadoutGiven.clear();
            applyImmediateRespawn(false);                // 还原 doImmediateRespawn 规则
            applyKeepInventory(false);                   // ★ v37：还原死亡掉落规则
        } finally {
            this.cleaningUp = false;
        }
    }

    /**
     * ★ v37（主人 22:37 要求）：对局期间**死亡不掉落物品**。
     * 做法：开局把 `keepInventory` 游戏规则置 true，结束/清理时还原原值。
     * （和 applyImmediateRespawn 同一个套路：只记一次原值，避免反复读写）
     */
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
            // 规则拿不到就算了，不影响对局
        }
    }

    /**
     * 开关 doImmediateRespawn 游戏规则（只在第一次改前记下原值，结束时还原）。
     * 打开后玩家死亡不弹死亡界面、直接重生，再配合 markPendingRespawn 就是"秒回出生点"。
     */
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
            // 规则拿不到就算了，不影响对局
        }
    }

    /** 出生点维度（设置里填维度 id，如 minecraft:overworld、minecraft:the_nether） */
    private ServerLevel lobbyLevel() {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(lobbyDim.get());
            if (rl != null) {
                ServerLevel level = getServerLevel().getServer()
                        .getLevel(ResourceKey.create(Registries.DIMENSION, rl));
                if (level != null) return level;
            }
        } catch (RuntimeException ignored) {
            // 维度写错就退回当前世界，别把对局搞崩
        }
        return getServerLevel();
    }

    /** 重生：送回本队出生点 + 上保护 */
    public void respawnAtSpawnPoint(ServerPlayer player) {
        if (!isStart) return;
        teleportPlayerToReSpawnPoint(player);
        markRespawn(player);
    }

    // ================== 死亡重生回出生点（v9 修复） ==================

    /**
     * 标记「这个玩家重生了，下一 tick 送回出生点」。
     * 为什么不直接在 PlayerRespawnEvent 里传送：原版在那之后还会再落一次位置，
     * 直接传送会被覆盖（表现就是"死了没回出生点"）。延迟 1 tick 最稳。
     */
    public void markPendingRespawn(ServerPlayer player) {
        pendingRespawn.put(player.getUUID(), getServerLevel().getServer().getTickCount());
        markRespawn(player);          // 先上保护，避免这 1 tick 内又被打
    }

    /**
     * v15：每 tick 扫一遍两队在线玩家，谁"死了"就立刻打重生标记。
     *
     * 为什么加这层：标记原本只在死亡/重生事件里打，一旦那条路没走到
     * （监听器没注册 / 地图缓存查不到 / 玩家对象换实例……）重生就整段失效。
     * 现在服务端自己每 tick 看一眼，只要 tick() 在跑就一定能发现死亡。
     */
    private void scanForDeaths() {
        int now = getServerLevel().getServer().getTickCount();
        for (ServerTeam team : getMapTeams().getNormalTeams()) {
            for (ServerPlayer player : team.getOnline()) {
                if (!player.isAlive() || player.isDeadOrDying()) {
                    if (!pendingRespawn.containsKey(player.getUUID())) {
                        LOGGER.info("[TDM] 检测到 {} 死亡 → 安排送回本队出生点", player.getName().getString());
                    }
                    pendingRespawn.put(player.getUUID(), now);
                }
            }
        }
    }

    /** 拿在线玩家：先用服务端玩家列表（最可靠），再退回 FPSMatch 查询 */
    private ServerPlayer onlinePlayer(UUID id) {
        ServerPlayer player = getServerLevel().getServer().getPlayerList().getPlayer(id);
        if (player != null) return player;
        return getPlayerByUUID(id).orElse(null);
    }

    private void processPendingRespawn() {
        if (pendingRespawn.isEmpty()) return;
        defineDefaultSpawnPoints();   // 兜底：队伍一个点都没有时补上

        int now = getServerLevel().getServer().getTickCount();
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(pendingRespawn.entrySet())) {
            if (!isStart) {
                pendingRespawn.clear();
                return;
            }
            UUID id = entry.getKey();
            ServerPlayer player = onlinePlayer(id);
            if (player == null) {
                // 拿不到玩家：可能刚死/刚复活换实例，等一会儿再说；超时（20 秒）才放弃
                if (now - entry.getValue() > 400L) {
                    LOGGER.warn("[TDM] 放弃：{} 20 秒内没能回到出生点（玩家对象拿不到）", id);
                    pendingRespawn.remove(id);
                }
                continue;
            }
            if (!player.isAlive() || player.isDeadOrDying()) {
                continue;                          // 还躺着/死亡界面，下一 tick 再试
            }
            respawnBackToSpawn(player);
            pendingRespawn.remove(id);
        }
    }

    /** 死亡重生：补状态 + 送回本队出生点 + 上保护 */
    private void respawnBackToSpawn(ServerPlayer player) {
        getMapTeams().getPlayerData(player).ifPresent(d -> d.setLiving(true));
        player.setGameMode(GameType.ADVENTURE);
        player.heal(player.getMaxHealth());
        player.removeAllEffects();
        sendToTeamSpawn(player, true);

        // ★ 重生：补满弹药 + **缺哪个槽补哪个**（v36：原来只有 5 个槽全空才整套重发，掉一件就不管）
        if (loadoutEnabled.get() && loadoutRefillOnRespawn.get()) {
            LoadoutManager.refillAmmo(player);
            LoadoutManager.refillMissing(player);
        }
        if (loadoutEnabled.get() && LoadoutManager.emptyLoadout(player)) {
            applyLoadoutTo(player);
            loadoutGiven.add(player.getUUID());
        }
    }

    /** 把玩家送到本队任意一个出生点（自己挑点、自己写重生位置，不依赖 FPSMatch 的分配） */
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

    /** 队伍出生点数量（日志用） */
    private int spawnPointCount(ServerTeam team) {
        if (team == null) return 0;
        return team.getCapabilityMap().get(SpawnPointCapability.class)
                .map(cap -> cap.getSpawnPointsData().size()).orElse(0);
    }

    /** 从本队出生点里随机挑一个 */
    private SpawnPointData pickSpawnPoint(ServerPlayer player) {
        ServerTeam team = getMapTeams().getTeamByPlayer(player).orElse(null);
        if (team == null) return null;
        return team.getCapabilityMap().get(SpawnPointCapability.class).map(cap -> {
            List<SpawnPointData> points = cap.getSpawnPointsData();
            if (points.isEmpty()) return null;
            return points.get(getRandom().nextInt(points.size()));
        }).orElse(null);
    }

    // ================== 重生保护 ==================

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

    // ================== 离队 / 重置（v24） ==================

    /**
     * 离队自动送回出生点。
     * FPSMatch 在玩家退出地图/队伍时会调 leave()，这里补上「传回出生点」。
     * 注意 cleaningUp 标记：我们自己在结束流程里批量调 leave() 时不要再重复传送。
     */
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

    /** reset（/fpsm ... debug reset）= 直接结束对局，而不是只清状态 */
    @Override
    public void reset() {
        if (isStart || postMatch || preStartRunning) {
            LOGGER.info("[TDM] 收到 reset → 直接结束对局");
            endMatchNow();
        }
        super.reset();
    }

    /** 立即结束：清统计 + 送回出生点 + 退出对局状态 */
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
        loadoutGiven.clear();
        applyImmediateRespawn(false);
        applyKeepInventory(false);                       // ★ v37：还原死亡掉落规则
        sendEveryoneToLobby();
        syncScores();
    }

    // ================== 背包系统（v17） ==================

    /** 对局是否已正式开始（背包包用） */
    public boolean isStarted() {
        return isStart;
    }

    /**
     * 现在能不能改装备。
     * v31（主人 2026-09-21 要求）：**对局中一律锁定** —— 对局里不能改装备/背包/配件。
     * （loadoutAllowEditInMatch 字段保留但不再生效；要改回旧行为就把上面两行换成
     *   return !isStart || loadoutAllowEditInMatch.get(); ）
     */
    public boolean canEditLoadout() {
        if (!loadoutEnabled.get()) return false;
        if (isStart) return false;
        return true;
    }

    /** 按玩家在 UI 里的选择发一整套装备 */
    public void applyLoadoutTo(ServerPlayer player) {
        if (!loadoutEnabled.get()) return;
        LoadoutManager.giveLoadout(player, loadoutClearOnStart.get());
    }

    /** 把「你能用什么 / 当前选了什么」推给客户端 UI */
    public void sendLoadoutPacket(ServerPlayer player) {
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, isStart, canEditLoadout()));
    }

    /** 中途加入的玩家也自动拿装备（每人每局一次） */
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

    // ================== 计分 ==================

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

    // ================== HUD 同步 ==================

    public void syncScores() {
        int flags = 0;
        if (showKillFeed.get()) flags |= TcpScoreS2CPacket.FLAG_KILL_FEED;
        if (showAvatars.get()) flags |= TcpScoreS2CPacket.FLAG_AVATARS;
        if (isStart) flags |= TcpScoreS2CPacket.FLAG_PLAYING;   // 对局中：客户端据此强制收掉倒计时文字
        if (postMatch) flags |= TcpScoreS2CPacket.FLAG_POST_MATCH;   // 结算中：同样别显示倒计时/即将开始

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

    /** 击杀提示：▌击杀者 [枪包图标] 被击杀者 */
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

    // ================== 消息播报（默认关闭） ==================

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
