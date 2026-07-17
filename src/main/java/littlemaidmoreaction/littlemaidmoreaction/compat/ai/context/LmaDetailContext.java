package littlemaidmoreaction.littlemaidmoreaction.compat.ai.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.AbstractMaidContext;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.search.LightQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;

/**
 * LMA 细节上下文 — 女仆/主人脚下方块、主人效果、主人最近食物 (v11)。
 *
 * <p>promptContext=false，LLM 通过 {@code query_game_context("lma_details")} 按需查询。
 *
 * <h3>与 TLM 内置上下文的互补</h3>
 * TLM 提供 {@code equipment}（手/背包/装甲）、{@code user}（主人名称/血量/手/装甲）、
 * {@code effects}（女仆药水效果）。LMA 补充脚下方块、主人效果、食物记忆。
 */
public final class LmaDetailContext {

    public static final String CATEGORY = "lma_details";
    private static final String SUMMARY =
        "LMA details: maid hunger/exp/favor/model/backpack/pickup/restrict, " +
        "owner foot block/effects/last food/exp level.";

    private LmaDetailContext() {}

    public static void registerAll(GameContextRegister register) {
        register.registerCategory(CATEGORY, SUMMARY, false);
        // 女仆状态
        register.registerContext(CATEGORY, new MaidHungerContext());
        register.registerContext(CATEGORY, new MaidExperienceContext());
        register.registerContext(CATEGORY, new MaidFavorContext());
        register.registerContext(CATEGORY, new MaidBackpackContext());
        register.registerContext(CATEGORY, new MaidModelContext());
        register.registerContext(CATEGORY, new MaidPickupContext());
        register.registerContext(CATEGORY, new MaidRestrictContext());
        register.registerContext(CATEGORY, new MaidFootBlockContext());
        // 主人状态
        register.registerContext(CATEGORY, new OwnerFootBlockContext());
        register.registerContext(CATEGORY, new OwnerEffectsContext());
        register.registerContext(CATEGORY, new OwnerLastFoodContext());
        register.registerContext(CATEGORY, new OwnerExperienceContext());
        // 光照 (v12 P1)
        register.registerContext(CATEGORY, new MaidLightContext());
    }

    // ── 女仆饥饿 ────────────────────────────────────────────

    private static final class MaidHungerContext extends AbstractMaidContext {
        private MaidHungerContext() { super("maid_hunger", "Maid hunger value"); }

        @Override
        public String getValue(EntityMaid maid) {
            return String.valueOf(maid.getHunger());
        }
    }

    // ── 女仆经验 ────────────────────────────────────────────

    private static final class MaidExperienceContext extends AbstractMaidContext {
        private MaidExperienceContext() { super("maid_experience", "Maid experience points"); }

        @Override
        public String getValue(EntityMaid maid) {
            return String.format("%d XP (4P=1XP, from fairies/maid beacon)", maid.getExperience());
        }
    }

    // ── 女仆好感度 ──────────────────────────────────────────

    private static final class MaidFavorContext extends AbstractMaidContext {
        private MaidFavorContext() { super("maid_favor", "Maid favorability level and points"); }

        @Override
        public String getValue(EntityMaid maid) {
            FavorabilityManager fm = maid.getFavorabilityManager();
            int points = maid.getFavorability();
            int level = fm.getLevel();
            String next = switch (level) {
                case 0 -> "64 (Lv1 at 64)";
                case 1 -> "192 (Lv2 at 192)";
                case 2 -> "384 (Lv3 at 384)";
                default -> "max";
            };
            return String.format("Lv%d (%d pts, next: %s)", level, points, next);
        }
    }

    // ── 女仆背包类型 ────────────────────────────────────────

    private static final class MaidBackpackContext extends AbstractMaidContext {
        private MaidBackpackContext() { super("maid_backpack", "Maid backpack type"); }

        @Override
        public String getValue(EntityMaid maid) {
            return maid.getMaidBackpackType().getId().toString();
        }
    }

    // ── 女仆模型 ────────────────────────────────────────────

    private static final class MaidModelContext extends AbstractMaidContext {
        private MaidModelContext() { super("maid_model", "Maid current model"); }

        @Override
        public String getValue(EntityMaid maid) {
            if (maid.isYsmModel()) {
                return String.format("YSM: %s (texture=%s)",
                    maid.getYsmModelId(), maid.getYsmModelTexture());
            }
            return "TLM: " + maid.getModelId();
        }
    }

    // ── 女仆拾取模式 ────────────────────────────────────────

    private static final class MaidPickupContext extends AbstractMaidContext {
        private MaidPickupContext() { super("maid_pickup", "Maid pickup mode"); }

        @Override
        public String getValue(EntityMaid maid) {
            boolean on = maid.isPickup();
            PickType type = maid.getConfigManager().getPickupType();
            return String.format("%s (%s)", on ? "ON" : "OFF", type.name().toLowerCase());
        }
    }

    // ── 女仆活动范围 ────────────────────────────────────────

    private static final class MaidRestrictContext extends AbstractMaidContext {
        private MaidRestrictContext() { super("maid_restrict", "Maid home restriction"); }

        @Override
        public String getValue(EntityMaid maid) {
            if (!maid.hasRestriction()) return "none (free roam)";
            return String.format("home mode, radius %.0f blocks, center %s",
                maid.getRestrictRadius(), maid.getRestrictCenter().toShortString());
        }
    }

    // ── 主人经验等级 ─────────────────────────────────────────

    private static final class OwnerExperienceContext extends AbstractMaidContext {
        private OwnerExperienceContext() { super("owner_experience", "Owner XP level"); }

        @Override
        public String getValue(EntityMaid maid) {
            LivingEntity owner = maid.getOwner();
            if (!(owner instanceof Player player)) return "no owner";
            return String.format("Lv%d (%d XP)", player.experienceLevel, player.totalExperience);
        }
    }

    // ── 女仆脚下方块 ────────────────────────────────────────

    private static final class MaidFootBlockContext extends AbstractMaidContext {
        private MaidFootBlockContext() {
            super("maid_foot_block", "Block under maid's feet");
        }

        @Override
        public String getValue(EntityMaid maid) {
            return blockAtPos(maid.level(), maid.blockPosition().below());
        }
    }

    // ── 主人脚下方块 ────────────────────────────────────────

    private static final class OwnerFootBlockContext extends AbstractMaidContext {
        private OwnerFootBlockContext() {
            super("owner_foot_block", "Block under owner's feet");
        }

        @Override
        public String getValue(EntityMaid maid) {
            LivingEntity owner = maid.getOwner();
            if (owner == null) return "no owner";
            return blockAtPos(owner.level(), owner.blockPosition().below());
        }
    }

    // ── 主人药水效果 ────────────────────────────────────────

    private static final class OwnerEffectsContext extends AbstractMaidContext {
        private OwnerEffectsContext() {
            super("owner_effects", "Owner active potion effects");
        }

        @Override
        public String getValue(EntityMaid maid) {
            LivingEntity owner = maid.getOwner();
            if (owner == null) return "no owner";
            Collection<MobEffectInstance> effects = owner.getActiveEffects();
            if (effects.isEmpty()) return "none";
            StringBuilder sb = new StringBuilder();
            for (MobEffectInstance e : effects) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(e.getEffect().getDisplayName().getString());
                sb.append(" Lv").append(e.getAmplifier() + 1);
                sb.append(" (").append(e.getDuration() / 20).append("s)");
            }
            return sb.toString();
        }
    }

    // ── 主人最近吃的食物 ────────────────────────────────────

    private static final class OwnerLastFoodContext extends AbstractMaidContext {
        private OwnerLastFoodContext() {
            super("owner_last_food", "Owner's last eaten food");
        }

        @Override
        public String getValue(EntityMaid maid) {
            LivingEntity owner = maid.getOwner();
            if (owner == null) return "no owner";
            String food = owner.getPersistentData().getString("lma_last_food");
            return food.isEmpty() ? "unknown" : food;
        }
    }

    // ── 女仆光照等级 (v12 P1) ──────────────────────────────

    private static final class MaidLightContext extends AbstractMaidContext {
        private MaidLightContext() {
            super("maid_light", "Light level at maid position (0-15). <9 = dark, mobs can spawn.");
        }

        @Override
        public String getValue(EntityMaid maid) {
            return LightQuery.describe(maid.level(), maid.blockPosition());
        }
    }

    // ── 工具方法 ─────────────────────────────────────────────

    private static String blockAtPos(net.minecraft.world.level.Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(block);
        return rl != null ? rl.toString() : "air";
    }
}
