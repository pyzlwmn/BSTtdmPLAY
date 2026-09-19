package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpAttachmentRequestC2SPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpLoadoutPresetC2SPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpLoadoutS2CPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpLoadoutSelectC2SPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpNetwork;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpRefitC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 背包 / 装备编辑 UI（按 B 打开，v24：多背包 + 布局不越界）
 *
 * ┌──────────────────────────────────────────────────────────┐
 * │ 🎒 装备编辑                                  对局中/未开局 │
 * │ [背包1][背包2]… [＋][✎][✕]              ← 背包（预设）行   │
 * │ ┌──────────┐ ┌─────────────────────────────────────────┐ │
 * │ │槽位1 步枪│ │ 可选装备（✔=当前，翻页 ◀ ▶）             │ │
 * │ │槽位2 手枪│ │                                          │ │
 * │ │…         │ │                                          │ │
 * │ │🔧 配件编辑│ │                                          │ │
 * │ └──────────┘ └─────────────────────────────────────────┘ │
 * └──────────────────────────────────────────────────────────┘
 */
public class TcpLoadoutScreen extends Screen {

    /** 面板期望尺寸（会按窗口大小收缩，保证不越界） */
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

    // ================== 控件 ==================

    private void rebuild() {
        clearWidgets();
        nameBox = null;
        builtVersion = TcpLoadoutData.version();

        boolean editable = TcpLoadoutData.canEdit();
        int innerX = left + 10;
        int innerW = panelW - 20;

        // ===== 背包（预设）行 =====
        List<TcpLoadoutS2CPacket.PresetData> presets = TcpLoadoutData.presets();
        int presetArea = innerW - 3 * 22;                    // 右边留 [+][改][删]
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
            b.active = editable;
            addRenderableWidget(b);
            px += pw + 2;
        }
        int bx = innerX + innerW - 3 * 22 + 2;
        int maxPresets = TcpLoadoutData.maxPresets();
        Button addB = Button.builder(Component.literal("＋"), btn ->
                        TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket("create", "", "")))
                .bounds(bx, py, 20, 16).build();
        addB.active = editable && presets.size() < maxPresets;
        addRenderableWidget(addB);

        Button renameB = Button.builder(Component.literal("✎"), btn -> {
                    renaming = !renaming;
                    rebuild();
                })
                .bounds(bx + 22, py, 20, 16).build();
        renameB.active = editable;
        addRenderableWidget(renameB);

        TcpLoadoutS2CPacket.PresetData act = TcpLoadoutData.activePreset();
        Button delB = Button.builder(Component.literal("✕"), btn -> {
                    if (act != null) {
                        TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutPresetC2SPacket("delete", act.id(), ""));
                    }
                })
                .bounds(bx + 44, py, 20, 16).build();
        delB.active = editable && presets.size() > 1;
        addRenderableWidget(delB);

        // 改名输入框
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

        // ===== 左列：槽位 + 配件入口 =====
        int colY = top + 74;
        List<TcpLoadoutS2CPacket.SlotData> allSlots = TcpLoadoutData.slots();
        int colW = 104;
        for (int i = 0; i < allSlots.size(); i++) {
            TcpLoadoutS2CPacket.SlotData data = allSlots.get(i);
            final int slot = data.slot();
            Button b = Button.builder(Component.literal((slot == activeSlot ? "▶ " : "  ") + data.label()),
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
        Button refit = Button.builder(Component.literal("🔩 改装"), btn ->
                        TcpNetwork.CHANNEL.sendToServer(new TcpRefitC2SPacket("start", slotNameOf(activeSlot))))
                .bounds(innerX, toolY, colW, SLOT_BTN_H - 2).build();
        refit.active = editable && !TcpLoadoutData.inMatch();     // v25：局内不可改装
        addRenderableWidget(refit);
        toolY += SLOT_BTN_H;

        Button att = Button.builder(Component.literal("🔧 配件编辑"), btn ->
                        TcpNetwork.CHANNEL.sendToServer(new TcpAttachmentRequestC2SPacket()))
                .bounds(innerX, toolY, colW, SLOT_BTN_H - 2).build();
        att.active = editable;
        addRenderableWidget(att);

        // ===== 右侧：可选项 =====
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
            Button b = Button.builder(Component.literal((selected ? "✔ " : "   ") + TcpGunNames.display(id)), btn ->
                            TcpNetwork.CHANNEL.sendToServer(new TcpLoadoutSelectC2SPacket(activeSlot, id)))
                    .bounds(rx, ry + i * OPT_BTN_H, rw, OPT_BTN_H - 1)
                    .build();
            b.active = editable;
            addRenderableWidget(b);
        }

        // 翻页（放在面板底部内侧）
        if (pages > 1) {
            int pgY = top + panelH - 30;
            addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
                page = Math.max(0, page - 1);
                rebuild();
            }).bounds(rx, pgY, 20, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
                page = Math.min(pages - 1, page + 1);
                rebuild();
            }).bounds(rx + 24, pgY, 20, 16).build());
        }
    }

    // ================== 绘制 ==================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        UiDraw.panel(g, left, top, panelW, panelH, UiDraw.RADIUS_LARGE, 0xFF3A4A5A, 0xE6101418);
        UiDraw.topBarRounded(g, left, top, panelW, 24, UiDraw.RADIUS_MEDIUM, 0xFF1B2A41);
        g.drawString(this.font, "🎒 装备编辑", left + 10, top + 8, 0xFFFFFFF0, false);

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
                ? "点击右侧装备选择 · ✎ 改背包名 · ＋ 新建背包 · 🔧 配件编辑 · ESC 关闭"
                : "对局中不可修改装备 · ESC 关闭";
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
