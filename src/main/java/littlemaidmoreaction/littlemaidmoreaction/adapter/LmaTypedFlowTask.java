package littlemaidmoreaction.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 每任务类型 IMaidTask — 每种 LMA 任务类型独立注册到 TLM TaskManager。
 *
 * <p>与 {@link LmaFlowTask}（泛用 fallback）的区别：
 * <ul>
 *   <li>UID: {@code lma:task/<taskType>}（而非 {@code lma:flow_task}）</li>
 *   <li>isHidden: false — 在 TLM GUI 中可见</li>
 *   <li>图标/名称因任务类型而异</li>
 * </ul>
 *
 * <p>行为与 {@link LmaFlowTask} 完全一致：
 * <ul>
 *   <li>禁用随机走动、禁用恐慌</li>
 *   <li>Brain 运行 {@link LmaFlowCoordinationBehavior}</li>
 * </ul>
 */
public final class LmaTypedFlowTask implements IMaidTask {

    private final ResourceLocation uid;
    private final String taskType;
    private final ItemStack icon;

    LmaTypedFlowTask(String taskType) {
        this.taskType = taskType;
        String safePath = sanitize(taskType);
        this.uid = ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "task/" + safePath);
        this.icon = LmaTaskTypeRegistry.getIcon(taskType);
    }

    /** 将 task_type 转为 ResourceLocation path 合法字符 */
    private static String sanitize(String raw) {
        String s = raw.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_\\-./]", "_");
        if (s.isEmpty()) s = "unknown";
        return s;
    }

    // ── IMaidTask ──

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // ★ v12.7 fix: TLM MaidBrain 对返回值做 .add() → 必须用可变列表
        return new ArrayList<>(List.of(Pair.of(4, new LmaFlowCoordinationBehavior())));
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return new ArrayList<>(List.of(Pair.of(4, new LmaFlowCoordinationBehavior())));
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }

    @Override
    public boolean enablePanic(EntityMaid maid) {
        return false;
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return true;
    }

    @Override
    public boolean isHidden(EntityMaid maid) {
        // ★ 在 TLM GUI 中可见 — 玩家能直观看到女仆当前任务类型
        return false;
    }

    @Override
    public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) {
        return FunctionCallSwitchResult.OK;
    }

    @Override
    public String getMaidActionSummary() {
        return "执行LMA任务：" + taskType;
    }

    @Override
    public MutableComponent getName() {
        return Component.translatable("task." + LittleMaidMoreAction.MOD_ID + "." + sanitize(taskType));
    }

    // ── 辅助 ──

    /** 获取原始 task_type 字符串 (如 "altar_craft") */
    public String taskType() {
        return taskType;
    }
}
