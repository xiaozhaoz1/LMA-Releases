package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkItems;
import com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;

import java.util.Set;

/**
 * v79.62 酒狐奶自动喂食 — 主人受伤时自动喂奶 (清负面+加buff).
 * 优先喂酒狐奶桶 (TamedMilkBucketItem), 其次野生酒狐奶 (WildMilkItem).
 * 节流 600t (30s), 主人血量 < 70% 触发.
 */
public final class MilkPipeline implements TaskPipeline, TaskConfigurable {

    private static final double HEAL_TRIGGER_RATIO = 0.7;
    private static final int THROTTLE_TICKS = 600;
    private static final String KEY_LAST_FEED = "lma_milk_last_feed";

    @Override public String taskType() { return "jiuhu_milk"; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");   // 纯 tick 驱动 (主人血量检测), 无信号依赖
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        // v79.62.1 启用判定迁至 PassiveConfigUtil (per-maid pipelineConfig "enabled");
        // submitPassive 已门控, 不再用全局 JIUHU_MILK_AUTO_FEED 二次判定
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof Player player)) return;
        if (!player.isAlive()) return;
        // 距离
        if (maid.distanceToSqr(player) > 64.0) return; // 8 格
        // 血量触发
        float ratio = player.getHealth() / player.getMaxHealth();
        if (ratio > HEAL_TRIGGER_RATIO) return;
        // 节流 (时间戳防溢出: last > now 视为异常 → 放行)
        long now = world.getGameTime();
        CompoundTag pd = pipelineData(maid);
        long last = pd.getLong(KEY_LAST_FEED);
        if (last > 0 && last <= now && now - last < THROTTLE_TICKS) return;

        // 找奶 — 优先酒狐奶桶, 其次野生酒狐奶
        ItemStack milk = findMilk(maid);
        if (milk.isEmpty()) return;

        // 喂奶
        boolean isTamed = milk.getItem() == KitsuneMilkItems.TAMED_MILK_BUCKET.get();
        if (WildKitsuneMilkConfig.CLEAR_NEGATIVE.get()) {
            player.removeAllEffects();
        }
        if (isTamed) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.get(), 1));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.get(), 0));
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    WildKitsuneMilkConfig.WILD_REGENERATION_TICKS.get(), 0));
        }
        world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK,
                SoundSource.PLAYERS, 0.6F, 0.8F + player.getRandom().nextFloat() * 0.4F);

        // 消耗奶 → 返回空桶
        milk.shrink(1);
        if (!player.getAbilities().instabuild) {
            ItemStack bucket = new ItemStack(Items.BUCKET);
            var inv = maid.getAvailableInv(true);
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.isEmpty()) { inv.setStackInSlot(i, bucket); break; }
//? if 1.20.1 {
                if (ItemStack.isSameItemSameTags(s, bucket) && s.getCount() < s.getMaxStackSize()) {
//?} else {
                if (ItemStack.isSameItemSameComponents(s, bucket) && s.getCount() < s.getMaxStackSize()) {
//?}
                    s.grow(1); break;
                }
            }
        }

        MaidChatBubbleApi.showInfo(maid, "狗修金受伤了！喝奶~");
        pd.putLong(KEY_LAST_FEED, now);
    }

    private static ItemStack findMilk(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        // 先找酒狐奶桶
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == KitsuneMilkItems.TAMED_MILK_BUCKET.get()) return s;
        }
        // 再找野生酒狐奶
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == KitsuneMilkItems.WILD_DOGMILK.get()) return s;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void onCleanup(EntityMaid maid) {
        // 数据键闭环: 清理喂食节流戳
        clearPipelineData(maid);
    }
}
