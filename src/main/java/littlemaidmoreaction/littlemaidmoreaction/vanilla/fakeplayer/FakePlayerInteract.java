package littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * FakePlayer 右键交互样板 — 3 处重复 (v38 InteractBlockAction / v67 BlockInteractService) 的公共骨架。
 *
 * <p>new LmaFakePlayer → simulate(RIGHT_CLICK_ONCE) → syncHandToMaid → cleanup。
 * 距离/方块存在等业务检查由调用方负责。
 */
public final class FakePlayerInteract {

    private FakePlayerInteract() {}

    /**
     * 模拟女仆右键方块一次。
     *
     * @return true=交互成功, false=模拟失败
     */
    public static boolean rightClick(ServerLevel world, EntityMaid maid, BlockPos pos, Direction face) {
        LmaFakePlayer fp = new LmaFakePlayer(world, maid, pos);
        try {
            boolean ok = LmaPlayerSimulator.simulate(fp, world, pos, face,
                    LmaPlayerSimulator.Mode.RIGHT_CLICK_ONCE);
            LmaPlayerSimulator.syncHandToMaid(fp);
            return ok;
        } finally {
            LmaPlayerSimulator.cleanup(fp, world);
        }
    }
}
