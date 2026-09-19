package com.github.pyzlwmn.bstcsdmplay.TeamCompetition;

import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 只让 OP 用 FPSMatch 的 ESC 暂停界面「地图」按钮（v12 新增）
 *
 * 机制（源码实锤）：
 *   - 那个按钮的**可见性**是客户端的一个开关：`FPSMClientGlobalData.mapSelectionButtonVisible`
 *   - 它由服务端下发：`MapSelectionAccessS2CPacket(visible)`（登录 / 换维度时各同步一次）
 *   - 服务端算法：`visible = player.hasPermissions(2) || FPSMatch 配置 enableMapSelectionButtonForNonOps`
 *   - 渲染和"备用点击检测"都会先查这个开关，所以把开关压成 false，非 OP 就既看不到也点不到
 *
 * 我们这道保险：非 OP 每 3 秒补推一次 visible=false，
 * 这样即使 FPSMatch 的配置没改 / 客户端缓存了旧值，也保证非 OP 手上没有这个按钮。
 *
 * ⚠️ modid 改成你自己的（下面按 bstcsdmplay 写的）
 */
@Mod.EventBusSubscriber(modid = "bstcsdmplay", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TdmOpOnlyPauseButton {

    /** 推送间隔（tick）：60 = 3 秒 */
    private static final int PUSH_INTERVAL_TICKS = 60;

    private TdmOpOnlyPauseButton() {
    }

    /** 登录时立刻纠正一次（FPSMatch 自己也会同步，这里做兜底） */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            hideForNonOp(player);
        }
    }

    /** 换维度时 FPSMatch 会重新同步，我们再补一次 */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            hideForNonOp(player);
        }
    }

    /** 周期性补推，防止被其它包覆盖 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % PUSH_INTERVAL_TICKS != 0) return;
        hideForNonOp(player);
    }

    /** 非 OP → 告诉它的客户端：你没有地图按钮（OP ≥2 级不动，交给 FPSMatch 原生逻辑） */
    private static void hideForNonOp(ServerPlayer player) {
        if (player.hasPermissions(2)) return;
        FPSMatch.sendToPlayer(player, new MapSelectionAccessS2CPacket(false));
    }
}
