package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Set;

/**
 * v79.62 探险家地图被动 — 女仆背包/手里有探险家地图 (海底神殿/林地府邸) 时,
 * 读出宝藏具体坐标并气泡提醒一次 (同地图去重).
 *
 * <p>触发: 无信号, 纯 tick 检测 (背包扫描). isLongRunning=true (心跳豁免看门狗).
 * 防御点: 无地图/无装饰/读坐标失败均静默; 同地图只报一次 (PD 存地图 NBT hash).
 *
 * <p><b>驱动 (v79.63 修复)</b>: {@code Drive.GMPM_PASSIVE} (纯信息气泡 — 用户裁定**不受坐下限制**,
 * 与 torch_light 那类"坐下暂停"的动作型被动相反)。此前本任务未进任何驱动集合 = **生产从不 tick**
 * (测试直调掩盖, 2026-09-11 架构评审 P0-1)。同理补 {@link #SCAN_INTERVAL} 前置节流 —
 * 原实现每 tick 扫全背包.
 */
public final class ExplorerMapPipeline implements TaskPipeline, TaskConfigurable {

    private static final String KEY_ANNOUNCED = "explorer_announced";
    /** 背包扫描节流 (tick, 5 秒) — 地图是持久物品, 迟早会发现; 无需逐 tick 扫 (v79.63) */
    private static final long SCAN_INTERVAL = 100L;
    private static final String THROTTLE_KEY = "explorer_map_scan";

    @Override public String taskType() { return "explorer_map"; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        // v79.63 前置节流 — 原每 tick 全背包扫描 (被动须自持节流)
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.ThrottleUtil
                .shouldFire(maid, THROTTLE_KEY, SCAN_INTERVAL)) {
            return;
        }
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !s.is(Items.FILLED_MAP)) continue;
            // 同地图已报过 → 跳过
            String hash = mapKey(s);
            CompoundTag pd = pipelineData(maid);
            String announced = pd.getString(KEY_ANNOUNCED);
            if (hash.equals(announced)) continue;
            // 读宝藏坐标
            BlockPos pos = readTreasurePos(world, s);
            if (pos == null) {
                // v79.63.5 (用户裁定): 无宝藏标记 (空白图/玩家自制图) ⇒ 给一句提示, 不再静默 ✗
                //   同样计入 announced (同图只提示一次, 与"同地图只报一次"同语义) ✓
                MaidChatBubbleApi.showInfo(maid, net.minecraft.network.chat.Component.translatable(
                        "bubble.littlemaidmoreaction.no_treasure"));
                pd.putString(KEY_ANNOUNCED, hash);
                return;
            }
            MaidChatBubbleApi.showInfo(maid, net.minecraft.network.chat.Component.translatable(
                    "bubble.littlemaidmoreaction.treasure_pos", pos.getX(), pos.getZ()));
            pd.putString(KEY_ANNOUNCED, hash);
            return;
        }
    }

    /** 地图去重键 — 地图物品数据序列化 (同 map id 同一份地图) */
    private static String mapKey(ItemStack s) {
//? if 1.20.1 {
        CompoundTag tag = s.getTag();
        return tag == null ? s.hashCode() + "" : tag.toString();
//?} else {
        return s.getComponentsPatch().toString();
//?}
    }

    /**
     * v79.62.5 读藏宝图目标坐标 (用户实测: 沉船图没反应 — 原读 MapItemSavedData 装饰, 但藏宝图目标
     * 存在 ITEM NBT (1.20.1 addTargetDecoration) / MAP_DECORATIONS 组件 (1.21.1), 存的是世界坐标 x/z,
     * 且地图未展开时 saved data 无该装饰). 兼容所有 vanilla 藏宝图类型: RED_X(沉船/埋藏宝藏) /
     * MANSION(林地府邸) / MONUMENT(海底神殿) — 玩家标记(PLAYER/BANNER/FRAME)不会用这些类型, 不误报.
     */
    @javax.annotation.Nullable
    private static BlockPos readTreasurePos(ServerLevel level, ItemStack map) {
//? if 1.20.1 {
        // 1.20.1: item NBT Decorations ListTag — addTargetDecoration 写: type(byte) / id / x(double,世界) / z(double,世界)
        CompoundTag tag = map.getTag();
        if (tag != null && tag.contains("Decorations", 9)) {
            net.minecraft.nbt.ListTag list = tag.getList("Decorations", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag d = list.getCompound(i);
                MapDecoration.Type t = MapDecoration.Type.byIcon(d.getByte("type"));
                if (t == MapDecoration.Type.RED_X
                        || t == MapDecoration.Type.MANSION
                        || t == MapDecoration.Type.MONUMENT) {
                    return new BlockPos((int) d.getDouble("x"), 0, (int) d.getDouble("z"));
                }
            }
        }
        return null;
//?} else {
        // 1.21.1: DataComponents.MAP_DECORATIONS — Entry(type Holder, x 世界, z 世界, rot)
        var decos = map.get(net.minecraft.core.component.DataComponents.MAP_DECORATIONS);
        if (decos != null) {
            for (var entry : decos.decorations().values()) {
                var t = entry.type().value();
                if (t == net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X.value()
                        || t == net.minecraft.world.level.saveddata.maps.MapDecorationTypes.WOODLAND_MANSION.value()
                        || t == net.minecraft.world.level.saveddata.maps.MapDecorationTypes.OCEAN_MONUMENT.value()) {
                    return new BlockPos((int) entry.x(), 0, (int) entry.z());
                }
            }
        }
        return null;
//?}
    }

    @Override
    public void onCleanup(EntityMaid maid) {
        clearPipelineData(maid);
    }
}

