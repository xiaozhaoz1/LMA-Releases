package com.github.xiaozhaoz1.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidGomokuTogglePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
//? if 1.20.1 {
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//?}

/**
 * v79.62.3: 五子棋「直接判你赢」(开关存女仆 PD {@link MaidGomokuTogglePacket#PD_KEY})。
 *
 * <p>服务端 RightClickBlock 拦 TLM Gomoku 棋盘: 新局 (chessCounter=0) + 坐上女仆开关开 +
 * 玩家空手点棋盘格 → 在玩家点击格的水平方向预置 4 个黑子 (该格留空) →
 * TLM 原生 {@code BlockGomoku.use} 把玩家这手落下 = 第 5 子 → 原生 getStatue 判 WIN →
 * 好感/胜场/成就全走 TLM 原链路 (不取消事件, 不重复发包)。
 *
 * <p>格子换算复刻 TLM BlockGomoku.getChessPos/getData (纯几何, part offset 表)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class GomokuInstantWinHandler {

    private GomokuInstantWinHandler() {}

    /** TLM Gomoku 方块 id */
    private static final String GOMOKU_BLOCK = "touhou_little_maid:gomoku";

    /** 棋盘格间隔/起点 (复刻 TLM getData) */
    private static final double CELL = 0.1316;
    private static final double CELL_HALF = 0.07;

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (event.getEntity().isShiftKeyDown()) return;
        // 只拦 TLM Gomoku 方块 (双平台 registry 查)
        net.minecraft.world.level.block.Block clicked = event.getLevel().getBlockState(event.getPos()).getBlock();
//? if 1.20.1 {
        net.minecraft.world.level.block.Block gomoku = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new net.minecraft.resources.ResourceLocation(GOMOKU_BLOCK));
//?} else {
        net.minecraft.world.level.block.Block gomoku = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse(GOMOKU_BLOCK));
//?}
        if (gomoku == null || clicked != gomoku) return;

        // 点击方块 part → 棋盘中心
        BlockState state = event.getLevel().getBlockState(event.getPos());
        BlockPos centerPos = event.getPos();
        // PART 枚举值是 StringRepresentable (left_up/up/...); 用相对偏移表 (9 块 3×3, 中心=自己)
        // 简化: 从点击块找 3×3 内真正的中心 (PART=center 的那块)
        centerPos = findCenter(event.getLevel(), event.getPos());
        if (centerPos == null) return;
        if (!(event.getLevel().getBlockEntity(centerPos) instanceof TileEntityGomoku gomokuTe)) return;
        // 新局才预置 (TLM reset 后 chessCounter=0)
        if (gomokuTe.getChessCounter() != 0) return;
        if (!gomokuTe.isPlayerTurn()) return;

        // 坐上女仆 (sitId UUID → 实体 → 乘客 EntityMaid)
        EntityMaid maid = findSittingMaid(event.getLevel(), gomokuTe);
        if (maid == null) return;
        if (!MaidGomokuTogglePacket.isEnabled(maid)) return;
        // 玩家需空手 (TLM use 落子前置)
        ItemStack held = event.getEntity().getMainHandItem();
        if (!held.isEmpty()) return;

        // 换算玩家点击的棋盘格 (复刻 TLM)
        net.minecraft.world.phys.Vec3 loc = event.getHitVec().getLocation()
                .subtract(event.getPos().getX(), event.getPos().getY(), event.getPos().getZ());
        int[] cell = toCell(loc, state);
        if (cell == null) return;
        int cx = cell[0], cy = cell[1];
        if (gomokuTe.getChessData()[cx][cy] != 0) return; // 已占

        // 预置: 选水平方向能放 4 子的方向 (优先左: cx-4..cx-1)
        int dx = 0, dy = 0;
        if (cx >= 4) { dx = -1; dy = 0; }
        else if (cx <= 10) { dx = 1; dy = 0; }
        else if (cy >= 4) { dx = 0; dy = -1; }
        else { dx = 0; dy = 1; }
        for (int k = 1; k <= 4; k++) {
            int px = cx + dx * k, py = cy + dy * k;
            if (px < 0 || px > 14 || py < 0 || py > 14) return; // 越界放弃
            if (gomokuTe.getChessData()[px][py] != 0) return;    // 被占放弃 (安全)
        }
        for (int k = 1; k <= 4; k++) {
            gomokuTe.setChessData(cx + dx * k, cy + dy * k, 1); // 1 = BLACK (玩家色)
        }
        gomokuTe.refresh();
    }

    /** 3×3 内找 center 块 (其 BE = TileEntityGomoku 中心) */
    private static BlockPos findCenter(net.minecraft.world.level.Level level, BlockPos clicked) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = clicked.offset(dx, 0, dz);
                if (level.getBlockEntity(p) instanceof TileEntityGomoku) return p;
            }
        }
        return null;
    }

    /** sitId (UUID) → 全维度实体找 EntitySit → 乘客 EntityMaid (1.21.1 getEntity 收 int, UUID 须遍历) */
    private static EntityMaid findSittingMaid(net.minecraft.world.level.Level level, TileEntityGomoku te) {
        java.util.UUID sitId = te.getSitId();
        if (sitId == null) return null;
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            for (Entity e : sl.getAllEntities()) {
                if (!e.getUUID().equals(sitId)) continue;
                for (Entity passenger : e.getPassengers()) {
                    if (passenger instanceof EntityMaid maid) return maid;
                }
            }
        }
        return null;
    }

    /**
     * 点击局部坐标 → 棋盘格 [x,y] (复刻 TLM BlockGomoku.getChessPos/getData 九宫格 offset 表).
     * TLM part 布局: 3×3 块, 每块含 5×5 格 (中央 15×15 棋盘跨 9 块)。
     */
    private static int[] toCell(net.minecraft.world.phys.Vec3 loc, BlockState state) {
        // 各 part 的 (xOffset, yOffset, xStart, yStart, xIdx, yIdx) — 复刻 BlockGomoku.getChessPos
        String part = state.getValue(com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku.PART).getSerializedName();
        double xo, yo, xs, ys;
        int xio, yio;
        switch (part) {
            case "left_up" -> { xo = 0.505; yo = 0.505; xs = 0.54; ys = 0.54; xio = 0; yio = 0; }
            case "up" -> { xo = 0.037; yo = 0.505; xs = 0.08; ys = 0.54; xio = 4; yio = 0; }
            case "right_up" -> { xo = -0.037; yo = 0.505; xs = -0.01; ys = 0.54; xio = 11; yio = 0; }
            case "left_center" -> { xo = 0.505; yo = 0.037; xs = 0.54; ys = 0.07; xio = 0; yio = 4; }
            case "center" -> { xo = 0.037; yo = 0.037; xs = 0.08; ys = 0.07; xio = 4; yio = 4; }
            case "right_center" -> { xo = -0.037; yo = 0.037; xs = -0.01; ys = 0.07; xio = 11; yio = 4; }
            case "left_down" -> { xo = 0.505; yo = 0.0; xs = 0.54; ys = 0.0; xio = 0; yio = 11; }
            case "down" -> { xo = 0.037; yo = 0.0; xs = 0.08; ys = 0.0; xio = 4; yio = 11; }
            case "right_down" -> { xo = -0.037; yo = 0.0; xs = -0.01; ys = 0.0; xio = 11; yio = 11; }
            default -> { return null; }
        }
        double x = loc.x, y = loc.z; // 棋盘面在 XZ 平面 (TLM use 用 location.x/z)
        int xi = (int) ((x - xo) / CELL);
        int yi = (int) ((y - yo) / CELL);
        double xs0 = xs + xi * CELL, xe0 = xs0 + CELL_HALF;
        double ys0 = ys + yi * CELL, ye0 = ys0 + CELL_HALF;
        xi += xio; yi += yio;
        boolean in = 0 <= xi && xi <= 14 && 0 <= yi && yi <= 14 && xs0 < x && x < xe0 && ys0 < y && y < ye0;
        return in ? new int[]{xi, yi} : null;
    }
}
