package littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaFlowCoordinationBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 便携装配 IMaidTask.
 * <p>WorkEatBehavior / NearbyCollectBehavior 由 {@code DefaultBehaviorBrain} 自动注入.
 */
public final class MaidAssemblyTask implements IMaidTask {

    public static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "task/maid_assembly");

    private static final MaidAssemblyTask INSTANCE = new MaidAssemblyTask();
    public static MaidAssemblyTask get() { return INSTANCE; }
    private MaidAssemblyTask() {}

    @Override public ResourceLocation getUid() { return UID; }
    @Override public ItemStack getIcon() { return new ItemStack(Items.CRAFTING_TABLE); }
    @Nullable @Override public SoundEvent getAmbientSound(EntityMaid maid) { return null; }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return new ArrayList<>(List.of(Pair.of(5, new LmaFlowCoordinationBehavior())));
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return new ArrayList<>(List.of(Pair.of(5, new LmaFlowCoordinationBehavior())));
    }

    @Override public boolean enableLookAndRandomWalk(EntityMaid maid) { return false; }
    @Override public boolean enablePanic(EntityMaid maid) { return false; }
    @Override public boolean enableEating(EntityMaid maid) { return true; }
    @Override public boolean isEnable(EntityMaid maid) { return net.minecraftforge.fml.ModList.get().isLoaded("create"); }
    @Override public boolean isHidden(EntityMaid maid) { return false; }
    @Override public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) { return FunctionCallSwitchResult.OK; }
    @Override public String getMaidActionSummary() { return "执行便携装配任务"; }

    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return littlemaidmoreaction.littlemaidmoreaction.task.gui.TaskConfigGui.of(maid);
    }
}
