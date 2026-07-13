package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.visual;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 视觉输出 — 动画/音效/粒子 覆盖 ~6 个 impl/action/visual/ 类 */
public final class VisualOutput {
    private VisualOutput() {}

    // === 动画 ===
    public static void playAnim(EntityMaid maid, String name, String mode, Map<String, String> setup) {
        var data = maid.getPersistentData();
        data.putString("lma_anim", name);
        data.putString("lma_anim_mode", mode);
        data.putLong("lma_anim_tick", maid.level().getGameTime());
        if (setup != null) {
            setup.forEach((k, v) -> data.putString("lma_anim_" + k, v));
        }
    }
    public static void playWeaponAnim(EntityMaid maid, String weaponId) {
        var data = maid.getPersistentData();
        data.putString("lma_weapon_anim", weaponId);
        data.putLong("lma_anim_tick", maid.level().getGameTime());
    }
    /** 清除所有 LMA 动画 PersistentData key（Phase 7 新增） */
    public static void resetAnimation(EntityMaid maid) {
        var data = maid.getPersistentData();
        data.remove("lma_anim"); data.remove("lma_anim_mode"); data.remove("lma_anim_phase");
        data.remove("lma_anim_start"); data.remove("lma_anim_casting"); data.remove("lma_anim_end");
        data.remove("lma_dur_start"); data.remove("lma_dur_casting"); data.remove("lma_dur_end");
        data.remove("lma_anim_priority"); data.remove("lma_lock_move"); data.remove("lma_anim_tick");
        data.remove("lma_anim_id"); data.remove("lma_anim_time");
    }

    // === 音效 ===
    public static void playSound(EntityMaid maid, String soundId, float volume, float pitch) {
        var rl = ResourceLocation.tryParse(soundId);
        if (rl == null) return;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (sound == null) return;
        maid.level().playSound(null, maid.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
    public static void playSoundAt(Level world, BlockPos pos, String soundId, float volume, float pitch) {
        var rl = ResourceLocation.tryParse(soundId);
        if (rl == null) return;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (sound == null) return;
        world.playSound(null, pos, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // === Phase 4: 随机音效 + 模组随机音效 ===
    /** 逗号分隔随机音效 — 提取自 PlaySoundAction */
    public static void playSoundRandom(EntityMaid maid, String soundId, float volume, float pitch) {
        if (!soundId.contains(",")) { playSound(maid, soundId.trim(), volume, pitch); return; }
        String[] parts = soundId.split(",");
        var valid = new ArrayList<String>();
        for (String p : parts) { String t = p.trim(); if (!t.isEmpty()) valid.add(t); }
        if (valid.isEmpty()) return;
        playSound(maid, valid.get(ThreadLocalRandom.current().nextInt(valid.size())), volume, pitch);
    }
    /** 模组命名空间随机音效 — 提取自 PlaySoundAction */
    public static void playSoundModRandom(EntityMaid maid, String namespace, float volume, float pitch) {
        var sounds = ForgeRegistries.SOUND_EVENTS.getEntries().stream()
            .filter(e -> e.getKey().location().getNamespace().equals(namespace))
            .map(e -> e.getKey().location().toString()).toList();
        if (sounds.isEmpty()) return;
        playSound(maid, sounds.get(ThreadLocalRandom.current().nextInt(sounds.size())), volume, pitch);
    }

    // === 粒子 ===
    public static void spawnParticle(Level world, String particleId, BlockPos pos, int count, double spread) {
        SimpleParticleType type = null;
        if ("heart".equals(particleId)) type = ParticleTypes.HEART;
        else if ("flame".equals(particleId)) type = ParticleTypes.FLAME;
        else if ("smoke".equals(particleId)) type = ParticleTypes.SMOKE;
        else if ("crit".equals(particleId)) type = ParticleTypes.CRIT;
        else if ("magic_crit".equals(particleId)) type = ParticleTypes.ENCHANTED_HIT;
        else if ("portal".equals(particleId)) type = ParticleTypes.PORTAL;
        if (type == null) return;
        for (int i = 0; i < count; i++) {
            double ox = (world.random.nextDouble() - 0.5) * spread;
            double oy = world.random.nextDouble() * spread;
            double oz = (world.random.nextDouble() - 0.5) * spread;
            world.addParticle(type, pos.getX() + 0.5 + ox, pos.getY() + 0.5 + oy, pos.getZ() + 0.5 + oz, 0, 0, 0);
        }
    }
    /** 注册表粒子（任意粒子类型）— Phase 4 新增 */
    public static void spawnParticleAny(Level world, String particleId, BlockPos pos, int count, double spread) {
        var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.tryParse(particleId));
        if (!(type instanceof net.minecraft.core.particles.ParticleOptions po)) return;
        for (int i = 0; i < count; i++) {
            double ox = (world.random.nextDouble() - 0.5) * spread;
            double oy = world.random.nextDouble() * spread;
            double oz = (world.random.nextDouble() - 0.5) * spread;
            world.addParticle(po, pos.getX() + 0.5 + ox, pos.getY() + 0.5 + oy, pos.getZ() + 0.5 + oz, 0, 0, 0);
        }
    }
    public static void spawnHeartParticle(EntityMaid maid) {
        spawnHeartParticle(maid, 1);
    }
    /** 爱心粒子 — count 指定数量，随机位置散布 */
    public static void spawnHeartParticle(EntityMaid maid, int count) {
        if (maid.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            double x = maid.getRandomX(1.0);
            double y = maid.getRandomY() + 0.5;
            double z = maid.getRandomZ(1.0);
            sl.sendParticles(ParticleTypes.HEART, x, y, z, count, 0.02, 0.02, 0.02, 0.02);
        }
    }
    /** 服务端粒子（ServerLevel.sendParticles，所有客户端可见） */
    public static void spawnParticleServer(net.minecraft.server.level.ServerLevel level, String particleId,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        var rl = ResourceLocation.tryParse(particleId);
        if (rl == null) return;
        var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(rl);
        if (type instanceof net.minecraft.core.particles.ParticleOptions po)
            level.sendParticles(po, x, y, z, count, dx, dy, dz, speed);
    }
}
