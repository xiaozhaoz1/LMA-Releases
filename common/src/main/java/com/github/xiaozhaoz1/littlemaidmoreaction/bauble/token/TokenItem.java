package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Token (可食用 · 女仆饰品) — v79.72 新增 (用户需求)。
 *
 * <p><b>食用效果 (统一一个实现, 三条路径都吃这套 ✓)</b>: +2 饱食度 · **回 5 点血** ·
 * **30 秒「缓慢恢复 I」** ✓
 * <br>① 玩家吃 ✓ (原版食物流程 + 本方法的附加效果)
 * <br>② 玩家右键喂女仆 ✓ ({@link TokenFeedHandler} 走同一套 {@link #applyEffects})
 * <br>③ 女仆吃自己背包的 ✓ (自动进食走 `completeUsingItem → finishUsingItem` — 见 `WorkEatBehavior` 注释实证)
 *
 * <p><b>饰品形态</b>: {@link TokenBauble} — 戴着持续给「缓慢回血 I」✓。
 * **可堆叠** ✓ (TLM 饰品槽只判 `isEmpty()`, 无单件假设 — 已 fact-forcing 实证 ⇒ 16 个配方产出成立 ✓)。
 *
 * <p><b>双平台食物 API</b> (fact-forcing: `javap` 本地 `forge-1.20.1-47.4.18_mapped_official_1.20.1.jar`
 * 实证 1.20.1 = `nutrition(int)` + `saturationMod(float)` ✓; 1.21.1 侧为 `saturationModifier(float)` ✓)。
 */
public final class TokenItem extends Item {

    /** 饱食度 +2 (用户需求) */
    public static final int HUNGER = 2;

    /**
     * **构建标记** (v79.74): 启动时打进日志 ✓ —— 专治"我改了你测的还是旧包"这个反复出现的坑 ✗
     * (凭证: 用户日志 18:21 早于 jar 装入 18:25 ⇒ 那次测的是旧包 ✓)
     * ⚠ 每次交付 Token 相关改动时**必须递增此值** ✓
     */
    public static final String BUILD_TAG = "token-t-0918-1940";
    /** 回血量: 5 点 (= 2.5 颗心, 用户需求"回5滴血") */
    public static final float HEAL_AMOUNT = 5.0f;
    /** 缓慢恢复 I 时长: 30 秒 = 600t (用户需求) */
    public static final int REGEN_TICKS = 600;
    /** 饱食度上限 (原版饥饿值口径 20 ✓; TLM `setHunger` 不夹, 由调用方保证 ✓) */
    private static final int MAX_HUNGER = 20;
    /** 饱和度系数 (取小值 ⇒ 不额外堆饱食度 ✓) */
    private static final float SATURATION = 0.1f;

    public TokenItem() {
        super(foodProperties());
    }

    private static Item.Properties foodProperties() {
        Item.Properties props = new Item.Properties();
//? if 1.20.1 {
        return props.food(new FoodProperties.Builder()
                .nutrition(HUNGER)
                .saturationMod(SATURATION)
                .alwaysEat()         // ★ 1.20.1 的方法名是 alwaysEat ✓ (1.21 才是 alwaysEdible ✗ — javap 实证 ✓)
                .build());
//?} else {
        return props.food(new FoodProperties.Builder()
                .nutrition(HUNGER)
                .saturationModifier(SATURATION)
                .alwaysEdible()      // ★ 1.21 侧名 ✓
                .build());
//?}
    }

    /**
     * 食用结算 — **三条路径的公共出口** ✓:
     * 玩家吃 / 女仆自动吃 (completeUsingItem→此处) 都会到这里; 右键喂女仆由
     * {@link TokenFeedHandler} 直接调 {@link #applyEffects} ✓ (同一套数值, 不重复实现 ✓)。
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);   // 原版食物: 玩家侧 +2 饱食度 ✓
        applyEffects(entity);
        return result;
    }

    /**
     * 效果结算 (幂等, 可在任一入口调用 ✓): 回 5 血 + 30s 缓慢恢复 I + 女仆侧 +2 饱食度 ✓
     */
    public static void applyEffects(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(HEAL_AMOUNT);                                     // 回 5 点血 ✓
        }
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 0));  // 缓慢恢复 I ✓
        // 女仆 (TLM): LivingEntity 无 FoodData ⇒ 单独给饱食度 ✓ (EntityMaid.getHunger/setHunger 实证 ✓)
        if (entity instanceof EntityMaid maid) {
            maid.setHunger(Math.min(MAX_HUNGER, maid.getHunger() + HUNGER));
        }
    }

    /**
     * **TLM 喂食链路第 ② 段** (`EntityMaid#mobInteract`: `post(event) || stack.interactLivingEntity(...) || openMaidGui`)
     * —— v79.74 补 ✓ (用户实测: "蹲下右键没反应, 换成金苹果就能喂" ✓ ⇒ 金苹果走的正是这一段 ✓)。
     *
     * <p>只认 **蹲下(Shift) + 右键** + 目标女仆 ✓; 其余一律 {@code PASS} ⇒ 绝不干扰"普通右键打开 TLM 界面" ✓。
     * <p>⚠ 与 {@code TokenFeedHandler}(第 ① 段事件) 可能**同一 tick 双命中** ⇒ 结算统一走
     * {@link #feedOnce} 的同 tick 护栏 ✓ (不会一次喂掉 2 个 ✗)。
     */
    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, net.minecraft.world.entity.player.Player player,
                                                                    LivingEntity target, net.minecraft.world.InteractionHand hand) {
        if (!(target instanceof EntityMaid maid)) return net.minecraft.world.InteractionResult.PASS;
        if (!player.isDiscrete()) return net.minecraft.world.InteractionResult.PASS;   // 与金苹果一致: 没蹲才喂 ✓
        if (player.level().isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        feedOnce(maid, player, stack);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** 同一次交互只结算一次 (① 事件段 与 ② interactLivingEntity 段 都在同一 tick 命中 ✓) */
    private static long lastFeedTick = Long.MIN_VALUE;

    /**
     * 喂食结算 (唯一出口 ✓): 消耗 1 个 + 效果 + 日志; 同 tick 重复调用直接返回 false ✓
     *
     * @return 是否真的结算了 (false = 本次被护栏拦住 / 幂等跳过)
     */
    public static boolean feedOnce(EntityMaid maid, net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (maid.level() == null || maid.level().isClientSide) return false;
        long now = maid.level().getGameTime();
        if (now == lastFeedTick) return false;      // ① / ② 同 tick 双命中 ⇒ 只算一次 ✓
        lastFeedTick = now;
        // 食用走 **`maid.eat(level, stack)`** —— 与 TLM 金苹果 (`ApplyGoldenAppleEvent`) **同源** ✓:
        //   · 原版进食音效 ✓ · 食物属性/`usingConvertsTo` ✓ · TLM 的 `MaidAfterEatEvent` ✓
        //  ⚠ 用户裁定 (2026-09-18): **坐姿问题以用户观察为准** ✓ ——
        //    我曾据日志 `isMaidInSittingPose()=false` 推断"只是进食动画" ✗ ⇒ **作废** ✗ (用户: "你肯定错了" ✓)
        //    ⇒ 保持与金苹果同源 ✓ (不要走"手动播音效"的偏门 ✗)
        //  物品扣减: `eat` 是否消耗随实体类型而异 ⇒ **按"没扣就手动扣 1"处理** ✓ (不会双扣 ✗ / 不会不扣 ✗)
        int countBefore = stack.getCount();
        maid.eat(maid.level(), stack);
        if (stack.getCount() == countBefore) {
            stack.shrink(1);                        // eat 未消耗 (非玩家实体) ⇒ 手动扣 1 ✓
        }
        applyEffects(maid);                         // 追加: 回 5 血 + 30s 缓慢恢复 I (+ 女仆饱食度兜底 ✓)
        // ★ v79.74 (用户实测"蹲下右键还是坐下了"): **cancel 挡不住 TLM 的坐下** ——
        //   TLM `SwitchSittingEvent`(priority=LOWEST ✓) 里**没有 `isCanceled()` 判断** ✗
        //   ⇒ 只要 Shift+右键 它就 `setInSittingPose(!…)` ✓ (金苹果同理 ✓)
        //   ⇒ 本 handler 跑在它**之前** ✗ (无法抢先) ⇒ 改为**本 tick 末尾撤销坐姿** ✓:
        //     `server.execute(...)` 的任务在**当前 tick 处理完之后**才跑 ⇒ 那时坐下已发生, 撤销有效 ✓
        //     (只在她**真的因本次交互坐下**时复位 ✓; 用户裁定: 喂 Token 不该让她坐下 ✓)
        if (maid.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            LittleMaidMoreAction.LOGGER.info("[Token][诊断] 已排队撤销坐姿任务 (tick={}, 当前坐姿={})",
                    sl.getGameTime(), maid.isMaidInSittingPose());
            sl.getServer().execute(() -> {
                boolean sitting = maid.isMaidInSittingPose();
                LittleMaidMoreAction.LOGGER.info("[Token][诊断] 撤销任务执行(tick末): 坐姿={} ⇒ {}",
                        sitting, sitting ? "复位为站立" : "已是站立, 无需处理");
                if (sitting) {
                    maid.setInSittingPose(false);
                }
                // ★ +2t 复查 (用户实测"确实坐下了" ✓ 而本 tick 内检查点却是站立 ✗ ⇒
                //   必是**之后**又被坐下的 ⇒ 记下是谁在何时动它 ✓; 若真坐了就再复位一次 ✓)
                sl.getServer().execute(() -> sl.getServer().execute(() -> {
                    boolean later = maid.isMaidInSittingPose();
                    LittleMaidMoreAction.LOGGER.info("[Token][诊断] +2t 复查: 坐姿={} (期望 false)", later);
                    if (later) {
                        LittleMaidMoreAction.LOGGER.warn("[Token][诊断] +2t 时她**确实坐着** ✗ ⇒ 有别的路径在坐她"
                                + " (我的事件 cancel 未覆盖该路径) ⇒ 再次复位 ✓");
                        maid.setInSittingPose(false);
                    }
                }));
            });
        } else {
            LittleMaidMoreAction.LOGGER.warn("[Token][诊断] level 不是 ServerLevel ⇒ 无法排队撤销坐姿 (level={})", maid.level());
        }
        LittleMaidMoreAction.LOGGER.info("[Token] 已喂女仆 (by {}): 饱食={} 血={} 位置={}",
                player.getName().getString(), maid.getHunger(), maid.getHealth(), maid.blockPosition().toShortString());
        return true;
    }
}
