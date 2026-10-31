package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpLoadoutRequestC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class TcpKeybinds {

    public static final KeyMapping OPEN = new KeyMapping(
            "key.filtdm.loadout", GLFW.GLFW_KEY_SEMICOLON, "key.categories.filtdm");

    private TcpKeybinds() {
    }

    public static void requestAndOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TcpLoadoutData.reset();
        TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutRequestC2SPacket());
        openEditor();
    }

    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof TcpLoadoutScreen) return;
        mc.setScreen(new TcpLoadoutScreen());
    }
}
