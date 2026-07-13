package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;
import java.util.Map;

/**
 * 女仆唱片机互动 (v15 简化 — 自动弹出入碟)。
 *
 * <p>参数: music_name(可选,唱片名关键词)
 * <p>流程: 有唱片→弹出/无唱片→从背包插入
 */
@RuleAction
public class JukeboxInteractAction extends AbstractFunctionalBlockInteraction {

    @Override public String id() { return "jukebox_interact"; }
    @Override public String displayName() { return "唱片机互动"; }
    @Override protected String defaultBlockId() { return "minecraft:jukebox"; }
    @Override protected List<String> validActions() { return List.of(); }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        var jukeboxOpt = getBlockEntity(maid.level(), pos, JukeboxBlockEntity.class);
        if (jukeboxOpt.isEmpty()) return;
        JukeboxBlockEntity jukebox = jukeboxOpt.get();

        String wantMusic = params.getOrDefault("music_name", "").toLowerCase();
        ItemStack record = jukebox.getFirstItem();

        // 有唱片 → 弹出 (规则触发时无条件弹出, 让新唱片进来)
        if (!record.isEmpty()) {
            ItemStack remainder = ItemHandlerHelper.insertItem(getMaidInventory(maid), record.copy(), false);
            if (remainder.getCount() < record.getCount()) {
                jukebox.removeItem(0, 1);
                jukebox.setChanged();
                completeFlowTask(maid);
            }
            return; // ★ 本轮已弹出，下个 tick 再插新碟
        }

        // 无唱片 → 插入
        if (jukebox.getFirstItem().isEmpty()) {
            ItemStack disc = extractFromMaidInv(maid, s -> {
                if (!s.is(ItemTags.MUSIC_DISCS)) return false;
                if (wantMusic.isEmpty()) return true;
                String name = s.getDisplayName().getString().toLowerCase();
                String id = s.getItem().toString().toLowerCase();
                return name.contains(wantMusic) || id.contains(wantMusic);
            }, 1);
            if (!disc.isEmpty()) {
                jukebox.setFirstItem(disc.copy());
                jukebox.setChanged();
                levelEvent(maid, pos, disc);
                playInteractionFeedback(maid, pos, SoundEvents.BARREL_OPEN);
                completeFlowTask(maid);
            }
        }
    }

    private void levelEvent(EntityMaid maid, BlockPos pos, ItemStack disc) {
        maid.level().levelEvent(null, 1010, pos, Item.getId(disc.getItem()));
    }
}
