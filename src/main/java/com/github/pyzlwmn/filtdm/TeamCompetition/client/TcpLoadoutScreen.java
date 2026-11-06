package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpAttachmentRequestC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpLoadoutPresetC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpLoadoutS2CPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpLoadoutSelectC2SPacket;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpRefitC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class TcpLoadoutScreen extends Screen {

    private static final int WANT_W = 400;
    private static final int WANT_H = 232;
    private static final int PER_PAGE = 7;
    private static final int SLOT_BTN_H = 18;
    private static final int OPT_BTN_H = 16;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    private int activeSlot = 0;
    private int page = 0;
    private int builtVersion = -1;

    private boolean renaming = false;
    private EditBox nameBox;

    public TcpLoadoutScreen() {
        super(Component.literal("装备编辑"));
    }

    @Override
    protected void init() {
        this.panelW = Math.min(WANT_W, Math.max(240, this.width - 20));
        this.panelH = Math.min(WANT_H, Math.max(180, this.height - 20));
        this.left = (this.width - panelW) / 2;
        this.top = (this.height - panelH) / 2;
        rebuild();
    }

    @Override
    public void tick() {
        if (builtVersion != TcpLoadoutData.version()) rebuild();
    }

    private void rebuild() {
        clearWidgets();
        nameBox = null;
        builtVersion = TcpLoadoutData.version();

        boolean editable = TcpLoadoutData.canEdit();

        boolean switchable = TcpLoadoutData.canSwitch();
        int innerX = left + 10;
        int innerW = panelW - 20;

        List<TcpLoadoutS2CPacket.PresetData> presets = TcpLoadoutData.presets();
        int presetArea = innerW - 3 * 22;
        int count = Math.max(1, presets.size());
        int pw = Math.max(40, Math.min(96, presetArea / count - 2));
        int px = innerX;
        int py = top + 28;
        for (TcpLoadoutS2CPacket.PresetData p : presets) {
            final String id = p.id();
            String label = (p.active() ? "▶ " : "") + p.name();
            Button b = Button.builder(Component.literal(label), btn ->
                            TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket("switch", id, "")))
                    .bounds(px, py, pw, 16)
                    .build();

            b.active = switchable && !p.active();
            addRenderableWidget(b);
            px += pw + 2;
        }
        int bx = innerX + innerW - 3 * 22 + 2;
        int maxPresets = TcpLoadoutData.maxPresets();
        Button addB = Button.builder(Component.literal("+"), btn ->
                        TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket("create", "", "")))
                .bounds(bx, py, 20, 16).build();
        addB.active = editable && presets.size() < maxPresets;
        addRenderableWidget(addB);

        Button renameB = Button.builder(Component.literal("改"), btn -> {
                    renaming = !renaming;
                    rebuild();
                })
                .bounds(bx + 22, py, 20, 16).build();
        renameB.active = editable;
        addRenderableWidget(renameB);

        TcpLoadoutS2CPacket.PresetData act = TcpLoadoutData.activePreset();
        Button delB = Button.builder(Component.literal("删"), btn -> {
                    if (act != null) {
                        TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket("delete", act.id(), ""));
                    }
                })
                .bounds(bx + 44, py, 20, 16).build();
        delB.active = editable && presets.size() > 1;
        addRenderableWidget(delB);

        if (renaming && act != null) {
            int ey = py + 20;
            nameBox = new EditBox(this.font, innerX, ey, Math.max(80, innerW - 60), 16,
                    Component.literal("背包名字"));
            nameBox.setMaxLength(16);
            nameBox.setValue(act.name());
            nameBox.setFocused(true);
            addRenderableWidget(nameBox);
            Button save = Button.builder(Component.literal("保存名字"), btn -> {
                        String v = nameBox == null ? "" : nameBox.getValue().trim();
                        TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket("rename", act.id(), v));
                        renaming = false;
                        rebuild();
                    })
                    .bounds(innerX + innerW - 56, ey, 56, 16).build();
            save.active = editable;
            addRenderableWidget(save);
        }

        int colY = top + 74;
        List<TcpLoadoutS2CPacket.SlotData> allSlots = TcpLoadoutData.slots();
        int colW = 104;
        for (int i = 0; i < allSlots.size(); i++) {
            TcpLoadoutS2CPacket.SlotData data = allSlots.get(i);
            final int slot = data.slot();
            Button b = Button.builder(Component.literal((slot == activeSlot ? "> " : "  ") + data.label()),
                            btn -> {
                                activeSlot = slot;
                                page = 0;
                                rebuild();
                            })
                    .bounds(innerX, colY + i * SLOT_BTN_H, colW, SLOT_BTN_H - 2)
                    .build();
            addRenderableWidget(b);
        }
        int toolY = colY + allSlots.size() * SLOT_BTN_H + 4;
        Button refit = Button.builder(Component.literal("改装"), btn ->
                        TcpNetwork.CHANNEL.sendToServer(new TcpRefitC2SPacket("start", slotNameOf(activeSlot))))
                .bounds(innerX, toolY, colW, SLOT_BTN_H - 2).build();
        refit.active = editable && !TcpLoadoutData.inMatch();
        addRenderableWidget(refit);
        toolY += SLOT_BTN_H;

        Button att = Button.builder(Component.literal("配件编辑"), btn ->
                        TcpNetwork.CHANNEL.sendToServer(new TcpAttachmentRequestC2SPacket()))
                .bounds(innerX, toolY, colW, SLOT_BTN_H - 2).build();
        att.active = editable;
        addRenderableWidget(att);

        int rx = innerX + colW + 8;
        int rw = innerX + innerW - rx;
        int ry = colY;
        TcpLoadoutS2CPacket.SlotData sel = TcpLoadoutData.slot(activeSlot);
        List<String> options = sel == null ? List.of() : sel.options();
        String current = sel == null ? null : sel.current();

        int pages = Math.max(1, (options.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        int shown = Math.min(PER_PAGE, Math.max(0, options.size() - page * PER_PAGE));
        for (int i = 0; i < shown; i++) {
            String id = options.get(page * PER_PAGE + i);
            boolean selected = id.equals(current);
            Button b = Button.builder(Component.literal((selected ? "> " : "   ") + TcpGunNames.display(id)), btn ->
                            TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutSelectC2SPacket(activeSlot, id)))
                    .bounds(rx, ry + i * OPT_BTN_H, rw, OPT_BTN_H - 1)
                    .build();
            // ★ v68 需求三：对局中（仅可切换、不可编辑）允许「在已保存的配置里换枪」：
            //   只点当前已经配好的枪（= 不改变配置内容）时放行；换成没配过的属于「编辑」，对局中灰掉。
            boolean presetPick = !editable && switchable && selected;
            b.active = editable || presetPick;
            addRenderableWidget(b);
        }

        if (pages > 1) {
            int pgY = top + panelH - 30;
            addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
                page = Math.max(0, page - 1);
                rebuild();
            }).bounds(rx, pgY, 20, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
                page = Math.min(pages - 1, page + 1);
                rebuild();
            }).bounds(rx + 24, pgY, 20, 16).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        UiDraw.panelEx(g, left, top, panelW, panelH, UiDraw.RADIUS_LARGE, 0xFF3A4A5A, 0xF01A2432, 0xE60D131C, 0xFFFFC93C);
        UiDraw.topBarRounded(g, left, top, panelW, 24, UiDraw.RADIUS_LARGE, 0xFF1B2A41);
        g.drawString(this.font, "装备编辑", left + 10, top + 8, 0xFFFFFFF0, false);

        String right = TcpLoadoutData.inMatch() ? "对局中" : "未开局";
        g.drawString(this.font, right, left + panelW - 10 - this.font.width(right), top + 8, 0xFFBBD0E0, false);

        if (!TcpLoadoutData.received()) {
            g.drawString(this.font, "正在读取你的可用装备…", left + 14, top + 60, 0xFFCCCCCC, false);
        } else if (!TcpLoadoutData.enabled()) {
            g.drawString(this.font, "背包系统已关闭（地图设置 loadout/enabled）", left + 14, top + 60, 0xFFFF8888, false);
        } else {
            TcpLoadoutS2CPacket.SlotData sel = TcpLoadoutData.slot(activeSlot);
            List<String> options = sel == null ? List.of() : sel.options();
            int rx = left + 10 + 104 + 8;
            if (options.isEmpty()) {
                g.drawString(this.font, "这个槽位没有可用装备", rx, top + 78, 0xFFFFAA66, false);
                g.drawString(this.font, "（配置里没写 / 皮肤未解锁）", rx, top + 92, 0xFF999999, false);
            }
            int pages = Math.max(1, (options.size() + PER_PAGE - 1) / PER_PAGE);
            if (pages > 1) {
                g.drawString(this.font, "第 " + (page + 1) + "/" + pages + " 页", rx + 50, top + panelH - 26, 0xFFCCCCCC, false);
            }
        }

        String hint = TcpLoadoutData.canEdit()
                ? "点右侧选择 · ESC 关闭"
                : (TcpLoadoutData.canSwitch()
                        ? "对局中：可以换背包，不能改装备 · ESC 关闭"
                        : "对局中不可修改装备 · ESC 关闭");
        g.drawString(this.font, hint, left + 10, top + panelH - 14, 0xFFAAAAAA, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private String slotNameOf(int slot) {
        return switch (slot) {
            case 0 -> "rifle";
            case 1 -> "pistol";
            case 2 -> "grenade";
            case 3 -> "utility";
            case 4 -> "knife";
            default -> "rifle";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
