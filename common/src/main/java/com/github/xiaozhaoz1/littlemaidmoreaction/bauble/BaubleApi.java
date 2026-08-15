package com.github.xiaozhaoz1.littlemaidmoreaction.bauble;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 饰品 API 基座 (v79.6x) — 本包只放饰品通用 API, 不实现具体饰品业务。
 *
 * <p>提供跨饰品复用的通用能力: 服务端时间戳状态 (无敌期/CD/冷却) 的 PersistentData 读写。
 * 具体饰品 (如酒狐奶) 的业务请放 {@code bauble/WildKitsuneMilk/} 子包 — API 与实现分离。
 *
 * <p><b>时间戳规则 (PersistentData 铁律)</b>: 键必须闭环 (set → remove);
 * 判定用 {@code stored > now || stored == 0} 防溢出/跨 session 残留。
 */
public final class BaubleApi {

    private static final String PREFIX = "lma_bauble_";

    private BaubleApi() {}

    /** 读到期时间戳 (tick); 缺键返回 0 */
    public static long readUntil(EntityMaid maid, String key) {
        return maid.getPersistentData().getLong(PREFIX + key);
    }

    /** 写到期时间戳 (tick) */
    public static void writeUntil(EntityMaid maid, String key, long untilTick) {
        maid.getPersistentData().putLong(PREFIX + key, untilTick);
    }

    /** 清到期时间戳 (键闭环) */
    public static void clearUntil(EntityMaid maid, String key) {
        maid.getPersistentData().remove(PREFIX + key);
    }

    /** CD 是否生效中 — stored 未到 (含缺键 0 = 未触发过, 视为不在 CD) */
    public static boolean onCooldown(EntityMaid maid, String key, long now) {
        long stored = readUntil(maid, key);
        return stored > now;
    }
}
