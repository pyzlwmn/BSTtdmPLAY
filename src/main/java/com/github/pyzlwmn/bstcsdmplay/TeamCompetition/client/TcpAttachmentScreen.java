package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.GunItemFactory;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.LoadoutConfig;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpAttachmentSelectC2SPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpAttachmentS2CPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 配件编辑页（v27.2：大预览 + 真模型渲染 + 文本不出框）
 *
 * ┌─────────────────────────────┬────────────────────────────┐
 * │                             │ 瞄具：xxx                   │
 * │      大号枪械模型预览          │ 枪口：—                     │
 * │      （BEWLR 真模型 9×）      │ …                          │
 * │  [当前装配件摘要…]             │ ├ 配件列表（✔=已装）          │
 * │ [枪位1][枪位2]…               │ │ …                        │
 * └─────────────────────────────┴────────────────────────────┘
 */
public class TcpAttachmentScreen extends Screen {

    /** 额外放大倍数；总缩放 = 本值 × 16，Y 轴取负是翻转 */
    private static final float GUN_SCALE = 5.0f;
    /** 模型居中微调（单位：模型方格，1 = 16 像素；正值 X=右，Y=下）——模型偏了就改这两个 */
    private static final float GUN_OFFSET_X = 0.0f;
    private static final float GUN_OFFSET_Y = 0.0f;

    private static final int WANT_W = 440;
    private static final int WANT_H = 272;
    private static final int PER_PAGE = 9;

    private static final int PREVIEW_H = 150;
    private static final int LEFT_W = 168;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    private String activeGun = null;
    private String activeType = null;
    private int page = 0;
    private int builtVersion = -1;

    public TcpAttachmentScreen() {
        super(Component.literal("配件编辑"));
    }

    @Override
    protected void init() {
        this.panelW = Math.max(220, Math.min(WANT_W, this.width - 12));
        this.panelH = Math.max(170, Math.min(WANT_H, this.height - 12));
        this.left = Math.max(2, (this.width - panelW) / 2);
        this.top = Math.max(2, (this.height - panelH) / 2);
        List<TcpAttachmentS2CPacket.GunEntry> guns = TcpAttachmentData.guns();
        if (activeGun == null && !guns.isEmpty()) activeGun = guns.get(0).slotName();
        rebuild();
    }

    @Override
    public void tick() {
        if (builtVersion != TcpAttachmentData.version()) rebuild();
    }

    private TcpAttachmentS2CPacket.GunEntry currentGun() {
        return activeGun == null ? null : TcpAttachmentData.gun(activeGun);
    }

    private TcpAttachmentS2CPacket.TypeEntry currentType() {
        TcpAttachmentS2CPacket.GunEntry g = currentGun();
        if (g == null || activeType == null) return null;
        for (TcpAttachmentS2CPacket.TypeEntry t : g.types()) {
            if (t.type().equals(activeType)) return t;
        }
        return null;
    }

    // ================== 控件 ==================

    private void rebuild() {
        clearWidgets();
        builtVersion = TcpAttachmentData.version();

        boolean editable = TcpAttachmentData.canEdit();
        List<TcpAttachmentS2CPacket.GunEntry> guns = TcpAttachmentData.guns();
        int leftColW = Math.max(60, Math.min(LEFT_W, panelW / 2 - 24));

        // 预览框高度按面板高度自适应（给标题/摘要/枪位按钮/提示留出空间）
        int rowCount = Math.max(1, (guns.size() + 1) / 2);
        int previewH = Math.max(70, Math.min(PREVIEW_H, panelH - 96 - rowCount * 15));

        // 枪位按钮（预览框下方，两列）
        int gunY = top + 30 + previewH + 6;
        int halfW = (leftColW - 4) / 2;
        for (int i = 0; i < guns.size() && i < 6; i++) {
            TcpAttachmentS2CPacket.GunEntry g = guns.get(i);
            final String slotName = g.slotName();
            int col = i % 2;
            int row = i / 2;
            Button b = Button.builder(Component.literal((slotName.equals(activeGun) ? "▶" : " ") + fit(g.label(), halfW - 6)),
                            btn -> {
                                activeGun = slotName;
                                activeType = null;
                                page = 0;
                                rebuild();
                            })
                    .bounds(left + 10 + col * (halfW + 4), gunY + row * 15, halfW, 14)
                    .build();
            addRenderableWidget(b);
        }

        TcpAttachmentS2CPacket.GunEntry gun = currentGun();
        int rx = left + 10 + leftColW + 14;
        int rw = left + panelW - 10 - rx;
        int ry = top + 30;

        // 配件槽按钮
        int typeY = ry;
        if (gun != null) {
            for (int i = 0; i < gun.types().size(); i++) {
                TcpAttachmentS2CPacket.TypeEntry t = gun.types().get(i);
                final String type = t.type();
                String cur = t.current() == null ? "—" : TcpGunNames.attachmentName(t.current());
                String label = LoadoutConfig.typeLabel(type) + "：" + cur;
                Button b = Button.builder(Component.literal((type.equals(activeType) ? "▶ " : "  ")
                                + fit(label, rw - 8)),
                                btn -> {
                                    activeType = type;
                                    page = 0;
                                    rebuild();
                                })
                        .bounds(rx, typeY + i * 16, rw, 15)
                        .build();
                addRenderableWidget(b);
            }
            typeY += gun.types().size() * 16 + 4;
        }

        // 配件列表
        TcpAttachmentS2CPacket.TypeEntry t = currentType();
        if (t != null) {
            List<String> options = t.options();
            int pages = Math.max(1, (options.size() + PER_PAGE - 1) / PER_PAGE);
            if (page >= pages) page = pages - 1;
            if (page < 0) page = 0;

            int bottomReserve = 46;
            int rows = Math.max(3, (top + panelH - bottomReserve - typeY) / 15);
            int shown = Math.min(Math.min(PER_PAGE, rows), Math.max(0, options.size() - page * PER_PAGE));
            for (int i = 0; i < shown; i++) {
                String id = options.get(page * PER_PAGE + i);
                boolean selected = id.equals(t.current());
                Button b = Button.builder(Component.literal((selected ? "✔ " : "   ")
                                + fit(TcpGunNames.attachmentName(id), rw - 8)), btn ->
                                TcpNetwork.CHANNEL.sendToServer(
                                        new TcpAttachmentSelectC2SPacket(activeGun, activeType, selected ? "" : id)))
                        .bounds(rx, typeY + i * 15, rw, 14)
                        .build();
                b.active = editable;
                addRenderableWidget(b);
            }
            int btnY = top + panelH - 26;
            if (t.current() != null) {
                Button un = Button.builder(Component.literal("✖ 卸下"), btn ->
                                TcpNetwork.CHANNEL.sendToServer(
                                        new TcpAttachmentSelectC2SPacket(activeGun, activeType, "")))
                        .bounds(rx, btnY, 56, 16).build();
                un.active = editable;
                addRenderableWidget(un);
            }
            if (pages > 1) {
                addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
                    page = Math.max(0, page - 1);
                    rebuild();
                }).bounds(rx + rw - 44, btnY, 20, 16).build());
                addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
                    page = Math.min(pages - 1, page + 1);
                    rebuild();
                }).bounds(rx + rw - 22, btnY, 20, 16).build());
            }
        }
    }

    // ================== 绘制 ==================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        UiDraw.panel(g, left, top, panelW, panelH, UiDraw.RADIUS_LARGE, 0xFF3A4A5A, 0xE6101418);
        UiDraw.topBarRounded(g, left, top, panelW, 24, UiDraw.RADIUS_MEDIUM, 0xFF1B2A41);
        g.drawString(this.font, "🔧 配件编辑", left + 10, top + 8, 0xFFFFFFF0, false);

        if (!TcpAttachmentData.received()) {
            g.drawString(this.font, "读取中…", left + 14, top + 60, 0xFFCCCCCC, false);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        TcpAttachmentS2CPacket.GunEntry gun = currentGun();
        if (gun == null) {
            g.drawString(this.font, "没有可编辑的枪械（loadout.json 里没配枪 / TaCZ 没装）",
                    left + 14, top + 60, 0xFFFFAA66, false);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        // 预览框
        int leftColW = Math.max(60, Math.min(LEFT_W, panelW / 2 - 24));
        int rowCount = Math.max(1, (gun.types().isEmpty() ? 1 : 1));
        int previewH = Math.max(70, Math.min(PREVIEW_H, panelH - 96 - Math.max(1, (TcpAttachmentData.guns().size() + 1) / 2) * 15));
        int px = left + 10;
        int py = top + 30;
        UiDraw.panel(g, px, py, leftColW, previewH, UiDraw.RADIUS_MEDIUM, 0xFF2A3A4A, 0xFF141C24);

        // 大号模型渲染：直接调 TaCZ 的 BEWLR，但要补上原版 GUI 的 (16, -16, 16) 归一化与 Y 翻转
        ItemStack preview = previewStack(gun);
        if (!preview.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            var buffers = mc.renderBuffers().bufferSource();
            float cx = px + leftColW / 2.0f;
            float cy = py + previewH / 2.0f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 100f);
            g.pose().scale(GUN_SCALE, GUN_SCALE, GUN_SCALE);
            boolean rendered = false;
            try {
                var ext = net.minecraftforge.client.extensions.common.IClientItemExtensions.of(preview);
                var bewlr = ext == null ? null : ext.getCustomRenderer();
                if (bewlr != null && bewlr.getClass().getName().startsWith("com.tacz")) {
                    g.pose().pushPose();
                    g.pose().scale(16f, -16f, 16f);      // ★ 方块单位 + GUI 的 Y 翻转
                    if (GUN_OFFSET_X != 0f || GUN_OFFSET_Y != 0f) {
                        g.pose().translate(GUN_OFFSET_X, GUN_OFFSET_Y, 0f);
                    }
                    // ★ 必须用 FIXED（模型）：TaCZ 的 display 里 GUI 对应的是 2D “slot 贴图”，
                    //   用 GUI 上下文就变成平面图标（糊）
                    bewlr.renderByItem(preview, ItemDisplayContext.FIXED, g.pose(), buffers,
                            0xF000F0, OverlayTexture.NO_OVERLAY);
                    g.pose().popPose();
                    rendered = true;
                }
            } catch (Throwable ignored) {
            }
            if (!rendered) {
                mc.getItemRenderer().renderStatic(preview, ItemDisplayContext.FIXED, 0xF000F0,
                        OverlayTexture.NO_OVERLAY, g.pose(), buffers, mc.level, 0);
            }
            g.pose().popPose();
            buffers.endBatch();
        }

        // 已装配件摘要（贴在预览框底部，超宽截断）
        StringBuilder sb = new StringBuilder();
        for (TcpAttachmentS2CPacket.TypeEntry t : gun.types()) {
            if (t.current() == null) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(LoadoutConfig.typeLabel(t.type())).append(' ').append(TcpGunNames.attachmentName(t.current()));
        }
        g.drawString(this.font, fit(sb.length() == 0 ? "（未装配件）" : sb.toString(), leftColW - 8),
                px + 4, py + previewH - 13, 0xFFBBD0E0, false);

        String hint = TcpAttachmentData.canEdit() ? "按 ESC 返回"
                : "ESC 返回";
        g.drawString(this.font, fit(hint, panelW - 20), left + 10, top + panelH - 14, 0xFFAAAAAA, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 预览用：把当前选中的配件也挂上去 */
    private ItemStack previewStack(TcpAttachmentS2CPacket.GunEntry gun) {
        try {
            java.util.Map<String, String> atts = new java.util.LinkedHashMap<>();
            for (TcpAttachmentS2CPacket.TypeEntry t : gun.types()) {
                if (t.current() != null && !t.current().isBlank()) atts.put(t.type(), t.current());
            }
            return GunItemFactory.create(gun.gunId(), 30, 300, "AUTO", atts);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 按像素宽度截断（超出加 …），保证不出框 */
    private String fit(String text, int maxPx) {
        if (text == null) return "";
        if (this.font.width(text) <= maxPx) return text;
        String ellipsis = "…";
        int w = this.font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            int cw = this.font.width(String.valueOf(c));
            if (w + cw > maxPx) break;
            sb.append(c);
            w += cw;
        }
        return sb + ellipsis;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
