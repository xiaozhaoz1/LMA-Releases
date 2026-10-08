package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 女仆「偷吃 Token」— 心跳行为 (v79.66o, 用户裁定 2026-09-20 ✓)。
 *
 * <p><b>行为规格 (用户逐条裁定)</b>:
 * <ol>
 *   <li>每 **6000t** 掷一次 **5%** 概率; <b>计时从"上一次概率判定"起算</b> ⇒ cd 一转完立即判定 ✓
 *       ({@link TokenStealMath#cooldownElapsed})</li>
 *   <li>主人必须在 **8 格内** 才偷 ✓</li>
 *   <li>把主人身上**那一整组 Token 直接搬进女仆背包**; **不满一组也有多少偷多少** ✓</li>
 *   <li>女仆背包**放不下就不偷** (先模拟校验, 原子 ⇒ 绝不"偷一半掉地上" ✗) ✓</li>
 *   <li>**偷到就开始吃**: 立刻吃 1 个 (走 TLM 原生 {@code maid.eat} ⇒ 音效/食物属性同源 ✓) + 效果 ✓</li>
 *   <li>气泡 = 「token真好吃」✓; 只偷 Token, 不碰主人其它物品 ✓; 女仆之间不互偷 (只针对主人) ✓</li>
 * </ol>
 *
 * <p><b>状态</b>: 上次判定 tick 存 per-maid **PD** (`MaidData.pl`, 运行时瞬态 ✓ 不需要落盘 —
 * 女仆卸载/世界重载后重新起算可接受, 6 分钟量级的行为 ✓)。
 *
 * <p><b>调用点</b>: {@code task/runtime/TaskTickHandler} 每女仆心跳 (便宜门控优先: 配置 → 冷却) ✓。
 */
public final class TokenStealService {

    private TokenStealService() {}

    /** per-maid 运行时状态类型键 (MaidData.pl) */
    private static final String PD_TYPE = "token_steal";
    /** 上次**判定** tick (用户裁定: 冷却从判定起算 ✓) */
    private static final String KEY_LAST_ATTEMPT = "lastAttemptTick";

    /** 偷取距离 (格) — 用户裁定 8 格内 ✓ */
    public static final double RANGE_BLOCKS = 8.0;

    /**
     * 心跳入口 (每女仆每 tick, 由 {@code TaskTickHandler} 调)。
     * <p>门控顺序刻意**从最便宜到最贵**: 配置 → 冷却 → 距离/物品/背包 (都在 {@link #attempt}) ✓
     */
    public static void tick(ServerLevel level, EntityMaid maid, long nowTick) {
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.TOKEN_STEAL_ENABLED.get()) {
            return;
        }
        var pd = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, PD_TYPE);
        long last = pd.contains(KEY_LAST_ATTEMPT) ? pd.getLong(KEY_LAST_ATTEMPT) : Long.MIN_VALUE;
        int interval = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig
                .TOKEN_STEAL_INTERVAL.get();
        if (!TokenStealMath.cooldownElapsed(last, nowTick, interval)) return;
        // ★ 用户裁定: **每次判定后**重新计时 (不是"偷到了才计时") ⇒ cd 转完立即判定 ✓
        pd.putLong(KEY_LAST_ATTEMPT, nowTick);
        float chance = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig
                .TOKEN_STEAL_CHANCE.get().floatValue();
        if (!TokenStealMath.rollHit(chance, maid.getRandom().nextFloat())) return;
        attempt(level, maid, maid.getOwner() instanceof net.minecraft.world.entity.player.Player p ? p : null);
        // 注意: 判定失败(无主人/没 token/背包满/超距)不额外惩罚 — 下次 cd 再试 ✓ (用户裁定口径)
    }

    /**
     * 调试命令路径: 跳过**冷却与概率** (立即偷一次), 但**仍遵守** 8 格 / 背包空间 / 物品条件 ✓
     * —— 这样命令测的就是真实路径的条件分支 ✓
     *
     * @return 是否真的偷到了
     */
    public static boolean forceAttempt(ServerLevel level, EntityMaid maid) {
        return attempt(level, maid,
                maid.getOwner() instanceof net.minecraft.world.entity.player.Player p ? p : null);
    }

    /**
     * **显式主人**重载 — 调试命令/测试用 ✓。
     *
     * <p>⚠ 为什么需要: TLM {@code EntityMaid#getOwner()} 走
     * {@code server.getPlayerList().getPlayer(uuid)} **反查** ⇒ gametest 的
     * {@code helper.makeMockPlayer(...)} **不在玩家列表里** ⇒ `getOwner()` 恒 `null` ✗
     * (实测 "夹具异常: 主人 token=0" ⇒ mock 与它反查到的主人不是同一对象) ⇒ 本重载注入 ✓。
     * 生产路径仍走 {@link #tick} → {@code maid.getOwner()} ✓ (行为零变化 ✓)。
     */
    public static boolean forceAttempt(ServerLevel level, EntityMaid maid,
                                       net.minecraft.world.entity.player.Player ownerOverride) {
        return attempt(level, maid, ownerOverride);
    }

    /** 真正的偷取 (含全部条件分支)。@return 是否偷到 */
    private static boolean attempt(ServerLevel level, EntityMaid maid,
                                   net.minecraft.world.entity.player.Player owner) {
        // ① 主人必须是同维度玩家 (女仆跟随主人 ⇒ 正常同维度 ✓)
        if (owner == null) return false;
        var sp = owner;
        if (sp.level() != level) return false;
        // ② 用户裁定: 8 格内 ✓ (平方距离, 免开方)
        if (!TokenStealMath.withinRange(maid.distanceToSqr(sp), RANGE_BLOCKS)) return false;
        // ③ 找主人身上第一组 Token (整组; 背包 items+armor+offhand 全覆盖 ✓)
        var pinv = sp.getInventory();
        int src = -1;
        for (int i = 0; i < pinv.getContainerSize(); i++) {
            ItemStack s = pinv.getItem(i);
            if (!s.isEmpty() && isToken(s)) {
                src = i;
                break;
            }
        }
        if (src < 0) return false;
        ItemStack stolenSrc = pinv.getItem(src);
        int amount = stolenSrc.getCount();          // ★ 不满一组也有多少偷多少 ✓
        if (amount <= 0) return false;
        ItemStack stolen = stolenSrc.copy();        // 复制一份用于搬运 (原槽稍后清空)

        // ④ 女仆背包空间**模拟**校验 (原子: 放不下 ⇒ 不偷 ✓ 用户裁定)
        var minv = maid.getAvailableInv(false);
        int slots = minv.getSlots();
        int[] planSlot = new int[slots];
        int[] planCount = new int[slots];
        int planned = 0;
        int remaining = amount;
        for (int i = 0; i < slots && remaining > 0; i++) {
            ItemStack probe = stolen.copyWithCount(remaining);
            ItemStack rejected = minv.insertItem(i, probe, true);   // simulate=true ⇒ 不改动 ✓
            int fit = remaining - rejected.getCount();
            if (fit > 0) {
                planSlot[planned] = i;
                planCount[planned] = fit;
                planned++;
                remaining -= fit;
            }
        }
        if (remaining > 0) return false;            // 放不下 ⇒ 一点不拿 ✓

        // ⑤ 执行搬运 (先取后放; 极端兜底: 任何放不进去的残余 ⇒ 还给主人, 绝不吞物品 ✗)
        pinv.setItem(src, ItemStack.EMPTY);
        int moved = 0;
        for (int k = 0; k < planned; k++) {
            int slot = planSlot[k];
            int cnt = planCount[k];
            ItemStack put = stolen.copyWithCount(cnt);
            ItemStack rejected = minv.insertItem(slot, put, false);
            moved += cnt - rejected.getCount();
            if (!rejected.isEmpty()) {
                pinv.placeItemBackInInventory(rejected);   // 兜底: 归还 (理论上不会走到 ✓)
            }
        }
        if (moved <= 0) {
            pinv.setItem(src, stolen);              // 完全没搬动 ⇒ 原样退回, 保持原子 ✓
            return false;
        }

        // ⑥ 偷到就开始吃 (用户裁定 ✓): 吃 1 个 + 效果 (与三条食用路径同源 ✓)
        eatOne(level, maid);

        // ⑦ 气泡「token真好吃」✓ + 日志 (外部行为禁止全静默 — #358 教训 ✓)
        com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi.showInfo(
                maid, Component.translatable("bubble.littlemaidmoreaction.token_steal"));
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                "[Token] 偷吃成功: 主人={} 偷走={} 个 (源槽={}) 女仆={} 距离={}",
                sp.getName().getString(), moved, src, maid.getStringUUID().substring(0, 8),
                String.format("%.2f", Math.sqrt(maid.distanceToSqr(sp))));
        return true;
    }

    /**
     * 吃 1 个 (从女仆背包里找 Token) — 与 {@code TokenItem.feedOnce} **同源**:
     * {@code maid.eat(level, stack)} (TLM 原生: 音效/食物属性/事件 ✓) + 未消耗则手动扣 1 +
     * {@link TokenItem#applyEffects} (回血/缓慢恢复/饱食度) ✓。
     * <p>⚠ 这里**不做** feedOnce 的"撤销坐姿"部分 — 那是给"玩家蹲下右键喂"用的 (本次没有交互 ⇒ 不会坐下 ✓)。
     */
    private static void eatOne(ServerLevel level, EntityMaid maid) {
        var inv = maid.getAvailableInv(true);       // handsFirst=true: 手上有就先吃手上的 ✓
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !isToken(s)) continue;
            int before = s.getCount();
            maid.eat(level, s);
            if (s.getCount() == before) {
                s.shrink(1);                        // eat 未消耗 (非玩家实体) ⇒ 手动扣 1 ✓
            }
            TokenItem.applyEffects(maid);
            return;
        }
    }

    /** 是否 Token (与 LmaItems.TOKEN 一致 — fact-forcing: 物品比对而非 id 猜测 ✓) */
    private static boolean isToken(ItemStack stack) {
        return stack.is(com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems.TOKEN.get());
    }

    // ── 调试辅助 (给 /lma token 用 — 键名只在本类出现一次, 防漂移 ✓) ──

    /** 距下次概率判定还需多少 tick (0 = 本 tick 就会判; 从未判定过 = 0 ✓) */
    public static long ticksUntilNextAttempt(EntityMaid maid, long nowTick) {
        var pd = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData.pl(maid, PD_TYPE);
        if (!pd.contains(KEY_LAST_ATTEMPT)) return 0L;
        long last = pd.getLong(KEY_LAST_ATTEMPT);
        int interval = com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig
                .TOKEN_STEAL_INTERVAL.get();
        return Math.max(0L, interval - (nowTick - last));
    }

    /** 女仆身上 Token 总数 (背包 + 双手 — 与搬运/食用同一视图 ✓) */
    public static int countTokens(EntityMaid maid) {
        int total = 0;
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && isToken(s)) total += s.getCount();
        }
        return total;
    }

    /** 指定玩家的 Token 总数 (调试/测试注入用 ✓) */
    public static int countTokens(net.minecraft.world.entity.player.Player sp) {
        if (sp == null) return 0;
        int total = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && isToken(s)) total += s.getCount();
        }
        return total;
    }

    /** 主人身上 Token 总数 (调试用: 离线/无主人 = 0 ✓ — 生产走 `maid.getOwner()` 反查 ✓) */
    public static int countOwnerTokens(EntityMaid maid) {
        return countTokens(maid.getOwner() instanceof net.minecraft.world.entity.player.Player sp ? sp : null);
    }
}
