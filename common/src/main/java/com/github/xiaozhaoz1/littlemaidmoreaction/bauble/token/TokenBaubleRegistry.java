package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token;

import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaItems;

/**
 * Token 饰品的**绑定入口** (v79.72) — 由 {@code api/LittleMaidMoreActionExtension#bindMaidBauble}
 * 在 TLM 构造期调用 ✓ (与 `WildKitsuneMilk/KitsuneMilkBaubleRegistry` 同一接线方式 ✓)。
 *
 * <p>⚠ 双平台 getter 形态不同 (forge = `RegistryObject` / neo = `Supplier`) ⇒ **双分支都要写** ✓
 * (见 `init/README.md` 陷阱 B)。
 */
public final class TokenBaubleRegistry {

    private TokenBaubleRegistry() {}

    /** 把 Token 物品挂进"女仆饰品槽" ✓ */
    public static void bind(BaubleManager manager) {
//? if 1.20.1 {
        // forge: bind 收 Supplier/ItemLike ⇒ 直接传 RegistryObject ✓ (与 KitsuneMilkBaubleRegistry 同款 ✓)
        manager.bind(LmaItems.TOKEN, new TokenBauble());
//?} else {
        // neoforge: bind 收已解析的 Item ✗ ⇒ 必须 .get() ✓ (1.21 编译期实证: 不 .get() 报 "对于 bind(Supplier…)")
        manager.bind(LmaItems.TOKEN.get(), new TokenBauble());
//?}
        // ★ 启动标记 (用户排障用 ✓): 一眼看出游戏里跑的是哪一版 Token 代码 ✓
        //   (教训: 用户日志 18:21 < jar 装入 18:25 ⇒ 测了旧包而双方都不知道 ✗)
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                "[LMA] Token 构建标记 = {} (若与交付版本不符 ⇒ 说明游戏未重启/装了旧包 ✗)", TokenItem.BUILD_TAG);
    }
}
