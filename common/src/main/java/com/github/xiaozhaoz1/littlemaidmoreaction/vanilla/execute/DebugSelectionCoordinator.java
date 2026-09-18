package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.BlockPatternCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

/**
 * 选区高亮调试协调器 (v79.61x, 用户裁定落位 execute 层) — 右键标记方块起点 → 终点 →
 * AABB 线框 + 网格 + 尺寸文字持续显示 → 清除。
 *
 * <p><b>交互兼容 (用户裁定 — 木棒右键与既有标记/搬运重叠)</b>: 既有语义 —
 * {@code BlockInteractSetupHandler} (木棒右键非容器方块 = 标记交互目标) /
 * {@code ArmTransferSetupHandler} (容器 = 搬运绑定) 均不检查潜行。
 * 选区标记 = <b>潜行 + 木棒右键</b> (客户端拦截 cancel → 服务端事件不 fire → 既有语义零冲突);
 * 非潜行木棒右键照旧。
 *
 * <p><b>渲染</b>: 双平台 RenderLevelStageEvent (forge 1.20.1 / neoforge 1.21.1, javap 实证
 * getStage/getPoseStack/getCamera + Stage.AFTER_BLOCK_ENTITIES 同值), 由客户端入口注册;
 * 起点/终点 0.03 内缩方块框 (WorldEditCUI PointCube 同款) + 区域 12 边线框 +
 * 1 格间距 3D 网格 (上限保护) + 尺寸文字 W×H×D + <b>缓存区块边界叠加</b>
 * (BlockPatternCache / EntityScanCache coveredChunks — 可视化扫描覆盖区, 用户裁定)。
 *
 * <p>纯客户端 (OnlyIn.CLIENT) — 仅被 LmaForgeClientEntry / LmaNeoForgeClientEntry 引用,
 * 专用服务器不加载; 断线/切维度/第三击清除。
 */
@OnlyIn(Dist.CLIENT)
public final class DebugSelectionCoordinator {

    private DebugSelectionCoordinator() {}

    /** 方块框内缩 (WorldEditCUI PointCube.PADDING 同款 — 选框稍小于方块不遮挡本体) */
    private static final double PAD = 0.03;

    /** 区域框外扩 (2026-08-16 用户裁定: 蓝色区域框要包住端点黄/红盒并明显更大 — 与端点盒 PAD 相同会边界重合叠一起) */
    private static final double REGION_PAD = 0.06;

    /** 网格间距 (格) */
    private static final double GRID_SPACING = 1.0;

    /** 网格/线框边长上限 (格) — 超大选区防爆线 (超过则只画外框) */
    private static final double MAX_GRID_EDGE = 32.0;
    /** 网格最小边长 (格) — 2026-08-16 用户实测: 2 格小区域画网格视觉像 6 格 → 边长 <2 只外框 */
    private static final double MIN_GRID_EDGE = 2.0;

    public enum Phase { NONE, STARTED, SELECTED }

    private static Phase phase = Phase.NONE;
    private static BlockPos start = null;
    private static BlockPos end = null;
    private static String dim = "";

    /**
     * 木棒判定 (标记/绑定物品) — **由客户端入口注入**。
     *
     * <p>v79.63 架构审计 A4: 本类原直接 import {@code event.StickBindUtil} (vanilla → event 反向越层)。
     * 判定本质需要 config (哪个物品算木棒), 而 vanilla 层不得读 config/event → 改为**注入**:
     * 认识两边的客户端入口 ({@code LmaForgeClientEntry} / {@code LmaNeoForgeClientEntry}) 在 setup 时注入一次。
     *
     * <p>未注入时默认 false → 选区调试功能静默禁用 (纯调试工具, 不影响玩法)。
     */
    private static java.util.function.Predicate<ItemStack> stickCheck = stack -> false;

    /** 注入木棒判定 (客户端 setup 调用一次; null → 复位为禁用) */
    public static void bindStickCheck(java.util.function.Predicate<ItemStack> check) {
        stickCheck = check == null ? stack -> false : check;
    }

    // ── 交互 (客户端入口监听 PlayerInteractEvent.RightClickBlock 调用) ──

    /** 选区判定 — 潜行 + 木棒 (标记/绑定物品) → 记录选区点 (纯观察, 不 cancel 事件流 — 用户裁定) */
    public static boolean isSelectionClick(ItemStack held, boolean shiftDown) {
        return shiftDown && stickCheck.test(held);
    }

    /** 手持木棍判定 (渲染门控 — 2026-08-16 用户裁定: 有木棍才显示选区; 主手/副手任一) */
    private static boolean holdingStick() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return stickCheck.test(main) || stickCheck.test(off);
    }

    /**
     * 状态机推进 (2026-08-16 用户裁定重设计): 点 A 保存起点 (黄盒) → 点 B 保存终点 (区域,
     * 含 A、B 两方块) → 点 C 开新一轮 (新起点, 无清除态 — 区域总显示最近两点; 原「第三击
     * 清除」导致用户误以为区域没保存/按旧区域算)。
     */
    public static void advance(Level level, BlockPos pos) {
        String curDim = level.dimension().location().toString();
        if (phase == Phase.SELECTED) {
            // 新一轮起点 — 旧 start/end 作废, 新点成为起点
            start = pos;
            end = null;
            dim = curDim;
            phase = Phase.STARTED;
        } else if (phase == Phase.NONE || !curDim.equals(dim)) {
            start = pos;
            end = null;
            dim = curDim;
            phase = Phase.STARTED;
        } else { // STARTED → 终点, 出区域 (含两方块)
            end = pos;
            phase = Phase.SELECTED;
        }
        // 2026-08-16 选区定位日志 — INFO 层可见实际方块位
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                "[LMA/Selection] phase={} dim={} pos=({}, {}, {})", phase, curDim, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void clear() {
        phase = Phase.NONE;
        start = null;
        end = null;
        dim = "";
    }

    public static Phase getPhase() { return phase; }

    /**
     * 当前选区 AABB (SELECTED 且双端点时, 归一化 + 半开 +1; 否则 null) — v79.62 供
     * 作物区域注册 (GUI「添加当前选区」读取: 选区 → FarmRegionStorage).
     */
    @javax.annotation.Nullable
    public static AABB getRegionAABB() {
        if (phase != Phase.SELECTED || start == null || end == null) return null;
        // v79.62.1 Y 轴 ±1 扩展: 下扩 1 含耕地层 (玩家只框选作物层时耕地也在区域);
        // 上扩 1 含甘蔗/竹/仙人掌多格高度 (半开区间 max 不含末端 → +2 = 上扩 1 + 半开 +1)
        return new AABB(
                Math.min(start.getX(), end.getX()),
                Math.min(start.getY(), end.getY()) - 1,
                Math.min(start.getZ(), end.getZ()),
                Math.max(start.getX(), end.getX()) + 1,
                Math.max(start.getY(), end.getY()) + 2,
                Math.max(start.getZ(), end.getZ()) + 1);
    }

    // ── 渲染 (客户端入口 RenderLevelStageEvent 调用) ──

    public static void render(PoseStack poseStack, Camera camera, MultiBufferSource buffers) {
        if (phase == Phase.NONE) return;
        if (!holdingStick()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 cam = camera.getPosition().reverse();
        poseStack.pushPose();
        poseStack.translate(cam.x, cam.y, cam.z);

        if (start != null) {
            // 起点: 描边线框 + 低 alpha 半透明填充
            drawBox(poseStack, buffers, start, 1.0F, 0.95F, 0.2F);
            DebugRenderer.renderFilledBox(poseStack, buffers, new AABB(start), 1.0F, 0.95F, 0.2F, 0.20F);
        }
        if (phase == Phase.SELECTED && start != null && end != null) {
            drawBox(poseStack, buffers, end, 1.0F, 0.4F, 0.4F);
            DebugRenderer.renderFilledBox(poseStack, buffers, new AABB(end), 1.0F, 0.4F, 0.4F, 0.20F);
            AABB region = getRegionAABB();
            if (region != null) {
                // Create 风格: 细线框 + 低 alpha 半透明填充 (中间空只剩边框感)
                drawEdges(poseStack, buffers, region, 0.3F, 0.9F, 1.0F);   // 青色线框
                DebugRenderer.renderFilledBox(poseStack, buffers, region, 0.3F, 0.9F, 1.0F, 0.12F);
            }
        }
        drawChunkOverlay(poseStack, buffers, level);

        poseStack.popPose();
    }

    // ── 渲染原语 (私有助手 — 供区块叠加等复用) ──

    /** 单方块 12 边线框 — PAD 外扩画在方块外表面外侧 */
    private static void drawBox(PoseStack pose, MultiBufferSource buffers, BlockPos pos,
                                float r, float g, float b) {
        VertexConsumer vc = buffers.getBuffer(RenderType.LINES);
        double x0 = pos.getX() - PAD, y0 = pos.getY() - PAD, z0 = pos.getZ() - PAD;
        double x1 = pos.getX() + 1 + PAD, y1 = pos.getY() + 1 + PAD, z1 = pos.getZ() + 1 + PAD;
        edge(vc, pose, x0, y0, z0, x1, y0, z0, r, g, b);
        edge(vc, pose, x1, y0, z0, x1, y0, z1, r, g, b);
        edge(vc, pose, x1, y0, z1, x0, y0, z1, r, g, b);
        edge(vc, pose, x0, y0, z1, x0, y0, z0, r, g, b);
        edge(vc, pose, x0, y1, z0, x1, y1, z0, r, g, b);
        edge(vc, pose, x1, y1, z0, x1, y1, z1, r, g, b);
        edge(vc, pose, x1, y1, z1, x0, y1, z1, r, g, b);
        edge(vc, pose, x0, y1, z1, x0, y1, z0, r, g, b);
        edge(vc, pose, x0, y0, z0, x0, y1, z0, r, g, b);
        edge(vc, pose, x1, y0, z0, x1, y1, z0, r, g, b);
        edge(vc, pose, x1, y0, z1, x1, y1, z1, r, g, b);
        edge(vc, pose, x0, y0, z1, x0, y1, z1, r, g, b);
    }

    /** AABB 12 边线框 — REGION_PAD 外扩; Create 风格双绘: 外层亮色 + 内层暗色 = 发光层次 */
    private static void drawEdges(PoseStack pose, MultiBufferSource buffers, AABB box,
                                  float r, float g, float b) {
        VertexConsumer vc = buffers.getBuffer(RenderType.LINES);
        double x0 = box.minX - REGION_PAD, y0 = box.minY - REGION_PAD, z0 = box.minZ - REGION_PAD;
        double x1 = box.maxX + REGION_PAD, y1 = box.maxY + REGION_PAD, z1 = box.maxZ + REGION_PAD;
        edge(vc, pose, x0, y0, z0, x1, y0, z0, r, g, b);
        edge(vc, pose, x1, y0, z0, x1, y0, z1, r, g, b);
        edge(vc, pose, x1, y0, z1, x0, y0, z1, r, g, b);
        edge(vc, pose, x0, y0, z1, x0, y0, z0, r, g, b);
        edge(vc, pose, x0, y1, z0, x1, y1, z0, r, g, b);
        edge(vc, pose, x1, y1, z0, x1, y1, z1, r, g, b);
        edge(vc, pose, x1, y1, z1, x0, y1, z1, r, g, b);
        edge(vc, pose, x0, y1, z1, x0, y1, z0, r, g, b);
        edge(vc, pose, x0, y0, z0, x0, y1, z0, r, g, b);
        edge(vc, pose, x1, y0, z0, x1, y1, z0, r, g, b);
        edge(vc, pose, x1, y0, z1, x1, y1, z1, r, g, b);
        edge(vc, pose, x0, y0, z1, x0, y1, z1, r, g, b);
    }

    /** 3D 网格线 (间距 GRID_SPACING; 边长超上限 → 跳过网格只留外框) */
    private static void drawGrid(PoseStack pose, MultiBufferSource buffers, AABB box,
                                 float r, float g, float b) {
        // 2026-08-16 修: AABB 半开区间 — 边长 = maxX-minX (不加 1), 线边界 = box.maxX (不 +1)
        double dx = box.maxX - box.minX;
        double dy = box.maxY - box.minY;
        double dz = box.maxZ - box.minZ;
        if (dx > MAX_GRID_EDGE || dy > MAX_GRID_EDGE || dz > MAX_GRID_EDGE) return;
        if (dx < MIN_GRID_EDGE || dy < MIN_GRID_EDGE || dz < MIN_GRID_EDGE) return;  // 小区域只外框
        VertexConsumer vc = buffers.getBuffer(RenderType.LINES);
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
        double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        // 2026-08-16 改: 3D 体内部网格被方块模型遮挡看不见 → 只画顶面水平网格;
        // gy = box.maxY + REGION_PAD 曾浮空 1 格 (半开区间 maxY 已是区域顶面边界, 再 +0.12 浮到
        // 上方第 2 格 — 用户实测「中间一条缝里的蓝色」) → 贴顶面外侧 +0.01
        double gy = box.maxY + 0.01;
        // X 方向线 (顶面)
        for (double x = x0 + GRID_SPACING; x < x1; x += GRID_SPACING) {
            edge(vc, pose, x, gy, z0, x, gy, z1, r, g, b);
        }
        // Z 方向线 (顶面)
        for (double z = z0 + GRID_SPACING; z < z1; z += GRID_SPACING) {
            edge(vc, pose, x0, gy, z, x1, gy, z, r, g, b);
        }
    }

    /** HUD 尺寸文字 (屏幕层 — GuiGraphics.drawString 双平台可靠; 选中时左上角显示 W×H×D;
     *  2026-08-16: 加木棍门控 — 放下木棍文字也消失, 与渲染一致) */
    public static void drawHud(net.minecraft.client.gui.GuiGraphics gui) {
        if (phase != Phase.SELECTED || start == null || end == null) return;
        if (!holdingStick()) return;
        int w = Math.abs(end.getX() - start.getX()) + 1;
        int h = Math.abs(end.getY() - start.getY()) + 1;
        int d = Math.abs(end.getZ() - start.getZ()) + 1;
        gui.drawString(Minecraft.getInstance().font,
                Component.literal("§b选区: " + w + "x" + h + "x" + d),
                4, 4, 0xFFFFFFFF);
    }

    /** 缓存区块边界叠加 — BlockPatternCache + EntityScanCache 覆盖区块 (水平 16×16 框) */
    private static void drawChunkOverlay(PoseStack pose, MultiBufferSource buffers, Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        // 画在玩家脚底水平面 (y 固定 — 区块框)
        double y = Minecraft.getInstance().player.getY() - 1;
        VertexConsumer vc = buffers.getBuffer(RenderType.LINES);
        for (long[] c : BlockPatternCache.coveredChunks(sl)) {
            double x0 = c[0] * 16, z0 = c[1] * 16;
            edge(vc, pose, x0, y, z0, x0 + 16, y, z0, 0.4F, 1.0F, 0.4F);
            edge(vc, pose, x0 + 16, y, z0, x0 + 16, y, z0 + 16, 0.4F, 1.0F, 0.4F);
            edge(vc, pose, x0 + 16, y, z0 + 16, x0, y, z0 + 16, 0.4F, 1.0F, 0.4F);
            edge(vc, pose, x0, y, z0 + 16, x0, y, z0, 0.4F, 1.0F, 0.4F);
        }
        for (long[] c : com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.EntityScanCache.GLOBAL.coveredChunks(sl)) {
            double x0 = c[0] * 16, z0 = c[1] * 16;
            edge(vc, pose, x0, y, z0, x0 + 16, y, z0, 0.4F, 0.6F, 1.0F);
            edge(vc, pose, x0 + 16, y, z0, x0 + 16, y, z0 + 16, 0.4F, 0.6F, 1.0F);
            edge(vc, pose, x0 + 16, y, z0 + 16, x0, y, z0 + 16, 0.4F, 0.6F, 1.0F);
            edge(vc, pose, x0, y, z0 + 16, x0, y, z0, 0.4F, 0.6F, 1.0F);
        }
    }

    /** 线段原语 (RenderType.LINES 顶点; 双平台 API 差异: 1.20.1 vertex/color/normal 旧名 [TLM 实证] vs 1.21.1 addVertex/setColor/setNormal) */
    private static void edge(VertexConsumer vc, PoseStack pose,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             float r, float g, float b) {
        var m = pose.last().pose();
//? if 1.20.1 {
        vc.vertex(m, (float) x0, (float) y0, (float) z0)
                .color(r, g, b, 1.0F).normal(pose.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
        vc.vertex(m, (float) x1, (float) y1, (float) z1)
                .color(r, g, b, 1.0F).normal(pose.last().normal(), 0.0F, 1.0F, 0.0F).endVertex();
//?} else {
        // 1.21.1: RenderType.LINES format 含 NORMAL — 缺 normal 在 Iris/Sodium 下 Blaze3D BufferBuilder
        // 严格校验抛 "Missing elements in vertex: Normal" (0.9.60 实测崩溃, #238 待实测隐患兑现);
        // setNormal(Pose,..) 签名 javap client jar 实证 (Pose 版自动用 pose.normal() 矩阵变换)
        vc.addVertex(m, (float) x0, (float) y0, (float) z0)
                .setColor(r, g, b, 1.0F).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
        vc.addVertex(m, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, 1.0F).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
//?}
    }
}
