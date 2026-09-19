package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpLoadoutRequestC2SPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 背包编辑 UI 的入口（客户端）。
 *
 * 打开方式有三个，坏一个还有别的：
 *   1) 按键 B（默认，可在「按键设置 → 团队竞技」改）
 *   2) 聊天输入 /tdmloadout          ← 服务端指令，会直接推一份数据并让客户端开界面
 *   3) 对局里任何服务端主动推的「打开」包（TcpLoadoutS2CPacket 的 FLAG_OPEN）
 *
 * ⚠️ 键位注册在 TcpKeyRegister（MOD 总线）、轮询在 TcpKeyTicker（FORGE 总线）。
 */
public final class TcpKeybinds {

    /** 默认 `;` 键（v28：原来默认 B） */
    public static final KeyMapping OPEN = new KeyMapping(
            "key.bstcsdmplay.loadout", GLFW.GLFW_KEY_SEMICOLON, "key.categories.bstcsdmplay");

    private TcpKeybinds() {
    }

    /** 按键/指令触发：清空缓存 → 找服务端要数据 → 开界面 */
    public static void requestAndOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TcpLoadoutData.reset();
        TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutRequestC2SPacket());
        openEditor();
    }

    /** 只开界面（数据已经到了） */
    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof TcpLoadoutScreen) return;
        mc.setScreen(new TcpLoadoutScreen());
    }
}
