package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidEmojiApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidEmojiType;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.HaqiOwnerVoicePacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.PassiveSignalSkeleton;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HaqiService;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSignal;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvSnapshot;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.Signals;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.EnvScanner;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.AnimExecute;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.movement.BrainHelper;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker; // 双版本同包 (TLM 1.20/1.21 引用实证)
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 哈气被动任务 (v79.9) — 女仆靠近其他女仆时概率触发, 移动到她旁边看着她并随机播放音频。
 * v79.20: 新增对主人变体 (target_type=owner) — 主人 2 格内且旁边无女仆时概率触发,
 * 声音 = littlemaid_peco 包 idle 子集随机 (客户端网络包), 表情 = 对主人子集随机,
 * 挥击掉主人血且不反击; 配置 6 项独立 (enabled/chance/duration/volume/hit_chance/hit_damage,
 * 后缀 _to_owner)。
 *
 * <p>触发: MAID_NEARBY 边沿信号 (EnvSense 200t 广播) → 2 格内 maid 过滤 → 概率掷骰 →
 * {@link TaskDispatcher#submitPassive}。互斥: 哈气运行中其他任务不启动 (TaskDispatcher 门控)。
 *
 * <p>执行状态机 (pipelineData: state/targetUuid/timer/audioTicks/hitTicks/targetType):
 * MOVE (导航到目标旁 1 格) → LOOK (看着她 + 随机音频 + 总时长 = 基础 + 音频时长) → cancelPassive。
 * v79.16: LOOK 期间 pin 位置 (keepAlive + stop) 防随机走 + 蹲下; onCleanup 恢复。
 * v79.16b: LOOK_TARGET 记忆 (TLM LookAtTargetSink 转头) + yaw 三轴直设; 蹲下分模型
 * (YSM=CROUCHING pose 映射 sneak / TLM 原版=坐姿动画 — 原版无蹲动画)。
 * v79.17: LOOK 期间概率挥击 (hit_chance 配置, 默认 30%) — 延迟 15t 后真实攻击
 * (swing 动画 + mobAttack 伤害, 伤害默认 1 点血可配置) + 原版挥击音; 女仆打女仆不引发反击
 * (TLM DefaultMonsterType FRIENDLY 排除 TamableAnimal 实证)。
 * v79.18: LOOK 进入时经 {@link AnimExecute} 播放哈气动画 (双通道分流, 自动同步):
 * YSM 模型 → playRouletteAnim (动画须在 YSM 模型包内同名 "haqi"); TLM geckolib 模型 →
 * ISS 注册的 haqi.animation.json (lma_anim 键 + seq + LmaAnimSyncMessage 同步)。onCleanup
 * stopRoulette + unfreeze (AnimExecute fallback freezeAI=true 置 IS_PANICKING — 防残留)。
 * v79.20: 进入 LOOK 时发表情气泡 (对女仆 emoji_10/05/09 / 对主人 emoji_01/02/20-24x24 随机,
 * 与语音随机独立); 对主人声音走 {@link HaqiOwnerVoicePacket} (服务端传 maidId+volume,
 * 客户端 peco 11 文件子集随机); 对主人时长 = 基础 (客户端文件时长服务端不可知)。
 *
 * <p>⚠ 音频命名非 maid* 前缀 (TLM playSound 音效包路线实证); 音频时长表为 ogg 实测
 * (granule position, 项目根 sounds/ 目录导入)。
 *
 * <p>v79.61x 架构审计 B (五层尺): 挥击/音效执行细节抽至 {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HaqiService},
 * 触发 (onSignal/HaqiTrigger) 与状态机 (MOVE/LOOK) 留管线。
 */
public final class HaqiPipeline implements PassiveSignalSkeleton, TaskConfigurable {

    /** pipelineData 键 (存于 lma_pl_haqi compound) */
    public static final String KEY_STATE = "state";
    public static final String KEY_TARGET = "target_uuid";
    public static final String KEY_TIMER = "timer";
    private static final String KEY_AUDIO_TICKS = "audio_ticks";
    /** 挥击倒计时 (进入 LOOK 后延迟, 0=不挥 / -1=已挥) */
    private static final String KEY_HIT_TICKS = "hit_ticks";
    /** 目标类型 (maid=对女仆 / owner=对主人; 向后兼容: 读空默认 maid) */
    public static final String KEY_TARGET_TYPE = "target_type";
    public static final String TARGET_MAID = "maid";
    /** 挥击延迟 (tick) — 进入 LOOK 后等一会儿再拍 */
    private static final int HIT_DELAY_TICKS = 15;

    /** 哈气动画名 — 对应 haqi.animation.json 内动画 key "haqi" (YSM 模型包内需同名动画) */
    public static final String HAQI_ANIM = "haqi";
    /** 对主人哈气动画 — maimeng.animation.json 内 key "maimeng" (用户裁定: 对女仆仍 haqi 不动) */
    public static final String HAQI_OWNER_ANIM = "maimeng";

    /**
     * 触发双通道 ({@link HaqiTrigger} 直接扫描 / onSignal) 与状态机共享的状态数据。
     * key 构造复用 {@link TaskPipeline#pipelineData} 默认实现 (lma_pl_haqi compound)。
     */
    public static CompoundTag stateData(EntityMaid maid) {
        return new HaqiPipeline().pipelineData(maid);
    }

    /** 触发距离: 3 格 (distSqr <= 9) */
    private static final double TRIGGER_DIST_SQR = 9.0;
    /** 到达目标旁判定: 1 格 (distSqr <= 1.5²) */
    private static final double ARRIVE_DIST_SQR = 2.25;
    /** 目标丢失判定: 4 格 (distSqr > 16) */
    private static final double LOST_DIST_SQR = 16.0;

    private static final Random RANDOM = new Random();

    private enum State { MOVE, LOOK }

    @Override public String taskType() { return "haqi"; }
    @Override public boolean isLongRunning() { return true; }

    /** 哈气结束清理 (pipelineData) — 蹲下不做 (YSM 模型不响应 pose CROUCHING, 用户裁定删除) */
    @Override
    public void onCleanup(EntityMaid maid) {
        // YSM 动画停止 (cancelPassive 先调 onCleanup, 覆盖全部退出路径)
        YsmOutput.stopRoulette(maid);
        // AnimExecute fallback freezeAI=true 置 IS_PANICKING — 结束必须解除 (Brain memory 闭环)
        BrainHelper.unfreeze(maid);
        // 动画键闭环 — 清 ANIM_MODE 并同步 → 客户端 provider 返回 null →
        // AnimationManager: CASTING→NONE 允许终止 → clearAnimationCache (FULL 循环强制停止, 防收回女仆后继续播)
        var root = maid.getPersistentData();
        // ANIM 运行时键全清 (含 SEQ — 残留 seq 是跨 session 重播源)。
        // 被动终结 (cancelPassive) 不调 clearAll, 此处是唯一清理点。
        for (String key : TaskKeys.ANIM_RUNTIME_KEYS) {
            root.remove(key);
        }
        var stopTag = new net.minecraft.nbt.CompoundTag();
        stopTag.putString(TaskKeys.ANIM_MODE, "");
        com.github.xiaozhaoz1.littlemaidmoreaction.network.LmaAnimSyncMessage.sendToTracking(maid, stopTag);
        PassiveSignalSkeleton.super.onCleanup(maid);
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        // 声明信号需求 — MAID_NEARBY 边沿触发 (EnvSenseBroadcaster 分发门禁)
        return okSignals(Set.of(Signals.envOf(EnvSignal.MAID_NEARBY)));
    }

    /** 触发: MAID_NEARBY → 2 格内 maid → 概率掷骰 → submitPassive (互斥由 TaskDispatcher 门控) */
    @Override
    public void onSignal(EntityMaid maid, EnvSnapshot snap, String signalId) {
        // 总开关 + 对主人二级开关, 任一开则进入 (内部再分别门控)
        if (!PassiveTaskConfig.HAQI_ENABLED.get() && !PassiveTaskConfig.HAQI_ENABLED_TO_OWNER.get()) return;
        // 防重复 (自身运行中)
        String key = TaskKeys.passiveKey(taskType());
        if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(key))) return;
        if (snap == null || snap.world() == null) return;

        // 2 格内其他 maid (快照实体列表, 距离过滤)
        List<LivingEntity> maids = snap.entities(EnvScanner.CAT_MAID);
        EntityMaid target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : maids) {
            if (e == maid || !(e instanceof EntityMaid m) || !m.isAlive()) continue;
            double d = e.blockPosition().distSqr(maid.blockPosition());
            if (d <= TRIGGER_DIST_SQR && d < best) {
                best = d;
                target = m;
            }
        }

        // 有女仆且概率命中 → 锁女仆 (显式写 target_type=maid)
        if (target != null && RANDOM.nextDouble() < PassiveTaskConfig.HAQI_CHANCE.get()) {
            // 锁定目标 + 提交 (写 pipelineData compound — 原写根导致状态机读空立即取消)
            var data = stateData(maid);
            data.putString(KEY_TARGET, target.getStringUUID());
            data.putString(KEY_TARGET_TYPE, TARGET_MAID);
            data.putString(KEY_STATE, State.MOVE.name());
            data.putInt(KEY_TIMER, 0);
            TaskDispatcher.submitPassive(maid, taskType());
            return;
        }
        // 女仆未触发 → 对主人变体 (独立开关/概率/距离, 与 HaqiTrigger 同通道复用)
        HaqiTrigger.tryTriggerOwner(maid);
    }

    /** 执行状态机 (被动 tick 仅驱动 IN_PROGRESS) */
    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        CompoundTag data = pipelineData(maid);
        String stateName = data.getString(KEY_STATE);
        State state;
        try {
            state = stateName.isEmpty() ? State.MOVE : State.valueOf(stateName);
        } catch (IllegalArgumentException ex) {
            state = State.MOVE;
        }

        // 目标类型 (向后兼容: 旧存档无 target_type → 按 maid 处理)
        String targetType = data.getString(KEY_TARGET_TYPE);
        if (targetType.isEmpty()) targetType = TARGET_MAID;

        LivingEntity target = resolveTarget(world, data.getString(KEY_TARGET), targetType);
        if (target == null || !target.isAlive()) {
            TaskDispatcher.cancelPassive(maid, taskType());
            return;
        }
        // 目标远离 → 放弃
        if (target.blockPosition().distSqr(maid.blockPosition()) > LOST_DIST_SQR) {
            TaskDispatcher.cancelPassive(maid, taskType());
            return;
        }

        switch (state) {
            case MOVE -> {
                BlockPos targetPos = target.blockPosition();
                if (maid.blockPosition().distSqr(targetPos) <= ARRIVE_DIST_SQR) {
                    // 到达 → 看她 + 音频 + 算总时长 (按目标类型分流)
                    enterLook(world, maid, data, targetType);
                } else {
                    NavigationUtil.navigateTo(maid, targetPos);
                }
            }
            case LOOK -> {
                // LOOK_TARGET 记忆 — TLM LookAtTargetSink 每实体 tick 读它转头 (原 lookAt 只设 yRot 被覆盖)
                maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
                maid.getLookControl().setLookAt(target, 10.0F, 40.0F);
                // yaw 三轴直设 (身体+头转向目标; lookAt 不设 yHeadRot)
                double dx = target.getX() - maid.getX();
                double dz = target.getZ() - maid.getZ();
                float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
                maid.setYRot(yaw);
                maid.setYHeadRot(yaw);
                maid.setYBodyRot(yaw);
                // 防随机走 (pin WALK_TARGET 脚格 + 停导航)
                NavigationUtil.keepAlive(world, maid);
                maid.getNavigation().stop();
                // 挥击倒计时 → 到 0 执行一次真实攻击 (swing 动画 + mobAttack 伤害 + 挥击音)
                int hitTicks = data.getInt(KEY_HIT_TICKS);
                if (hitTicks > 0) {
                    hitTicks--;
                    if (hitTicks == 0) {
                        HaqiService.doHit(maid, target, targetType);
                        hitTicks = -1; // 已挥, 防重复
                    }
                    data.putInt(KEY_HIT_TICKS, hitTicks);
                }
                int timer = data.getInt(KEY_TIMER) + 1;
                int total = data.getInt(KEY_AUDIO_TICKS);
                if (total <= 0) total = PassiveTaskConfig.HAQI_DURATION_TICKS.get();
                if (timer >= total) {
                    // v79.58 续哈气 (用户裁定): 结束瞬间扫描 2 格内女仆 — 有 → 无缝续
                    // (tryContinue 重置 MOVE/timer, 消除 20t 周期间隙 → 哈气持续期间
                    // 自救等被动永不触发); 无 → 停止
                    if (HaqiTrigger.tryContinue(world, maid)) {
                        return;
                    }
                    TaskDispatcher.cancelPassive(maid, taskType());
                    return;
                }
                data.putInt(KEY_TIMER, timer);
            }
        }
    }

    /**
     * 进入 LOOK — 按目标类型分流:
     * <ul>
     *   <li>maid (原版): 随机 {@link HaqiService} 音频清单 + playSound, 总时长 = 基础 + 音频时长</li>
     *   <li>owner: HaqiOwnerVoicePacket 网络包 (客户端 peco 包 11 文件子集随机),
     *       总时长 = 基础 (客户端文件时长服务端不可知)</li>
     * </ul>
     * 共点: 挥击掷骰 (独立配置) + 表情气泡 (目标类型子集随机) + 哈气动画。
     */
    private static void enterLook(ServerLevel world, EntityMaid maid, CompoundTag data, String targetType) {
        data.putString(KEY_STATE, State.LOOK.name());
        data.putInt(KEY_TIMER, 0);
        int total;
        if (HaqiService.TARGET_OWNER.equals(targetType)) {
            // 对主人: peco idle 子集随机声 (客户端), 挥击对主人概率/伤害
            int base = PassiveTaskConfig.HAQI_DURATION_TICKS_TO_OWNER.get();
            HaqiOwnerVoicePacket.sendToTracking(maid, PassiveTaskConfig.HAQI_VOLUME_TO_OWNER.get().floatValue());
            data.putInt(KEY_AUDIO_TICKS, base);
            // 概率挥击 — 掷骰命中则延迟 15t 后拍一下 (伤害走对主人配置, 主人不反击)
            data.putInt(KEY_HIT_TICKS,
                    RANDOM.nextDouble() < PassiveTaskConfig.HAQI_HIT_CHANCE_TO_OWNER.get()
                            ? HIT_DELAY_TICKS : 0);
            total = base;
        } else {
            // 对女仆: 原版分支逐行不变
            String sound = HaqiService.SOUND_NAMES[RANDOM.nextInt(HaqiService.SOUND_NAMES.length)];
            HaqiService.playSound(maid, sound);
            int base = PassiveTaskConfig.HAQI_DURATION_TICKS.get();
            int audio = HaqiService.SOUND_TICKS.getOrDefault(sound, 60);
            data.putInt(KEY_AUDIO_TICKS, base + audio);
            // 概率挥击 — 掷骰命中则延迟 15t 后拍一下 (伤害走配置, 默认 1 点血)
            data.putInt(KEY_HIT_TICKS,
                    RANDOM.nextDouble() < PassiveTaskConfig.HAQI_HIT_CHANCE.get()
                            ? HIT_DELAY_TICKS : 0);
            total = base + audio;
        }
        // 表情气泡 — 对女仆/对主人子集随机, 与语音随机相互独立
        MaidEmojiApi.send(maid, HaqiService.TARGET_OWNER.equals(targetType) ? MaidEmojiType.OWNER : MaidEmojiType.MAID);
        // 诊断: 触发女仆模型状态 (isYsm 服务端判断 — YSM 通道分流依据)
        LittleMaidMoreAction.LOGGER.info("[LMA/Haqi] LOOK 进入 maid={} targetType={} isYsmModel={}",
                maid.getStringUUID(), targetType, maid.isYsmModel());
        // 哈气动画 — AnimExecute FULL 双通道分流 (YSM=playRouletteAnim / TLM=ISS geckolib:
        // START 播一次 → CASTING 循环 (durCasting=LOOK 总时长×3 — 用户裁定: 动画持续时长 = 哈气总时长 3 倍,
        // 哈气期间持续趴着) → END 播一次 → 自动停; FULL 而非 INSTANT — INSTANT 的 LOOP 无法停止)
        // 对主人播 maimeng 动画, 对女仆仍 haqi; 骨名 PascalCase, TLM/YSM 双模型均匹配 → 统一播同一个 (错题 #134)
        // (演化史见 changelog)
        String anim = HaqiService.TARGET_OWNER.equals(targetType) ? HAQI_OWNER_ANIM : HAQI_ANIM;
        AnimExecute.execute(world, maid, "FULL", "",
                anim, anim, anim,
                "", String.valueOf(total * 3), "", false);
    }

    /**
     * 锁定目标 (UUID + 目标类型 → 世界实体):
     * owner → Player (在线主人) / maid → EntityMaid (向后兼容默认)。
     */
    private static LivingEntity resolveTarget(ServerLevel world, String uuid, String targetType) {
        if (uuid.isEmpty()) return null;
        try {
            java.util.UUID id = java.util.UUID.fromString(uuid);
            Entity e = world.getEntity(id);
            if (HaqiService.TARGET_OWNER.equals(targetType)) {
                if (e instanceof Player p) return p;
                // 兜底: PlayerList — 在线主人权威列表 (真玩家与 getEntity 同结果; gametest mock 玩家
                // 注册后不在实体索引实证 1.21: makeMockServerPlayerInLevel → getEntity(uuid) 为 null)
                return world.getServer().getPlayerList().getPlayer(id);
            }
            return e instanceof EntityMaid m ? m : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
