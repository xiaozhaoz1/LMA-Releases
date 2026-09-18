package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Token 的**饰品形态** (v79.72, 用户需求): 戴在女仆身上时持续给「**生命恢复 I**」✓
 *
 * <p>实现方式沿用同包模板 {@code WildKitsuneMilk/TamedMilkBauble} (TLM 的 {@link IMaidBauble} ✓):
 * 用 {@link #onTick} 周期性补效果 ✓ —— 每次给 {@code DURATION} tick、每 {@code REFRESH} tick 补一次
 * (刷得比时长快 ⇒ **不断档** ✓), 这样:
 * ① 女仆**摘下饰品**或效果过期后**自然停** ✓ (不需要额外状态)
 * ② 重复触发是**幂等**的 (同一效果重复 add ⇒ 取更强者/刷新时长 ✓ — 见 bauble README 陷阱 D) ✓
 *
 * <p>⚠ 不用 `INFINITE_DURATION` (-1): 那会让效果在摘下后**永久留存** ✗ (TLM 也不便清理)。
 */
public final class TokenBauble implements IMaidBauble {

    /** 效果时长 (tick) — 3 秒, 足以跨过两次刷新 ✓ */
    private static final int DURATION = 60;
    /** 刷新间隔 (tick) — 每 40t (2 秒) 补一次 ⇒ 时长始终富余 ✓ */
    private static final int REFRESH = 40;
    /** 缓慢回血 **I** ⇒ 增幅器 0 ✓ (0 = I 级) */
    private static final int AMPLIFIER = 0;

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level() == null || maid.level().isClientSide) return;
        if (maid.tickCount % REFRESH != 0) return;   // 节流: 每 REFRESH tick 补一次 ✓
        // ★ v79.74 修正 (用户实测: "佩戴了但看不到恢复图标" ✓):
        //   原来用 `(…, true, **false**)` ⇒ **隐藏粒子+隐藏图标** ✗ ⇒ 玩家以为没生效 ✓
        //   ⇒ 改为 3 参构造 = **可见图标** ✓ (佩戴期间图标应当一直挂着 ✓, 这也是用户的验收标准 ✓)
        maid.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, AMPLIFIER));
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        // ★ v79.74 修正 (用户实测: "吃完恢复 2 秒就没了" ✓):
        //   原来这里 `maid.removeEffect(MobEffects.REGENERATION)` ✗ —— TLM 在**槽位同步/装备变更**时
        //   也会触发 `onTakeOff` ✓ ⇒ 它会把**吃出来的 30 秒效果一起清掉** ✗ (= 自己的代码删自己的效果 ✗)。
        //   ⇒ 现在**不清除** ✓: 饰品给的是 60t(3s) 短刷新 ✓ ⇒ 摘下后**自然在 ≤3s 内过期** ✓
        //     ⇒ "戴着才有"的语义不变 ✓, 且不再误伤长效果(吃的 600t) ✓。
        //   ⚠ 若要真正"摘下立刻清", 必须先判断"该效果是不是**本饰品刷出来的**"
        //     (vanilla 的 MobEffectInstance 无法区分来源 ✗ —— 除非改用 amplifier/自管计时字段) ⇒ 暂不做 ✗
    }
}
