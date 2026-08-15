package com.github.xiaozhaoz1.littlemaidmoreaction.compat.numen;

import com.dwinovo.numen.entity.CompanionFactory;
import com.dwinovo.numen.entity.CompanionLifecycle;
import com.dwinovo.numen.entity.NumenPlayer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.NumenCompanionSyncPayload;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Numen 假人桥 (v75.1 按钮驱动石板化) — 假人成唯一主体, 女仆收石板。
 *
 * <p>流程 (用户规格): 开 ai_control 任务 → 设置 GUI 点"变成假人" (ACTION_TRANSFORM=16)
 * → 生成假人 + 自动设女仆当前模型 (YSM 指令) + 同步状态 (最大血量/血量) → 女仆<b>带全背包</b>
 * 收进 TLM 智慧石板 (ItemSmartSlab, storeMaidData) 进玩家背包 → 女仆实体移除。
 * 放石板 → 新女仆 idle 回归 (自带物品) + 假人销毁 (物品爆一地)。
 *
 * <p>收起前必须 TaskDispatcher.cancel (石板存 idle 状态) — 否则放女仆恢复 gate →
 * 又生成假人 → 循环 (用户硬性要求)。
 *
 * <p>生命周期: tick 只做兜底清理 + 广播 (v75.1 变身由按钮触发, 不再自动);
 * SHELVED 模式: BINDINGS 有 + 女仆实体不在 (石板中) → 假人独立存活, 等石板放出交接
 * (EntityJoinLevelEvent → onMaidRestored)。
 *
 * <p>v74/v75 的镜像/物品转移逻辑 (跟随/HP 同步/挡射线/swing 镜像/背包搬移) 全部删除 —
 * 假人独立驱动, 女仆石板自带物品。
 */
public final class NumenMaidBridge {

    /** maid UUID → companion UUID 绑定表 (内存态; 重启后重开任务幂等恢复) */
    private static final Map<UUID, UUID> BINDINGS = new ConcurrentHashMap<>();
    /** 收起流程进行中 — tick 跳过防竞态 */
    private static final java.util.Set<UUID> SHELVING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 假人 UUID 集广播节拍 (服务器 tick 计数, 全维度去重) */
    private static long lastBroadcastTick = -1;
    /** 绑定清空后待发一次空集广播 (客户端 dispose 孤儿 LLM 环) */
    private static boolean pendingEmptyBroadcast = false;

    /** 用户删除标记 (maidId) — G 面板删除后不重生; 变身按钮重新允许 */
    private static final java.util.Set<UUID> DELETED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 石板 NBT LMA 键: companion UUID (交接识别) */
    public static final String SLAB_COMPANION_KEY = "lma_companion";
    /** 女仆 PD 键: 固定 companion UUID (v75.2 — YSM 按 UUID 分配模型, UUID 恒定则一次设置永久; v79.55 收编 TaskKeys) */
    private static final String PD_COMPANION_UUID = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.COMPANION_UUID;
    /** maidId → {provider, voice} (v75.3: SHELVED 广播用 — 女仆收石板后读不到 TaskConfigs) */
    private static final Map<UUID, String[]> MAID_AI_CFG = new ConcurrentHashMap<>();
    /** 随机台词表 (v75.3: TLM kaomoji.json core 子集 — 与女仆同款颜文字) */
    private static final String[] RANDOM_CHAT_LINES = {
            "(๑>◡<๑)", "(｡•̀ᴗ-)✧", "(⁄ ⁄•⁄ω⁄•⁄ ⁄)⁄", "(≧∇≦)/", "(๑´0`๑)",
            "( • ω • )✧", "(ง •_•)ง", "(づ￣ ³￣)づ", "(｡･∀･)ﾉﾞ", "(⁎⁍̴̛ᴗ⁍̴̛⁎)",
            "(๑˘︶˘๑)", "(●ↀωↀ●)✧", "(￣▽￣)ノ", "(｡•ㅅ•｡)♡", "(≧ω≦)",
            "(๑•ㅂ•)و✧", "(ฅ´ω`ฅ)", "(°▽°)/", "(๑´ڡ`๑)☆", "(๑•̀ㅁ•́๑)✧",
            "(๑•́ ₃ •̀๑)", "(*/ω＼*)", "( •̀ ω •́ )✧", "(๑╹ᆺ╹)", "(￣ε￣＠)",
            "(๑¯∀¯๑)", "(｡◕‿◕｡)", "(≧◡≦)", "(⁀ᗢ⁀)", "(｡♥‿♥｡)",
            "(づ｡◕‿‿◕｡)づ", "( ˘ ³˘)♥", "(๑˃ᴗ˂)ﻭ", "(｡･ω･｡)ﾉ♡", "(＠＾－＾)",
            "(〃￣ω￣〃ゞ)", "(･ω<)☆", "(✿◠‿◠)", "( •ω•ฅ)", "(´｡• ᵕ •｡`)",
            "(*≧ω≦)", "(oﾟ▽ﾟ)o", "(•̀ᴗ•́)و ̑̑", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "(☆ω☆)",
            "(๑>ᴗ<๑)", "٩(｡•́‿•̀｡)۶", "(๑♡⌓♡๑)", "(≧▽≦)", "^(◕ᴗ◕)^"
    };

    /** 读女仆 ai_control 配置 (llm_provider/voice, 空 → 全局默认) */
    private static String readAiConfig(EntityMaid maid, String key) {
        String v = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.TaskConfigs.get(maid, "ai_control").getString(key);
        if (!v.isEmpty()) return v;
        return key.equals(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline.KEY_PROVIDER)
                ? com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.AI_LLM_PROVIDER.get()
                : com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.AI_VOICE.get();
    }

    private NumenMaidBridge() {}

    static {
        // 面板删除 (dismiss) 拦截: 清理绑定 + 标记不重生 (物品随假人 .dat, 重开按钮回归)
        CompanionLifecycle.onRemove(body -> {
            if (body.isDeadOrDying()) return;
            UUID maidId = findMaidIdByCompanion(body.getUUID());
            if (maidId == null) return;
            BINDINGS.remove(maidId);
            if (BINDINGS.isEmpty()) pendingEmptyBroadcast = true;
            DELETED.add(maidId);
            LittleMaidMoreAction.LOGGER.info("[NumenBridge] 面板删除假人 maid={} companion={} — 不重生 (按钮恢复)",
                    maidId, body.getUUID());
        });
    }

    // ── 生命周期 (每 tick 由 NumenTickHandler 调用) ──

    /** 每 tick: v75.1 变身由按钮触发 — tick 只做兜底清理 + 广播 */
    public static void tick(ServerLevel level) {
        for (var e : level.getAllEntities()) {
            if (!(e instanceof EntityMaid maid)) continue;
            UUID maidId = maid.getUUID();
            if (SHELVING.contains(maidId)) continue;
            if (!maid.isAlive()) {
                stop(maid, level);   // 女仆死亡 → 假人销毁 + 物品爆地
                continue;
            }
            if (!AiControlGate.isEnabled(maid) && BINDINGS.containsKey(maidId)) {
                stop(maid, level);   // gate off (手动切任务) + 假人在 → 销毁 + 物品爆地
            }
        }
        // 广播 (仅女仆实体在时; SHELVED 假人 LLM 环靠 summon 时绑定 + Numen 全局默认)
        broadcast(level);
        // v75.3: SHELVED 假人随机台词 (仿 TLM RandomEmoji — 60~120s 随机颜文字气泡)
        tickRandomChat(level);
    }

    /** v75.3: 假人定时随机台词 (S→C SpeechBubbleSyncPayload — 假人头顶气泡, 仿 TLM RandomEmoji —
     *  直接读 TLM MaidConfig: ENABLE_EMOJI 开关 + EMOJI_CHECK_RATE 节拍 + UUID 偏移) */
    private static void tickRandomChat(ServerLevel level) {
        if (!com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.ENABLE_EMOJI.get()) return;
        // v77.9: LMA 独立台词开关 (任务全局设置屏可调)
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig.COMPANION_CHAT_ENABLED.get()) return;
        // v77.4: LMA 独立节拍配置 (不碰 TLM 全局 EmojiCheckRate; 调测可设 20=1s)
        int checkRate = com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig.COMPANION_CHAT_RATE.get();
        long now = level.getGameTime();
        for (var entry : BINDINGS.entrySet()) {
            if (level.getEntity(entry.getKey()) instanceof EntityMaid) continue;   // 仅 SHELVED (女仆在石板)
            NumenPlayer companion = NumenPlayer.findByUuid(level.getServer(), entry.getValue());
            if (companion == null || !companion.isAlive()) continue;
            UUID ownerId = companion.getOwnerUuid();
            if (ownerId == null) continue;
            long offset = companion.getUUID().getLeastSignificantBits() % checkRate;   // 仿 RandomEmoji UUID 偏移
            if ((now + offset) % checkRate != 0) continue;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null) continue;
            String emoji = RANDOM_CHAT_LINES[companion.getRandom().nextInt(RANDOM_CHAT_LINES.length)];
            // SpeechBubbleSyncPayload (numen-api S→C, KIND_TEXT=2) — 假人头顶气泡
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(owner,
                    new com.dwinovo.numen.network.payload.SpeechBubbleSyncPayload(
                            companion.getUUID(), (byte) 2, emoji));
            // v75.3: 随机语音 — 女仆语音包音频在假人位置播放 (客户端自建播放器, TLM mixin 支持 ICustomSoundBuffer)
            // v77.9: LMA 独立语音开关 (任务全局设置屏可调)
            String[] cfg = MAID_AI_CFG.get(entry.getKey());
            if (com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig.COMPANION_VOICE_ENABLED.get()
                    && cfg != null && cfg.length > 2 && cfg[2] != null && !cfg[2].isEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(owner,
                        new com.github.xiaozhaoz1.littlemaidmoreaction.network.LmaMaidVoicePayload(
                                companion.getUUID(), cfg[2],
                                com.github.tartaricacid.touhoulittlemaid.init.InitSounds.MAID_PLAYER.get().getLocation()));
            }
        }
    }

    /** v75.1: 设置 GUI "变成假人" 按钮入口 — 任务须运行中 (gate on)
     *  v75.3: 前置硬检查 — OpenYSM (开源版) + 女仆当前 YSM 模型, 缺一阻止 */
    public static void transform(EntityMaid maid, ServerLevel level) {
        UUID maidId = maid.getUUID();
        if (!maid.isAlive()) return;
        if (!AiControlGate.isEnabled(maid)) {
            maid.getChatBubbleManager().addTextChatBubble("✘ 请先开启 AI 操控任务");
            return;
        }
        // v77.7: 任何 YSM (OpenYSM 或混淆版) 均可 — 混淆版走命令通道
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.compat.YsmCompat.isInstalled()) {
            maid.getChatBubbleManager().addTextChatBubble("✘ 需要 YSM / OpenYSM — 未安装");
            return;
        }
        String ysmId = maid.getYsmModelId();
        if (ysmId == null || ysmId.isEmpty()) {
            maid.getChatBubbleManager().addTextChatBubble("✘ 女仆当前非 YSM 模型 — 请在 TLM 模型界面选择 YSM 模型");
            return;
        }
        if (DELETED.contains(maidId)) {
            DELETED.remove(maidId);
            MAID_AI_CFG.remove(maidId);   // v75.3: 清理记录 (transform 重开会重新 put)
        }
        start(maid, level);
    }

    private static void broadcast(ServerLevel level) {
        MinecraftServer server = level.getServer();
        long tick = server.getTickCount();
        if (tick != lastBroadcastTick) {
            lastBroadcastTick = tick;
            if (tick % 20 == 0) {
                if (!BINDINGS.isEmpty()) {
                    List<NumenCompanionSyncPayload.Binding> bindings = new ArrayList<>();
                    for (var entry : BINDINGS.entrySet()) {
                        // v75.3: SHELVED (女仆在石板) 也用变身时记录的 provider/voice —
                        // 否则假人 LLM 环无绑定 → 用 Numen 全局默认 (女仆设置丢失)
                        EntityMaid maid = level.getEntity(entry.getKey()) instanceof EntityMaid m ? m : null;
                        String provider = "", voice = "";
                        if (maid != null) {
                            provider = readAiConfig(maid, com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline.KEY_PROVIDER);
                            voice = readAiConfig(maid, com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline.KEY_VOICE);
                        } else {
                            String[] saved = MAID_AI_CFG.get(entry.getKey());
                            if (saved != null) { provider = saved[0]; voice = saved[1]; }
                        }
                        bindings.add(new NumenCompanionSyncPayload.Binding(entry.getValue(), provider, voice));
                    }
                    if (!bindings.isEmpty()) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                                new NumenCompanionSyncPayload(bindings));
                    }
                } else if (pendingEmptyBroadcast) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                            new NumenCompanionSyncPayload(List.of()));
                    pendingEmptyBroadcast = false;
                }
            }
        }
    }

    /** 变身: summon 假人 (幂等) → 自动设模型 → 状态同步 → 女仆收石板 (带全物品) */
    private static void start(EntityMaid maid, ServerLevel level) {
        UUID maidId = maid.getUUID();
        UUID compId = BINDINGS.get(maidId);
        if (compId != null) {
            NumenPlayer existing = NumenPlayer.findByUuid(level.getServer(), compId);
            if (existing != null && existing.isAlive()) {
                shelveMaid(maid, existing, level);   // 假人在 → 直接收起女仆
                return;
            }
            BINDINGS.remove(maidId);   // 假人丢失 → 重新生成
        }
        UUID owner = maid.getOwnerUUID();
        if (owner == null) return;
        try {
            // v75.3: 每女仆独立假人 — 不走 Companions.summon (幂等键 (owner,name) → 同名女仆共享假人 → 混)。
            // 固定 UUID: PD 有 → spawn(stored) 从 .dat 恢复 (面板删除后回归); 无 (首次) → 随机新 UUID 存 PD。
            // CompanionFactory.spawn 不注册 roster (休眠索引) — 面板可见靠 syncRosterToOwner (live 列表)。
            NumenPlayer companion = null;
            String storedUuid = maid.getPersistentData().getString(PD_COMPANION_UUID);
            if (!storedUuid.isEmpty()) {
                UUID fixed;
                try { fixed = UUID.fromString(storedUuid); } catch (IllegalArgumentException ex) { fixed = UUID.randomUUID(); }
                if (NumenPlayer.findByUuid(level.getServer(), fixed) == null) {
                    companion = com.dwinovo.numen.entity.CompanionFactory.spawn(
                            level.getServer(), fixed, maid.getName().getString(),
                            owner, level, maid.position().add(0, 1.0, 0));
                }
            }
            if (companion == null) {
                companion = com.dwinovo.numen.entity.CompanionFactory.spawn(
                        level.getServer(), UUID.randomUUID(), maid.getName().getString(),
                        owner, level, maid.position().add(0, 1.0, 0));
            }
            // v75.3: 假人必须可见 — v74 时代旧 .dat 带 INVISIBILITY effect → isInvisible
            // → Numen 气泡渲染跳过 (SpeechBubbleRenderer L64: body.isInvisible() return)
            companion.setInvisible(false);
            companion.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            maid.getPersistentData().putString(PD_COMPANION_UUID, companion.getUUID().toString());
            // v75.3: 记录 LLM provider/voice (女仆收石板后广播用)
            MAID_AI_CFG.put(maidId, new String[]{
                    readAiConfig(maid, com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline.KEY_PROVIDER),
                    readAiConfig(maid, com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline.KEY_VOICE),
                    maid.getSoundPackId()});   // v75.3: 女仆语音包 id (假人随机语音用)
            companion.setOwnerUuid(owner);
            // 成就摘监听 (假人行为触发 inventory_changed 成就)
            companion.getAdvancements().stopListening();
            // v75: 假人可见 (玩家渲染 + YSM 模型) — 不再隐形
            var ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                com.dwinovo.numen.entity.Companions.syncRosterToOwner(level.getServer(), ownerPlayer);
            }
            // v75.1: 自动设女仆当前模型 (YSM 指令) + 状态同步 (最大血量/血量)
            applyMaidModel(maid, companion, level);
            syncCompanionState(maid, companion);
            BINDINGS.put(maidId, companion.getUUID());
            LittleMaidMoreAction.LOGGER.info("[NumenBridge] 假人生成 maid={} companion={} owner={}",
                    maidId, companion.getUUID(), owner);
            shelveMaid(maid, companion, level);
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.error("[NumenBridge] summon 失败: {}", ex.toString());
        }
    }

    /** v75.3: 女仆 YSM 模型 → 假人 — OpenYSM 直接 API (org.openysm DataAttachment),
     *  零指令零权限全自动。仅 YsmModelId 非空时设; getModelId() (GeckoLib) 不设 (覆盖手动)。
     *  YsmCompat 门控 (yes_steve_model 混淆版无此 API — 需装 OpenYSM 2.6.6+) */
    private static void applyMaidModel(EntityMaid maid, NumenPlayer companion, ServerLevel level) {
        try {
            String modelId = maid.getYsmModelId();   // TLM 女仆 YSM 模型 id (独立 NBT 键, 空=非 YSM 模型)
            if (modelId == null || modelId.isEmpty()) {
                LittleMaidMoreAction.LOGGER.info("[NumenBridge] 女仆 YsmModelId 为空 (isYsmModel={}) modelId={} — 跳过自动设模型",
                        maid.isYsmModel(), maid.getModelId());
                return;
            }
            String texture = maid.getYsmModelTexture();
            if (texture == null || texture.isEmpty()) texture = "-";
            if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.YsmCompat.isOpenYsm()) {
                var cap = companion.getData(org.openysm.capability.YSMDataAttachments.MODEL_INFO);
                if (cap != null) {
                    cap.setModelAndTexture(modelId, texture);
                    cap.setMandatory(true);
                    LittleMaidMoreAction.LOGGER.info("[NumenBridge] 自动设 OpenYSM 模型 companion={} model={} texture={} (直接 API)",
                            companion.getUUID(), modelId, texture);
                }
                return;
            }
            // v77.7-77.8: 混淆版 YSM — YSM 官方命令通道 (YsmCommandChannel, 零 API 依赖)
            YsmCommandChannel.setModel(level, companion, modelId, texture);
            LittleMaidMoreAction.LOGGER.info("[NumenBridge] 自动设 YSM 命令模型 companion={} model={} texture={}",
                    companion.getUUID(), modelId, texture);
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.warn("[NumenBridge] OpenYSM 模型设置异常: {}", ex.toString());
        }
    }

    /** v75.1: 状态同步 — 最大血量/血量 (女仆 → 假人, 一次性) */
    private static void syncCompanionState(EntityMaid maid, NumenPlayer companion) {
        try {
            var attr = companion.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(maid.getMaxHealth());
            }
            companion.setHealth(Math.max(1.0F, Math.min(maid.getHealth(), companion.getMaxHealth())));
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.warn("[NumenBridge] 状态同步失败: {}", ex.toString());
        }
    }

    /** 收起流程: cancel 任务 (idle 存石板, 防放女仆循环) → storeMaidData → discard → 石板进玩家背包 */
    private static void shelveMaid(EntityMaid maid, NumenPlayer companion, ServerLevel level) {
        UUID maidId = maid.getUUID();
        if (!SHELVING.add(maidId)) return;
        try {
            // 1. 任务取消 — 石板 NBT 存 idle (用户硬性要求: 放女仆不能恢复 gate)
            TaskDispatcher.cancel(maid);
            // 2. 造石板 + 存女仆 (v75.2 实证: 必须用 HAS_MAID 物品 — TLM SlabClickEvent 同款;
            //    EMPTY 物品写数据 → TLM 判定为空石板)
            ItemStack slab = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
            AbstractStoreMaidItem.storeMaidData(slab, maid);
            // 3. LMA 交接键: companion UUID
            var data = slab.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                    .copyTag();
            data.putString(SLAB_COMPANION_KEY, companion.getUUID().toString());
            slab.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
            // 4. 女仆实体移除
            maid.discard();
            // 5. 石板进玩家背包 (失败 → 掉脚下不丢数据)
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(maid.getOwnerUUID());
            if (owner != null) {
                if (!owner.addItem(slab)) {
                    owner.drop(slab, false);
                    owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[LMA] 背包已满 — 石板掉落脚下 (含女仆数据)"));
                }
            } else {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                        companion.getX(), companion.getY(), companion.getZ(), slab));
            }
            LittleMaidMoreAction.LOGGER.info("[NumenBridge] 女仆收石板 maid={} companion={} (idle)",
                    maidId, companion.getUUID());
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.error("[NumenBridge] 收起失败: {} — 女仆保留, 假人照跑", ex.toString());
        } finally {
            SHELVING.remove(maidId);
        }
    }

    /** 关闭: 销毁假人 + 物品爆一地 (v75.1 无物品转移 — 女仆石板自带背包) */
    public static void stop(EntityMaid maid, ServerLevel level) {
        UUID maidId = maid.getUUID();
        UUID compId = BINDINGS.remove(maidId);
        if (BINDINGS.isEmpty()) pendingEmptyBroadcast = true;
        DELETED.remove(maidId);
        MAID_AI_CFG.remove(maidId);   // v75.3: 清理记录
        if (compId == null) return;
        NumenPlayer companion = NumenPlayer.findByUuid(level.getServer(), compId);
        if (companion != null) {
            companion.getInventory().dropAll();   // 物品爆一地
            // v75.3: dismiss (despawn + roster 删) — despawn 只休眠, roster 残留 → Numen 登录复活 (用户实证: 重进游戏还有假人)
            com.dwinovo.numen.entity.Companions.dismiss(level.getServer(), companion);
        }
        LittleMaidMoreAction.LOGGER.info("[NumenBridge] 假人销毁 maid={} companion={} (物品爆地, roster 清理)", maidId, compId);
    }

    /**
     * v79.2: 重启/重连恢复 — 追踪魂符 (玩家背包石板 lma_companion 键) → 对应假人若在线 → 传送玩家旁。
     * 位置来源链根因 (javap 实证): Numen respawnAllOwnedBy → Companions.respawn →
     * CompanionFactory.spawn(pos=null) → 位置 = .dat 旧位置 (仅 pos!=null 才 moveTo 覆盖)。
     * 对齐 = 玩家脚格视线前方 2 格 (yaw 方向向量, 防压玩家); 仅登录一次, 不影响登录后自主游走。
     */
    public static void alignRestoredCompanions(ServerPlayer player) {
        MinecraftServer server = player.server;
        for (UUID compId : collectSlabCompanionUuids(player)) {
            NumenPlayer companion = NumenPlayer.findByUuid(server, compId);
            if (companion == null || !companion.isAlive()) continue;
            if (!companion.isOwnedByPlayer(player.getUUID())) continue;
            double rad = Math.toRadians(player.getYRot());
            net.minecraft.core.BlockPos p = player.blockPosition();
            companion.teleportTo(p.getX() + 0.5 - Math.sin(rad) * 2.0,
                    p.getY() + 1.0, p.getZ() + 0.5 + Math.cos(rad) * 2.0);
            LittleMaidMoreAction.LOGGER.info("[NumenBridge] 重启对齐 companion={} → 玩家旁 ({},{},{})",
                    compId, p.getX(), p.getY(), p.getZ());
        }
    }

    /** 玩家背包中带 lma_companion 键的石板 → companion UUID 集合 (魂符追踪, v79.2) */
    private static java.util.Set<UUID> collectSlabCompanionUuids(ServerPlayer player) {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            String v = data.copyTag().getString(SLAB_COMPANION_KEY);
            if (v.isEmpty()) continue;
            try {
                ids.add(UUID.fromString(v));
            } catch (IllegalArgumentException ignored) {
                // 非 UUID 残留 — 忽略
            }
        }
        return ids;
    }

    /** 女仆卸载 (onEntityLeaveLevel 兜底; 石板收起由 shelveMaid 处理, 此处幂等) */
    public static void onMaidUnload(EntityMaid maid, ServerLevel level) {
        UUID maidId = maid.getUUID();
        if (!BINDINGS.containsKey(maidId)) return;   // 已 SHELVED (石板收起) → 假人托管不销毁
        stop(maid, level);
    }

    /** 石板放出交接: 新女仆实体 join → 匹配 SHELVED 绑定 → 假人销毁 + 物品爆地 + 绑定清理 */
    public static void onMaidRestored(EntityMaid maid, ServerLevel level) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) return;
        UUID maidId = maid.getUUID();
        if (BINDINGS.containsKey(maidId)) return;   // 已在绑定 (非石板放出)

        // ── v79.11: PD lma_companion_uuid 直查优先 (精确对应刚放出的石板 — 防多魂符误伤) ──
        // 石板 NBT 经 storeMaidData 往返: 女仆 PD 带固定 companion UUID → 精确对应刚放出的石板。
        // v79.9 用户实测: BINDINGS 遍历经背包石板键匹配, 放 A 魂符时背包有 B 石板键 → 误销毁 B 假人。
        String stored = maid.getPersistentData().getString(PD_COMPANION_UUID);
        if (!stored.isEmpty()) {
            UUID compId;
            try {
                compId = UUID.fromString(stored);
            } catch (IllegalArgumentException ex) {
                return;
            }
            NumenPlayer companion = NumenPlayer.findByUuid(level.getServer(), compId);
            if (companion != null && companion.isAlive() && companion.isOwnedByPlayer(ownerId)) {
                companion.getInventory().dropAll();
                com.dwinovo.numen.entity.Companions.dismiss(level.getServer(), companion);
                hasSlabWithCompanion(level, ownerId, compId);   // 清石板残留键 (复用副作用)
                LittleMaidMoreAction.LOGGER.info("[NumenBridge] 石板放出交接 maid={} (new) companion={} — 假人销毁 (PD 键直查)",
                        maidId, compId);
            }
            return;   // PD 键存在即精确处理 — 不再走 BINDINGS 遍历 (防误伤)
        }

        // ── fallback: 无 PD 键 (旧石板/旧版本) → BINDINGS 遍历匹配 ──
        for (var entry : BINDINGS.entrySet()) {
            UUID oldMaidId = entry.getKey();
            if (level.getEntity(oldMaidId) instanceof EntityMaid) continue;   // 旧女仆还在
            NumenPlayer companion = NumenPlayer.findByUuid(level.getServer(), entry.getValue());
            if (companion == null || !companion.isAlive()) continue;
            if (!companion.isOwnedByPlayer(ownerId)) continue;   // 同主人
            // 玩家背包有对应石板 (lma_companion 匹配) → 确认是该石板放出
            if (!hasSlabWithCompanion(level, ownerId, companion.getUUID())) continue;
            // 交接: 假人销毁 + 物品爆一地 (新女仆从石板自带全物品) — v75.3 dismiss 清理 roster 防登录复活
            companion.getInventory().dropAll();
            BINDINGS.remove(oldMaidId);
            if (BINDINGS.isEmpty()) pendingEmptyBroadcast = true;
            com.dwinovo.numen.entity.Companions.dismiss(level.getServer(), companion);
            LittleMaidMoreAction.LOGGER.info("[NumenBridge] 石板放出交接 maid={} (new) companion={} — 假人销毁 (roster 清理)",
                    maidId, companion.getUUID());
            return;
        }
    }

    /** 玩家背包是否有带 lma_companion 键的石板 (交接识别) */
    private static boolean hasSlabWithCompanion(ServerLevel level, UUID ownerId, UUID companionUuid) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            ItemStack stack = owner.getInventory().getItem(i);
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && companionUuid.toString().equals(data.copyTag().getString(SLAB_COMPANION_KEY))) {
                // 石板已用 (放出了女仆) → 清除交接键
                var tag = data.copyTag();
                tag.remove(SLAB_COMPANION_KEY);
                stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                return true;
            }
        }
        return false;
    }

    // ── 查询与外部调用 ──

    /** 查找女仆对应假人 */
    public static NumenPlayer findCompanion(ServerLevel level, EntityMaid maid) {
        UUID compId = BINDINGS.get(maid.getUUID());
        return compId == null ? null : NumenPlayer.findByUuid(level.getServer(), compId);
    }

    /** 绑定表中的假人 UUID (事件监听: 过滤假人身份) */
    public static boolean isBoundCompanion(UUID uuid) {
        return BINDINGS.containsValue(uuid);
    }

    /** 假人对应女仆 (v75: 女仆可能在石板 — 返回 null 表示 SHELVED) */
    public static EntityMaid findMaidByCompanion(ServerLevel level, UUID companionUuid) {
        UUID maidId = findMaidIdByCompanion(companionUuid);
        if (maidId == null) return null;
        var e = level.getEntity(maidId);
        return e instanceof EntityMaid maid ? maid : null;
    }

    /** maidId 逆查 (BINDINGS value → key) */
    private static UUID findMaidIdByCompanion(UUID companionUuid) {
        for (var entry : BINDINGS.entrySet()) {
            if (entry.getValue().equals(companionUuid)) return entry.getKey();
        }
        return null;
    }
}
