package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.output;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/** TPM 输出原语 */
public final class TpmWriter {
    private TpmWriter() {}

    public static void forceGuard(EntityMaid maid, float damage) {
        maid.getPersistentData().putFloat("truePowerOfMaid.guardDamage", damage);
    }
}
