package littlemaidmoreaction.littlemaidmoreaction.compat.createbigcannons.task;

import com.simibubi.create.content.contraptions.Contraption;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBlock;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonTubeBlock;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.QuickfiringBreechBlock;
import rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.QuickfiringBreechBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.cannon_end.BigCannonEnd;
import rbasamoyai.createbigcannons.cannons.big_cannons.cannon_end.BigCannonEndBlock;
import rbasamoyai.createbigcannons.equipment.manual_loading.RamRodItem;
import rbasamoyai.createbigcannons.equipment.manual_loading.WormItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonMunitionBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCartridgeBlock;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CannonLoadService {

    private static final int SEARCH_RANGE = 8;
    private static final int PUSH_STRENGTH = 3;
    private static final int PUSH_REACH = 5;

    private CannonLoadService() {}

    public record BreechInfo(BlockPos localPos, QuickfiringBreechBlockEntity breech, Direction pushDirection) {}

    public record CannonState(int projectileCount, int propellantCount, int totalCharges, boolean canLoadAtBreech) {
        public boolean hasProjectile() { return projectileCount > 0; }
        public boolean hasPropellant() { return propellantCount > 0; }
    }

    public enum AmmoType { EMPTY, PROJECTILE, PROPELLANT }

    public record AmmoSlot(BlockPos pos, AmmoType ammo, String blockType) {}

    // ═══ 详细扫描 ═══

    public static List<AmmoSlot> scanDetailed(PitchOrientedContraptionEntity entity, BreechInfo breechInfo) {
        List<AmmoSlot> slots = new ArrayList<>();
        if (!(entity.getContraption() instanceof AbstractMountedCannonContraption cannon)) return slots;
        Direction pushDir = breechInfo.pushDirection();
        BlockPos breechPos = breechInfo.localPos();
        for (int i = 0; i < 10; i++) {
            BlockPos pos = breechPos.relative(pushDir, i + 1);
            BlockEntity be = cannon.presentBlockEntities.get(pos);
            if (!(be instanceof IBigCannonBlockEntity cbe)) break;
            var block = cbe.cannonBehavior().block();
            BlockState tubeState = be.getBlockState();
            Block tubeBlock = tubeState.getBlock();
            String blockType = classifyBlock(tubeBlock);
            if (block.state().isAir()) {
                if (tubeBlock instanceof BigCannonEndBlock) break;
                slots.add(new AmmoSlot(pos, AmmoType.EMPTY, blockType));
            } else if (block.state().getBlock() instanceof BigCannonPropellantBlock) {
                slots.add(new AmmoSlot(pos, AmmoType.PROPELLANT, blockType));
            } else if (block.state().getBlock() instanceof BigCannonMunitionBlock) {
                slots.add(new AmmoSlot(pos, AmmoType.PROJECTILE, blockType));
            }
        }
        return slots;
    }

    private static String classifyBlock(Block block) {
        if (block instanceof QuickfiringBreechBlock || block.getClass().getSimpleName().contains("Breech")) return "breech";
        if (block instanceof BigCannonEndBlock) return "end";
        if (block instanceof BigCannonTubeBlock) {
            String name = block.getClass().getSimpleName();
            return name.contains("Chamber") ? "chamber" : "barrel";
        }
        return "other";
    }

    public static boolean isLoadOrderCorrect(List<AmmoSlot> slots) {
        if (slots.isEmpty()) return true;
        boolean seenProjectile = false;
        for (AmmoSlot slot : slots) {
            if (slot.ammo() == AmmoType.EMPTY) break;
            switch (slot.ammo()) {
                case PROPELLANT -> { if (seenProjectile) return false; }
                case PROJECTILE -> { if (seenProjectile) return false; seenProjectile = true; }
            }
        }
        return true;
    }

    // ═══ 胶刷清膛 ═══

    public static boolean wormClear(PitchOrientedContraptionEntity entity, BreechInfo breechInfo,
                                     com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        if (!(entity.getContraption() instanceof MountedBigCannonContraption cannon)) return false;
        Direction pushDir = breechInfo.pushDirection();
        BlockPos breechPos = breechInfo.localPos();
        boolean cleared = false;
        int safety = 10;
        while (safety-- > 0) {
            boolean found = false;
            for (int i = 0; i < 10; i++) {
                BlockPos pos = breechPos.relative(pushDir, i + 1);
                BlockEntity be = cannon.presentBlockEntities.get(pos);
                if (!(be instanceof IBigCannonBlockEntity cbe)) break;
                StructureBlockInfo info = cbe.cannonBehavior().block();
                if (info.state().isAir()) continue;
                ItemStack stack = info.state().getBlock() instanceof BigCannonMunitionBlock munition
                    ? munition.getExtractedItem(info) : ItemStack.EMPTY;
                cbe.cannonBehavior().removeBlock();
                if (!stack.isEmpty()) {
                    var inv = maid.getAvailableInv(true);
                    for (int j = 0; j < inv.getSlots() && !stack.isEmpty(); j++) {
                        stack = inv.insertItem(j, stack, false);
                    }
                    if (!stack.isEmpty()) {
                        var bp = maid.getAvailableBackpackInv();
                        for (int j = 0; j < bp.getSlots() && !stack.isEmpty(); j++) {
                            stack = bp.insertItem(j, stack, false);
                        }
                    }
                }
                BigCannonBlock.writeAndSyncMultipleBlockData(Set.of(pos), entity, cannon);
                cleared = true;
                found = true;
                break;
            }
            if (!found) break;
        }
        return cleared;
    }

    // ═══ 扫描 ═══

    public static CannonState scanCannon(PitchOrientedContraptionEntity entity, BreechInfo breechInfo) {
        if (!(entity.getContraption() instanceof AbstractMountedCannonContraption cannon))
            return new CannonState(0, 0, 0, false);
        int projectiles = 0, propellants = 0, total = 0;
        Direction pushDir = breechInfo.pushDirection();
        BlockPos breechPos = breechInfo.localPos();
        for (int i = 0; i < 10; i++) {
            BlockPos pos = breechPos.relative(pushDir, i + 1);
            BlockEntity be = cannon.presentBlockEntities.get(pos);
            if (!(be instanceof IBigCannonBlockEntity cbe)) break;
            var block = cbe.cannonBehavior().block();
            if (block.state().isAir()) break;
            total++;
            if (block.state().getBlock() instanceof BigCannonPropellantBlock) propellants++;
            else if (block.state().getBlock() instanceof BigCannonMunitionBlock) projectiles++;
        }
        BlockPos firstTube = breechPos.relative(pushDir);
        BlockEntity firstBe = cannon.presentBlockEntities.get(firstTube);
        boolean canLoad = firstBe instanceof IBigCannonBlockEntity cbe
            && cbe.cannonBehavior().block().state().isAir();
        return new CannonState(projectiles, propellants, total, canLoad);
    }

    // ═══ 搜索 ═══

    @Nullable
    public static PitchOrientedContraptionEntity findContraptionEntity(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(PitchOrientedContraptionEntity.class,
            new AABB(center).inflate(SEARCH_RANGE, 4, SEARCH_RANGE))
            .stream()
            .filter(e -> e.getContraption() instanceof AbstractMountedCannonContraption)
            .findFirst().orElse(null);
    }

    public static List<BlockPos> findAllCannonMounts(ServerLevel level, BlockPos center) {
        List<BlockPos> result = new ArrayList<>();
        for (int dr = 0; dr <= SEARCH_RANGE; dr++) {
            for (int dx = -dr; dx <= dr; dx++) {
                for (int dz = -dr; dz <= dr; dz++) {
                    if (Math.abs(dx) != dr && Math.abs(dz) != dr) continue;
                    BlockPos pos = center.offset(dx, 0, dz);
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos p = pos.offset(0, dy, 0);
                        Block block = level.getBlockState(p).getBlock();
                        if (block instanceof CannonMountBlock || block instanceof FixedCannonMountBlock) {
                            result.add(p.immutable());
                        }
                    }
                }
            }
        }
        level.getEntitiesOfClass(PitchOrientedContraptionEntity.class,
            new AABB(center).inflate(SEARCH_RANGE, 4, SEARCH_RANGE))
            .stream()
            .filter(e -> e.getContraption() instanceof AbstractMountedCannonContraption)
            .forEach(e -> result.add(e.blockPosition()));
        result.sort((a, b) -> Double.compare(center.distSqr(a), center.distSqr(b)));
        return result;
    }

    @Nullable
    public static BlockPos findCannonMount(ServerLevel level, BlockPos center) {
        List<BlockPos> all = findAllCannonMounts(level, center);
        return all.isEmpty() ? null : all.get(0);
    }

    public static boolean isCannonMount(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return block instanceof CannonMountBlock || block instanceof FixedCannonMountBlock;
    }

    public static boolean isValidMount(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CannonMountBlock) return true;
        if (state.getBlock() instanceof FixedCannonMountBlock) return true;
        return findContraptionEntity(level, pos) != null;
    }

    @Nullable
    public static PitchOrientedContraptionEntity getContraption(ServerLevel level, BlockPos mountPos) {
        BlockEntity be = level.getBlockEntity(mountPos);
        if (be instanceof CannonMountBlockEntity mount) return mount.getContraption();
        if (be instanceof FixedCannonMountBlockEntity mount) return mount.getContraption();
        return findContraptionEntity(level, mountPos);
    }

    @Nullable
    public static BlockPos getStandPos(ServerLevel level, BlockPos mountPos) {
        PitchOrientedContraptionEntity entity = findContraptionEntity(level, mountPos);
        if (entity != null) {
            Direction facing = entity.getInitialOrientation();
            return mountPos.relative(facing.getOpposite(), 2);
        }
        BlockState state = level.getBlockState(mountPos);
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            return mountPos.relative(facing.getOpposite(), 2);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return mountPos.relative(facing.getOpposite(), 2);
        }
        return null;
    }

    // ═══ 装配体炮闩 ═══

    @Nullable
    public static BreechInfo findBreechInContraption(PitchOrientedContraptionEntity entity) {
        Contraption contraption = entity.getContraption();
        if (!(contraption instanceof AbstractMountedCannonContraption cannon)) return null;
        Direction pushDir = entity.getInitialOrientation();
        for (Map.Entry<BlockPos, BlockEntity> entry : cannon.presentBlockEntities.entrySet()) {
            if (entry.getValue() instanceof QuickfiringBreechBlockEntity breech) {
                return new BreechInfo(entry.getKey(), breech, pushDir);
            }
        }
        return null;
    }

    public static boolean isBreechOpen(BreechInfo info) {
        return info != null && info.breech().isOpen();
    }

    public static boolean isOnCooldown(BreechInfo info) {
        return info != null && info.breech().onInteractionCooldown();
    }

    public static void toggleBreech(PitchOrientedContraptionEntity entity, BreechInfo info) {
        if (info == null || entity == null) return;
        info.breech().toggleOpening();
        info.breech().setChanged();
        if (entity.getContraption() instanceof MountedBigCannonContraption cannon) {
            BigCannonBlock.writeAndSyncMultipleBlockData(Set.of(info.localPos()), entity, cannon);
        }
    }

    // ═══ 装填弹药 ═══

    public static boolean loadMunition(PitchOrientedContraptionEntity entity, BreechInfo breechInfo, ItemStack stack) {
        if (!(entity.getContraption() instanceof MountedBigCannonContraption cannon)) return false;
        QuickfiringBreechBlockEntity breech = breechInfo.breech();
        if (!breech.isOpen()) return false;
        Block itemBlock = Block.byItem(stack.getItem());
        if (!(itemBlock instanceof BigCannonMunitionBlock munition)) return false;
        Direction pushDir = breechInfo.pushDirection();
        BlockPos nextPos = breechInfo.localPos().relative(pushDir);
        BlockEntity loadBe = cannon.presentBlockEntities.get(nextPos);
        if (!(loadBe instanceof IBigCannonBlockEntity loadCbe)) return false;
        StructureBlockInfo loadInfo = munition.getHandloadingInfo(stack, nextPos, pushDir);
        StructureBlockInfo existing = loadCbe.cannonBehavior().block();
        if (!existing.state().isAir()) {
            BlockPos pushPos = nextPos.relative(pushDir);
            BlockEntity pushBe = cannon.presentBlockEntities.get(pushPos);
            if (!(pushBe instanceof IBigCannonBlockEntity pushCbe)) return false;
            if (!pushCbe.cannonBehavior().canLoadBlock(existing)) return false;
            pushCbe.cannonBehavior().loadBlock(existing);
            loadCbe.cannonBehavior().removeBlock();
        }
        boolean ok = loadCbe.cannonBehavior().tryLoadingBlock(loadInfo);
        if (ok) {
            Set<BlockPos> changes = new HashSet<>(2);
            changes.add(nextPos);
            if (!existing.state().isAir()) changes.add(nextPos.relative(pushDir));
            BigCannonBlock.writeAndSyncMultipleBlockData(changes, entity, cannon);
        }
        return ok;
    }

    public static boolean ramPush(PitchOrientedContraptionEntity entity, BreechInfo breechInfo) {
        if (!(entity.getContraption() instanceof MountedBigCannonContraption cannon)) return false;
        Direction pushDir = breechInfo.pushDirection();
        BlockPos startPos = breechInfo.localPos();
        int k = 0;
        boolean found = false;
        for (int i = 0; i < PUSH_REACH; i++) {
            BlockPos pos1 = startPos.relative(pushDir, i);
            BlockEntity be = cannon.presentBlockEntities.get(pos1);
            if (be instanceof IBigCannonBlockEntity cbe) {
                if (!cbe.cannonBehavior().block().state().isAir()) { k = i; found = true; break; }
            }
        }
        if (!found) return false;
        List<StructureBlockInfo> toPush = new ArrayList<>();
        for (int i = 0; i < PUSH_STRENGTH + 1; i++) {
            BlockPos pos1 = startPos.relative(pushDir, i + k);
            BlockEntity be = cannon.presentBlockEntities.get(pos1);
            if (!(be instanceof IBigCannonBlockEntity cbe)) break;
            StructureBlockInfo info = cbe.cannonBehavior().block();
            if (info.state().isAir()) break;
            toPush.add(info);
            if (toPush.size() > PUSH_STRENGTH) return false;
        }
        Set<BlockPos> changes = new HashSet<>();
        for (int i = toPush.size() - 1; i >= 0; i--) {
            BlockPos pos1 = startPos.relative(pushDir, i + k);
            BlockPos pos2 = pos1.relative(pushDir);
            BlockEntity be1 = cannon.presentBlockEntities.get(pos1);
            BlockEntity be2 = cannon.presentBlockEntities.get(pos2);
            if (!(be1 instanceof IBigCannonBlockEntity cbe1) || !(be2 instanceof IBigCannonBlockEntity cbe2)) return false;
            if (!cbe2.cannonBehavior().canLoadBlock(toPush.get(i))) return false;
            cbe1.cannonBehavior().removeBlock();
            cbe2.cannonBehavior().tryLoadingBlock(toPush.get(i));
            changes.add(pos1);
            changes.add(pos2);
        }
        if (!changes.isEmpty()) {
            BigCannonBlock.writeAndSyncMultipleBlockData(changes, entity, cannon);
        }
        return true;
    }

    // ═══ breech+1 位置检测 ═══

    public static boolean isProjectileAtBreech(PitchOrientedContraptionEntity entity, BreechInfo breechInfo) {
        if (!(entity.getContraption() instanceof AbstractMountedCannonContraption cannon)) return false;
        BlockPos firstTube = breechInfo.localPos().relative(breechInfo.pushDirection());
        BlockEntity be = cannon.presentBlockEntities.get(firstTube);
        if (!(be instanceof IBigCannonBlockEntity cbe)) return false;
        Block block = cbe.cannonBehavior().block().state().getBlock();
        return block instanceof BigCannonMunitionBlock && !(block instanceof BigCannonPropellantBlock);
    }

    // ═══ 物品类型检测 ═══

    public static boolean isRamRod(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof RamRodItem;
    }

    public static boolean isWorm(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof WormItem;
    }

    public static boolean isPropellant(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof BigCannonPropellantBlock;
    }

    public static boolean isProjectile(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Block block = Block.byItem(stack.getItem());
        return block instanceof BigCannonMunitionBlock && !(block instanceof BigCannonPropellantBlock);
    }

    public static boolean isBigCartridge(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof BigCartridgeBlock;
    }

    /** 炮管内是否已装填BigCartridge（1个顶4份发射药，已装则跳过第2发）。 */
    public static boolean hasBigCartridgeInTube(PitchOrientedContraptionEntity entity, BreechInfo breechInfo) {
        if (!(entity.getContraption() instanceof AbstractMountedCannonContraption cannon)) return false;
        Direction pushDir = breechInfo.pushDirection();
        BlockPos breechPos = breechInfo.localPos();
        for (int i = 0; i < 10; i++) {
            BlockPos pos = breechPos.relative(pushDir, i + 1);
            BlockEntity be = cannon.presentBlockEntities.get(pos);
            if (!(be instanceof IBigCannonBlockEntity cbe)) break;
            var info = cbe.cannonBehavior().block();
            if (info.state().isAir()) break;
            if (info.state().getBlock() instanceof BigCartridgeBlock) return true;
        }
        return false;
    }
}
