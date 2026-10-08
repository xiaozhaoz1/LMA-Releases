package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk;

import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;

/**
 * 酒狐奶饰品注册 (v79.6x) — 把两个奶物品绑定到 IMaidBauble。
 * 挂载: {@code LittleMaidMoreActionExtension.bindMaidBauble(BaubleManager)}。
 */
public final class KitsuneMilkBaubleRegistry {

    private KitsuneMilkBaubleRegistry() {}

    public static void bind(BaubleManager manager) {
//? if 1.20.1 {
        manager.bind(KitsuneMilkItems.TAMED_MILK_BUCKET, new TamedMilkBauble());
        manager.bind(KitsuneMilkItems.WILD_DOGMILK, new WildMilkBauble());
//?} else {
        manager.bind(KitsuneMilkItems.TAMED_MILK_BUCKET.get(), new TamedMilkBauble());
        manager.bind(KitsuneMilkItems.WILD_DOGMILK.get(), new WildMilkBauble());
//?}
    }
}
