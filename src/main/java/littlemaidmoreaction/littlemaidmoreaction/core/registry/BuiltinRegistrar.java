package littlemaidmoreaction.littlemaidmoreaction.core.registry;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

// === 条件 ===
import littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.condition.target.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.condition.world.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.condition.debug.DebugAllConditionsCondition;

// === 动作 ===
import littlemaidmoreaction.littlemaidmoreaction.impl.action.combat.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.control.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.effect.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.item.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.maid.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.maid_ext.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.movement.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.visual.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.world.*;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.debug.DebugAllActionsAction;

/**
 * v27 内置条件/动作显式注册器 — 编译期安全，零反射。
 *
 * <p>ForgeClassScanner 失败时（注册表为空），直接通过 {@code new} 实例化所有内置扩展。
 * 相比 v10-v26 的 FQCN 字符串 + {@code Class.forName} 反射方案，
 * 此版本使用直接的 Java 构造器调用 —— 编译器保证类名正确、构造函数存在。</p>
 *
 * <p>新增条件/动作：在 impl/ 包下添加 {@code @RuleCondition}/@{@code RuleAction} 类后，
 * 同时在此文件中添加一行 {@code register(new XxxCondition/Action())}。</p>
 */
public final class BuiltinRegistrar {

    // ── 条件注册 ──

    public static void registerAllConditions() {
        // maid
        register(new FavorabilityCondition());
        register(new IsCombatTaskCondition());
        register(new IsInWaterCondition());
        register(new IsMainhandAttackCondition());
        register(new IsOnFireCondition());
        register(new IsOwnerAttackerCondition());
        register(new IsOwnerNearbyCondition());
        register(new IsOwnerTargetCondition());
        register(new IsSneakingCondition());
        register(new IsTamedCondition());
        register(new MaidAirSupplyCondition());
        register(new MaidArmorCondition());
        register(new MaidArmorToughnessCondition());
        register(new MaidAttackDamageCondition());
        register(new MaidAttackSpeedCondition());
        register(new MaidAttrCondition());
        register(new MaidBackpackFluidCondition());
        register(new MaidBackpackSlotsCondition());
        register(new MaidBackpackTypeCondition());
        register(new MaidBaubleCondition());
        register(new MaidBaubleCountCondition());
        register(new MaidCanBrainMoveCondition());
        register(new MaidCanClimbCondition());
        register(new MaidCanPathToCondition());
        register(new MaidExperienceCondition());
        register(new MaidFallDistanceCondition());
        register(new MaidGlowingCondition());
        register(new MaidHasAnyEffectCondition());
        register(new MaidHasBackpackCondition());
        register(new MaidHasBootsCondition());
        register(new MaidHasChestplateCondition());
        register(new MaidHasCurseCondition());
        register(new MaidHasEffectCondition());
        register(new MaidHasHelmetCondition());
        register(new MaidHasLeggingsCondition());
        register(new MaidHasRestrictionCondition());
        register(new MaidHasShieldCondition());
        register(new MaidHasTargetCondition());
        register(new MaidHasWeaponCondition());
        register(new MaidHealthCondition());
        register(new MaidHealthRatioCondition());
        register(new MaidHomeModeCondition());
        register(new MaidHungerCondition());
        register(new MaidInRestrictionCondition());
        register(new MaidInvisibleCondition());
        register(new MaidIsAimingCondition());
        register(new MaidIsBabyCondition());
        register(new MaidIsBeggingCondition());
        register(new MaidIsBlockingCondition());
        register(new MaidIsFishingCondition());
        register(new MaidIsHoldingProjectileCondition());
        register(new MaidIsInLavaCondition());
        register(new MaidIsInRainCondition());
        register(new MaidIsInvulnerableCondition());
        register(new MaidIsPickupCondition());
        register(new MaidIsRideableCondition());
        register(new MaidIsRidingCondition());
        register(new MaidIsSittingCondition());
        register(new MaidIsSleepingCondition());
        register(new MaidIsSprintingCondition());
        register(new MaidIsStruckByLightningCondition());
        register(new MaidIsSwimmingCondition());
        register(new MaidIsSwingingCondition());
        register(new MaidIsUsingItemCondition());
        register(new MaidLuckCondition());
        register(new MaidMainhandCondition());
        register(new MaidMainhandTagCondition());
        register(new MaidMaxAirSupplyCondition());
        register(new MaidMaxHealthCondition());
        register(new MaidModelIdCondition());
        register(new MaidMovementSpeedCondition());
        register(new MaidOffhandCondition());
        register(new MaidOnHurtCondition());
        register(new MaidRestrictRadiusCondition());
        register(new MaidScheduleActivityCondition());
        register(new MaidScheduleCondition());
        register(new MaidSoundPackCondition());
        register(new MaidSwingingArmsCondition());
        register(new MaidTaskCondition());
        register(new OwnerArmorCondition());
        register(new OwnerDistanceCondition());
        register(new OwnerHasAttackTargetCondition());
        register(new OwnerHealthCondition());
        register(new OwnerHealthRatioCondition());
        register(new OwnerHoldingItemCondition());
        register(new OwnerOffhandCondition());
        register(new RandomCondition());

        // target
        register(new CanSeeTargetCondition());
        register(new DistanceCondition());
        register(new HealthRatioCondition());
        register(new TargetArmorCondition());
        register(new TargetBurningCondition());
        register(new TargetDistanceHCondition());
        register(new TargetDistanceVCondition());
        register(new TargetHasEffectCondition());
        register(new TargetHealthCondition());
        register(new TargetHoldingItemCondition());
        register(new TargetIsAliveCondition());
        register(new TargetIsAnimalCondition());
        register(new TargetIsBabyCondition());
        register(new TargetIsBossCondition());
        register(new TargetIsMonsterCondition());
        register(new TargetIsOnGroundCondition());
        register(new TargetIsPlayerCondition());
        register(new TargetIsUndeadCondition());
        register(new TargetMaxHealthCondition());
        register(new TargetNameCondition());
        register(new TargetTypeCondition());
        register(new WouldLethalCondition());

        // world
        register(new AltarNearbyCondition());
        register(new BlockAboveCondition());
        register(new BlockAtCondition());
        register(new BlockBelowCondition());
        register(new BypassesArmorCondition());
        register(new ContainerHasItemCondition());
        register(new DamageTypeCondition());
        register(new DimensionCondition());
        register(new EnvSensorCondition());
        register(new IsCriticalAttackCondition());
        register(new PlayerHasItemCondition());
        register(new StructureNearbyCondition());
        register(new WorldBiomeCondition());
        register(new WorldDifficultyCondition());
        register(new WorldHasDaylightCondition());
        register(new WorldIsDayCondition());
        register(new WorldIsThunderingCondition());
        register(new WorldLightLevelCondition());
        register(new WorldMoonPhaseCondition());
        register(new WorldNightCondition());
        register(new WorldRainingCondition());
        register(new WorldTimeCondition());

        // debug
        register(new DebugAllConditionsCondition());

        LittleMaidMoreAction.LOGGER.info(
            "[BuiltinRegistrar] 直接注册 {} 个内置条件 (回退路径, 零反射)", ConditionRegistry.size());
    }

    // ── 动作注册 ──

    public static void registerAllActions() {
        // combat
        register(new BleedAction());
        register(new DealDamageAction());
        register(new DealPercentDamageAction());
        register(new DealTrueDamageAction());
        register(new DoHurtTargetAction());
        register(new ExecutionKillAction());
        register(new ExtinguishAction());
        register(new HealAction());
        register(new HealPercentAction());
        register(new KnockbackAction());
        register(new LaunchAction());
        register(new LaunchProjectileAction());
        register(new LifeStealAction());
        register(new LifeStealPercentAction());
        register(new SetFireAction());
        register(new ShieldAction());

        // control
        register(new BreakAction());
        register(new CancelEventAction());
        register(new DisableRuleAction());
        register(new EnableRuleAction());
        register(new OpenMaidEditorAction());
        register(new RandomAction());
        register(new RepeatAction());
        register(new ResetAnimAction());
        register(new SendMessageAction());
        register(new WaitAction());
        register(new WaitAnimAction());
        register(new WaitUntilAction());

        // effect
        register(new ApplyEffectAction());
        register(new ClearEffectsAction());

        // item
        register(new ClearInventoryAction());
        register(new DropItemAction());
        register(new ExtractMaidXpAction());
        register(new GiveItemAction());
        register(new littlemaidmoreaction.littlemaidmoreaction.impl.action.maid.RepairItemAction());

        // maid
        register(new AutoMatchCropAction());
        register(new ClearRestrictionAction());
        register(new ForceTargetAction());
        register(new littlemaidmoreaction.littlemaidmoreaction.impl.action.maid.RepairItemAction());
        register(new RestoreMaidTaskAction());
        register(new SaveSwitchTaskAction());
        register(new SetExperienceAction());
        register(new SetFavorAction());
        register(new SetGlowingAction());
        register(new SetHomeAction());
        register(new SetHomeModeAction());
        register(new SetHungerAction());
        register(new SetInvisibleAction());
        register(new SetInvulnerableAction());
        register(new SetMaidTaskAction());
        register(new SetModelAction());
        register(new SetPickupAction());
        register(new SetScheduleAction());
        register(new SetSilentAction());
        register(new SetSittingAction());
        register(new SetSoundPackAction());

        // maid_ext
        register(new DropHandItemAction());
        register(new ModifyMaidAttrAction());
        register(new SetAimingAction());
        register(new SetBackpackShowItemAction());
        register(new SetBaubleAction());
        register(new SetBeggingAction());
        register(new SetCanClimbAction());
        register(new SetRideableAction());
        register(new SetSwingingAction());
        register(new SwapHandsAction());
        register(new TeleportToOwnerAction());

        // movement
        register(new DashAction());
        register(new FaceTargetAction());
        register(new FollowOwnerAction());
        register(new FreezeAiAction());
        register(new GuardPosAction());
        register(new LeapAction());
        register(new PullAction());
        register(new PushAction());
        register(new SetMotionAction());
        register(new SlowAction());
        register(new SwapPositionAction());
        register(new TeleportAction());

        // visual
        register(new PlayAnimAction());
        register(new PlaySoundAction());
        register(new PlaySoundAtAction());
        register(new PlayWeaponAnimAction());
        register(new SpawnHeartParticleAction());
        register(new SpawnParticleAction());

        // world
        register(new AnvilRepairAction());
        register(new BreakBlockAction());
        register(new DamageNearbyAction());
        register(new EnchantItemAction());
        register(new ExecuteCommandAction());
        register(new ExplosionAction());
        register(new InteractBlockAction());
        register(new PlaceAltarItemAction());
        register(new PlaceBlockAction());
        register(new PutInContainerAction());
        register(new SendBubbleAction());
        register(new SendChatAction());
        register(new SetTimeAction());
        register(new SetWeatherAction());
        register(new SmeltItemAction());
        register(new SpawnEntityAction());
        register(new SummonLightningAction());
        register(new TakeFromContainerAction());
        register(new TradeVillagerAction());

        // debug
        register(new DebugAllActionsAction());

        LittleMaidMoreAction.LOGGER.info(
            "[BuiltinRegistrar] 直接注册 {} 个内置动作 (回退路径, 零反射)", ActionRegistry.size());
    }

    // ── 辅助方法 ──

    private static void register(ICondition cond) {
        if (!ConditionRegistry.has(cond.key())) {
            ConditionRegistry.register(cond);
        }
    }

    private static void register(IAction action) {
        if (!ActionRegistry.has(action.id())) {
            ActionRegistry.register(action);
        }
    }

    private BuiltinRegistrar() {}
}
