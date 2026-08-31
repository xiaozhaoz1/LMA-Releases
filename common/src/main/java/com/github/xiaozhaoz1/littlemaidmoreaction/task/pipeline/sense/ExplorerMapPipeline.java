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
 */
public final class ExplorerMapPipeline implements TaskPipeline, TaskConfigurable {

    private static final String KEY_ANNOUNCED = "explorer_announced";

    @Override public String taskType() { return "explorer_map"; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return PipelineResult.ok("");
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
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
            if (pos == null) continue;
            MaidChatBubbleApi.showInfo(maid, "宝藏坐标: " + pos.getX() + ", " + pos.getZ());
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

    /** 读探险家结构坐标 — 遍历地图装饰, 找 MANSION/MONUMENT (双版本枚举差异 stonecutter) */
    @javax.annotation.Nullable
    private static BlockPos readTreasurePos(ServerLevel level, ItemStack map) {
        MapItemSavedData data = net.minecraft.world.item.MapItem.getSavedData(map, level);
        if (data == null) return null;
        for (MapDecoration deco : data.getDecorations()) {
            boolean isExplorer = false;
//? if 1.20.1 {
            isExplorer = deco.getType() == MapDecoration.Type.MANSION
                    || deco.getType() == MapDecoration.Type.MONUMENT;
//?} else {
            var t = deco.type().value();
            isExplorer = t == net.minecraft.world.level.saveddata.maps.MapDecorationTypes.WOODLAND_MANSION.value()
                    || t == net.minecraft.world.level.saveddata.maps.MapDecorationTypes.OCEAN_MONUMENT.value();
//?}
            if (isExplorer) {
                // v79.62.1 修复坐标换算: 原版 addDecoration 世界→装饰 = (world-center)/(1<<scale) * 2
                // 反向: world = center + (deco / 2.0) * (1 << scale)  (探险家地图 scale=2 → 1<<2=4)
                int scaleFactor = 1 << data.scale;
//? if 1.20.1 {
                int x = data.centerX + (int) (deco.getX() / 2.0 * scaleFactor);
                int z = data.centerZ + (int) (deco.getY() / 2.0 * scaleFactor);
//?} else {
                int x = data.centerX + (int) (deco.x() / 2.0 * scaleFactor);
                int z = data.centerZ + (int) (deco.y() / 2.0 * scaleFactor);
//?}
                return new BlockPos(x, 0, z);
            }
        }
        return null;
    }

    @Override
    public void onCleanup(EntityMaid maid) {
        clearPipelineData(maid);
    }
}

