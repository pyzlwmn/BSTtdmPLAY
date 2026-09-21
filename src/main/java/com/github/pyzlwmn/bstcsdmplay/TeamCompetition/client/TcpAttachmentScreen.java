package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.GunItemFactory;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.LoadoutConfig;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpAttachmentSelectC2SPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpAttachmentS2CPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.math.Axis;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 配件编辑页（v30：大预览 + 左键拖动旋转 + 悬停试装预览 + 图标去 emoji）
 *
 * ┌─────────────────────────────┬────────────────────────────┐
 * │                             │ 瞄具：xxx                   │
 * │      大号枪械模型预览          │ 枪口：—                     │
 * │      （BEWLR 真模型 9×）      │ …                          │
 * │  [当前装配件摘要…]             │ ├ 配件列表（>=已装）          │
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

    // ================== v30：左键拖动旋转 ==================
    /** 初始偏角（给一点角度看起来更立体） */
    private static final float ROT_START_Y = 25.0f;
    /** 俯仰限幅 */
    private static final float ROT_MAX_X = 70.0f;
    private float rotY = ROT_START_Y;
    private float rotX = 0.0f;
    private boolean dragging = false;
    private double dragAnchorX = 0.0;
    private double dragAnchorY = 0.0;
    private float dragBaseRotY = 0.0f;
    private float dragBaseRotX = 0.0f;

    // ================== v30：悬停试装预览 ==================
    /** 当前鼠标悬停的配件（type / id），仅客户端，不写服务端 */
    private String hoverType = null;
    private String hoverId = null;
    /** 配件列表按钮 + 对应 {type, id}，用于鼠标命中检测 */
    private final List<AbstractWidget> listButtons = new ArrayList<>();
    private final List<String[]> listButtonIds = new ArrayList<>();

    // ================== v30：预览框几何（rebuild 算好，render/鼠标事件共用）==================
    private int pvX;
    private int pvY;
    private int pvW;
    private int pvH;

    // ================== v32：预览枪缓存 ==================
    /** 之前每帧重建预览枪 → 每帧刷 7 行日志 + 反射开销；改成「组合没变就不重建」 */
    private ItemStack cachedPreview = ItemStack.EMPTY;
    private String cachedPreviewKey = null;

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
        listButtons.clear();
        listButtonIds.clear();
        builtVersion = TcpAttachmentData.version();

        boolean editable = TcpAttachmentData.canEdit();
        List<TcpAttachmentS2CPacket.GunEntry> guns = TcpAttachmentData.guns();
        int leftColW = Math.max(60, Math.min(LEFT_W, panelW / 2 - 24));

        // 预览框高度按面板高度自适应（给标题/摘要/枪位按钮/提示留出空间）
        int rowCount = Math.max(1, (guns.size() + 1) / 2);
        int previewH = Math.max(70, Math.min(PREVIEW_H, panelH - 96 - rowCount * 15));
        // v30：预览框几何统一在这里算，render() 与鼠标事件共用
        this.pvX = left + 10;
        this.pvY = top + 30;
        this.pvW = leftColW;
        this.pvH = previewH;

        // 枪位按钮（预览框下方，两列）
        int gunY = top + 30 + previewH + 6;
        int halfW = (leftColW - 4) / 2;
        for (int i = 0; i < guns.size() && i < 6; i++) {
            TcpAttachmentS2CPacket.GunEntry g = guns.get(i);
            final String slotName = g.slotName();
            int col = i % 2;
            int row = i / 2;
            Button b = Button.builder(Component.literal((slotName.equals(activeGun) ? ">" : " ") + fit(g.label(), halfW - 6)),
                            btn -> {
                                activeGun = slotName;
                                activeType = null;
                                page = 0;
                                rotY = ROT_START_Y;      // v30：换枪复位角度
                                rotX = 0.0f;
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
                Button b = Button.builder(Component.literal((type.equals(activeType) ? "> " : "  ")
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
                Button b = Button.builder(Component.literal((selected ? "> " : "   ")
                                + fit(TcpGunNames.attachmentName(id), rw - 8)), btn ->
                                TcpNetwork.CHANNEL.sendToServer(
                                        new TcpAttachmentSelectC2SPacket(activeGun, activeType, selected ? "" : id)))
                        .bounds(rx, typeY + i * 15, rw, 14)
                        .build();
                b.active = editable;
                addRenderableWidget(b);
                listButtons.add(b);                              // v30：悬停命中检测
                listButtonIds.add(new String[]{activeType, id});
            }
            int btnY = top + panelH - 26;
            if (t.current() != null) {
                Button un = Button.builder(Component.literal("卸下"), btn ->
                                TcpNetwork.CHANNEL.sendToServer(
                                        new TcpAttachmentSelectC2SPacket(activeGun, activeType, "")))
                        .bounds(rx, btnY, 56, 16).build();
                un.active = editable;
                addRenderableWidget(un);
            }
            if (pages > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
                    page = Math.max(0, page - 1);
                    rebuild();
                }).bounds(rx + rw - 44, btnY, 20, 16).build());
                addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
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

        // v38：投影 + 渐变 + 描边 + 金色顶条
        UiDraw.panelEx(g, left, top, panelW, panelH, UiDraw.RADIUS_LARGE, 0xFF3A4A5A, 0xF01A2432, 0xE60D131C, 0xFFFFC93C);
        UiDraw.topBarRounded(g, left, top, panelW, 24, UiDraw.RADIUS_LARGE, 0xFF1B2A41);   // ★ v44：与面板同半径，避免上两角变方
        g.drawString(this.font, "配件编辑", left + 10, top + 8, 0xFFFFFFF0, false);

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

        // v30：悬停配件列表项 → 实时试装预览（只改本地预览，不写服务端）
        hoverType = null;
        hoverId = null;
        AbstractWidget hoverW = null;
        if (TcpAttachmentData.canEdit()) {
            for (int i = 0; i < listButtons.size() && i < listButtonIds.size(); i++) {
                AbstractWidget w = listButtons.get(i);
                if (mouseX >= w.getX() && mouseX < w.getX() + w.getWidth()
                        && mouseY >= w.getY() && mouseY < w.getY() + w.getHeight()) {
                    hoverType = listButtonIds.get(i)[0];
                    hoverId = listButtonIds.get(i)[1];
                    hoverW = w;
                    break;
                }
            }
        }
        // v39：悬停项加 1px 金色描边（更明确的反馈）
        if (hoverW != null) {
            int ox = hoverW.getX() - 1, oy = hoverW.getY() - 1;
            int ow = hoverW.getWidth() + 2, oh = hoverW.getHeight() + 2;
            g.fill(ox, oy, ox + ow, oy + 1, 0xFFFFC93C);
            g.fill(ox, oy + oh - 1, ox + ow, oy + oh, 0xFFFFC93C);
            g.fill(ox, oy + 1, ox + 1, oy + oh - 1, 0xFFFFC93C);
            g.fill(ox + ow - 1, oy + 1, ox + ow, oy + oh - 1, 0xFFFFC93C);
        }

        // 预览框（几何在 rebuild() 里算好）
        int px = pvX;
        int py = pvY;
        int leftColW = pvW;
        int previewH = pvH;
        // v38：预览框 = 描边（悬停金）+ 渐变底
        UiDraw.fillRounded(g, px - 1, py - 1, leftColW + 2, previewH + 2, UiDraw.RADIUS_MEDIUM + 1,
                hoverId != null ? 0xFFFFC93C : 0xFF2A3A4A);
        UiDraw.fillVGradient(g, px, py, leftColW, previewH, UiDraw.RADIUS_MEDIUM, 0xFF1B2634, 0xFF0E141C);

        // 大号模型渲染：直接调 TaCZ 的 BEWLR，但要补上原版 GUI 的 (16, -16, 16) 归一化与 Y 翻转
        ItemStack preview = previewStack(gun, hoverType, hoverId);
        if (!preview.isEmpty()) {
            // ★ v49：告诉 TaCZ「我们现在是 GUI 渲染」→ 它才会走「高模（含配件）」
            markTaczGuiRender();
            Minecraft mc = Minecraft.getInstance();
            var buffers = mc.renderBuffers().bufferSource();
            float cx = px + leftColW / 2.0f;
            float cy = py + previewH / 2.0f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 100f);
            g.pose().scale(GUN_SCALE, GUN_SCALE, GUN_SCALE);
            // ★ v30：左键拖动旋转（绕模型中心；先 Y 后 X，X 限幅）
            g.pose().mulPose(Axis.YP.rotationDegrees(rotY));
            g.pose().mulPose(Axis.XP.rotationDegrees(rotX));
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
                    bewlr.renderByItem(preview, previewContext(), g.pose(), buffers,
                            0xF000F0, OverlayTexture.NO_OVERLAY);
                    g.pose().popPose();
                    rendered = true;
                }
            } catch (Throwable ignored) {
            }
            if (!rendered) {
                mc.getItemRenderer().renderStatic(preview, previewContext(), 0xF000F0,
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

        String hint = TcpAttachmentData.canEdit()
                ? "拖动旋转 · 右键复位 · ESC 返回"
                : "对局中不可修改配件 · ESC 返回";
        g.drawString(this.font, fit(hint, panelW - 20), left + 10, top + panelH - 14, 0xFFAAAAAA, false);

        // v30：预览中提示（悬停时显示在预览框左上角）——v32 加上槽位类型名 + 配件图标
        if (hoverId != null && !hoverId.isBlank()) {
            String label = LoadoutConfig.typeLabel(hoverType) + " " + TcpGunNames.attachmentName(hoverId);
            g.drawString(this.font, fit("预览：" + label, leftColW - 8), px + 4, py + 4, 0xFFFFC93C, true);
            try {
                ItemStack hoverAtt = GunItemFactory.createAttachment(hoverId);
                if (!hoverAtt.isEmpty()) {
                    g.renderItem(hoverAtt, px + leftColW - 22, py + previewH - 20);
                }
            } catch (Throwable ignored) {
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * ★ v49（关键修复）：TaCZ 的枪模渲染器用
     *   `RenderDistance.inRenderHighPolyModelDistance(poseStack)` 决定画「高模（含配件）」还是「低模」，
     *   而这个方法**第一句就是 `isGuiRender()`** —— 只有调用过 `markGuiRenderTimestamp()` 才算 GUI 渲染。
     *
     * 我们之前没调 → TaCZ 当成非 GUI（世界/远景）渲染 → 走低模 → 枪身画得出来、配件全不画。
     * （反汇编 GunItemRendererWrapper#lambda$renderByItem$6 与 RenderDistance#inRenderHighPolyModelDistance 实锤）
     */
    private static void markTaczGuiRender() {
        try {
            Class.forName("com.tacz.guns.util.RenderDistance")
                    .getMethod("markGuiRenderTimestamp")
                    .invoke(null);
        } catch (Throwable ignored) {
        }
    }

    /** ★ v46：预览渲染上下文（可在 config/bstcsdmplay/loadout.json 改 previewContext） */
    private static ItemDisplayContext previewContext() {
        try {
            return ItemDisplayContext.valueOf(LoadoutConfig.previewContext());
        } catch (Throwable t) {
            return ItemDisplayContext.FIXED;
        }
    }

    /**
     * 预览用：把当前选中的配件挂上；若鼠标悬停在某个配件上，用它临时顶替该槽位（纯客户端，不写服务端）
     * v32：结果按「枪 + 配件组合」缓存，组合没变就直接复用（修每帧重建 / 日志刷屏）
     */
    private ItemStack previewStack(TcpAttachmentS2CPacket.GunEntry gun, String hType, String hId) {
        try {
            java.util.Map<String, String> atts = new java.util.LinkedHashMap<>();
            for (TcpAttachmentS2CPacket.TypeEntry t : gun.types()) {
                if (t.current() != null && !t.current().isBlank()) atts.put(t.type(), t.current());
            }
            if (hType != null && hId != null && !hId.isBlank()) atts.put(hType, hId);

            String key = gun.gunId() + "#" + atts;
            if (key.equals(cachedPreviewKey)) return cachedPreview;

            int[] na = GunItemFactory.nativeAmmo(gun.gunId());      // ★ v43：原版弹匣 + 备弹 ×3
            ItemStack stack = GunItemFactory.create(gun.gunId(), na[0], na[1], "NATIVE", atts);
            cachedPreviewKey = key;
            cachedPreview = stack;
            return stack;
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

    // ================== v30：左键拖动旋转 / 右键复位 ==================

    /** 鼠标是否落在预览框内 */
    private boolean inPreview(double mx, double my) {
        return mx >= pvX && mx < pvX + pvW && my >= pvY && my < pvY + pvH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (!inPreview(mx, my)) return false;
        if (button == 1) {                     // 右键：复位角度
            rotY = ROT_START_Y;
            rotX = 0.0f;
            return true;
        }
        if (button == 0) {                     // 左键：开始拖动
            dragging = true;
            dragAnchorX = mx;
            dragAnchorY = my;
            dragBaseRotY = rotY;
            dragBaseRotX = rotX;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (dragging) {
            rotY = dragBaseRotY + (float) (mx - dragAnchorX) * 1.2f;
            rotX = Math.max(-ROT_MAX_X, Math.min(ROT_MAX_X,
                    dragBaseRotX + (float) (my - dragAnchorY) * 1.0f));
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
