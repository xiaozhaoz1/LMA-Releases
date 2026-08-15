package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * v67.3: 任务黑白名单统一判定 — 全局 (Cloth Config) + per-maid (pipelineConfig) 名单。
 *
 * <p>语义:
 * <ul>
 *   <li>黑名单命中 → 拒绝 (优先级最高)</li>
 *   <li>白名单非空且未命中 → 拒绝 (白名单=允许集合)</li>
 *   <li>两者皆空 → 全部放行</li>
 * </ul>
 * 匹配: 精确物品/方块 id 或 {@code modid:*} 通配 (整 mod)。
 * 生效规则: per-maid 名单非空覆盖全局名单, 空则用全局。
 * 签名用 {@code List<? extends String>} 兼容 ForgeConfigSpec 的 ConfigValue&lt;List&lt;? extends String&gt;&gt;。
 */
public final class ItemFilters {

    /** pipelineConfig 黑名单键 */
    public static final String KEY_BLACKLIST = "blacklist";
    /** pipelineConfig 白名单键 */
    public static final String KEY_WHITELIST = "whitelist";

    private ItemFilters() {}

    /** 生效名单: per-maid 非空覆盖全局 */
    public static List<String> effective(List<? extends String> maidList, List<? extends String> globalList) {
        List<? extends String> src = maidList != null && !maidList.isEmpty() ? maidList : globalList;
        return src == null ? List.of() : List.copyOf(src);
    }

    /** 黑白名单判定 (物品) */
    public static boolean isAllowed(ItemStack stack, List<? extends String> blacklist, List<? extends String> whitelist) {
        if (stack == null || stack.isEmpty()) return false;
        return isAllowed(stack.getItem(), blacklist, whitelist);
    }

    /**
     * 黑白名单判定 (物品对象) — 注册表取完整 id。
     *
     * <p>禁止用 {@code Item.toString()} 匹配 (错题 #191): 1.20.1 只返回 path
     * ({@code getKey().getPath()}), 1.21.1 才返回完整注册名 ({@code getRegisteredName()}),
     * 跨平台语义不一致, 名单匹配必须走注册表。
     */
    public static boolean isAllowed(Item item, List<? extends String> blacklist, List<? extends String> whitelist) {
        if (item == null) return false;
//? if 1.20.1 {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
//?} else {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
//?}
        if (key == null) return false;
        return isAllowed(key.toString(), blacklist, whitelist);
    }

    /** 黑白名单判定 (方块) */
    public static boolean isAllowed(BlockState state, List<? extends String> blacklist, List<? extends String> whitelist) {
        if (state == null || state.isAir()) return false;
//? if 1.20.1 {
        return isAllowed(ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString(), blacklist, whitelist);
//?} else {
        return isAllowed(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), blacklist, whitelist);
//?}
    }

    /** 黑白名单判定 (id 字符串) — 黑名单命中拒绝; 白名单非空且未命中拒绝 */
    public static boolean isAllowed(String id, List<? extends String> blacklist, List<? extends String> whitelist) {
        if (isMatch(id, blacklist)) return false;
        if (whitelist != null && !whitelist.isEmpty() && !isMatch(id, whitelist)) return false;
        return true;
    }

    /** 匹配: 精确 id 或 modid:* 通配 */
    private static boolean isMatch(String id, List<? extends String> list) {
        if (list == null) return false;
        for (String entry : list) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (e.endsWith(":*")) {
                String prefix = e.substring(0, e.length() - 1); // "modid:"
                if (id.startsWith(prefix)) return true;
            } else if (e.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** per-maid 名单读取 (pipelineConfig): NBT ListTag → List&lt;String&gt; */
    public static List<String> maidList(CompoundTag cfg, String key) {
        ListTag list = cfg.getList(key, Tag.TAG_STRING);
        if (list.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }
}
