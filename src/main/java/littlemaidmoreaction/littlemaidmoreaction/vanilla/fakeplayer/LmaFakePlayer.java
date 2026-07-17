package littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * LMA 女仆假玩家 — 模拟女仆所有者执行方块交互、实体交互、方块挖掘。
 *
 * <p>参考 Create DeployerFakePlayer 模式:
 * <ul>
 *   <li>继承 {@link FakePlayer} — Forge 内置假玩家基类</li>
 *   <li>GameProfile 伪装成女仆所有者 UUID, 绕过领地保护</li>
 *   <li>攻击速度 1/64 tick — 每 tick 可攻击</li>
 *   <li>方块破坏进度跨 tick 累积</li>
 * </ul>
 */
public class LmaFakePlayer extends FakePlayer {
    private final EntityMaid maid;
    private BlockPos breakingPos;
    private float breakingProgress;

    /**
     * @param world 服务端世界
     * @param maid  执行操作的女仆
     * @param pos   假玩家出生/站立位置 (通常是目标方块位置)
     */
    public LmaFakePlayer(ServerLevel world, EntityMaid maid, BlockPos pos) {
        super(world, buildProfile(maid));
        this.maid = maid;
        setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        // 同步女仆手上物品为假玩家主手
        ItemStack maidHand = maid.getMainHandItem();
        if (!maidHand.isEmpty()) {
            setItemInHand(InteractionHand.MAIN_HAND, maidHand.copy());
        }
    }

    /** 用女仆所有者 UUID 构建 GameProfile, 绕过领地保护 */
    private static GameProfile buildProfile(EntityMaid maid) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) {
            ownerId = UUID.nameUUIDFromBytes(("lma_maid_" + maid.getId()).getBytes());
        }
        return new GameProfile(ownerId, "LMA-Maid-" + maid.getId());
    }

    // ── 必备覆写 ──

    @Override
    public float getCurrentItemAttackStrengthDelay() {
        return 1 / 64f; // 每 tick 可攻击 — 参考 DeployerFakePlayer
    }

    @Override
    public Vec3 position() {
        return new Vec3(getX(), getY(), getZ());
    }

    @Override
    public boolean canEat(boolean ignoreHunger) {
        return false;
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        return false;
    }

    @Override
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return false;
    }

    // ── 方块破坏进度 ──

    public BlockPos getBreakingPos() { return breakingPos; }
    public void setBreakingPos(BlockPos pos) { this.breakingPos = pos; }
    public float getBreakingProgress() { return breakingProgress; }
    public void setBreakingProgress(float progress) { this.breakingProgress = progress; }
    public void addBreakingProgress(float delta) { this.breakingProgress += delta; }

    public void clearBreakingProgress(ServerLevel world) {
        if (breakingPos != null) {
            world.destroyBlockProgress(getId(), breakingPos, -1);
        }
        breakingPos = null;
        breakingProgress = 0;
    }

    // ── 女仆拥有者 ──

    public EntityMaid getMaid() { return maid; }

    @Nullable
    public UUID getOwnerUUID() { return maid.getOwnerUUID(); }

    // ── 伤害来源标记 — 由女仆造成 ──

    @Override
    public void awardKillScore(Entity victim, int score, DamageSource source) {
        // 击杀计分给女仆, 不给假玩家
    }
}
