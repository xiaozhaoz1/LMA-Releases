package com.github.xiaozhaoz1.littlemaidmoreaction.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.client.sound.CustomSoundLoader;
import com.github.tartaricacid.touhoulittlemaid.client.sound.OggReader;
import com.github.tartaricacid.touhoulittlemaid.client.sound.data.SoundData;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * 对主人哈气语音子集加载器 (v79.20, 客户端) — 固定 littlemaid_peco 声音包,
 * 从磁盘读取 11 个 idle 文件的 ogg 解码为 SoundBuffer 缓存, 播放时随机取一个。
 *
 * <p>TLM 机制: 自定义声音包从 {@code gameDir/tlm_custom_pack} 目录/zip 加载
 * ({@link CustomPackLoader#PACK_FOLDER}), 每个 idle 变体挂同一 SoundEvent
 * {@code touhou_little_maid:maid.mode.idle}, 播放时 {@code SoundCache.getBuffer} 全量随机 —
 * 无法指定子集。故本类直接读磁盘文件 + TLM 公开解码器 {@link OggReader}
 * (readSoundDataFromFile public static, 双版本同签名)。
 *
 * <p>zip 形式的包 (未展开目录) 兜底: 退回 TLM 声音缓存全 idle 随机 + 日志告警。
 */
//? if 1.20.1 {
@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
//?} else {
@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
//?}
public final class PecoHaqiSubsetLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** OggReader 需要 log4j Marker (双版实证); 日志调用不走 Marker 参数 (LOGGER 为 slf4j) */
    private static final Marker MARKER = MarkerManager.getMarker("LMA/HaqiOwner");

    /** 用户指定: 对主人哈气随机播放的 11 个 idle 变体 (littlemaid_peco 声音包 mode 目录) */
    private static final Set<String> IDLE_SUBSET = Set.of(
            "idle2", "idle23", "idle32", "idle40", "idle53",
            "idle61", "idle64", "idle66", "idle67", "idle77", "idle78");

    /** 声音包 id (TLM DefaultMaidSoundPack.PECO_SOUND_PACK_ID 实证值) */
    private static final String PECO_PACK_ID = "littlemaid_peco";

    /** idle 事件 (TLM InitSounds.MAID_IDLE 实证: touhou_little_maid:maid.mode.idle) */
    private static final String MAID_IDLE_RL = "touhou_little_maid:maid.mode.idle";

    /** 缓存的子集缓冲 (每会话加载一次) */
    private static List<SoundBuffer> buffers;
    private static boolean loaded;

    private PecoHaqiSubsetLoader() {}

    /** 随机返回一个子集缓冲 — 语音随机与表情随机相互独立 */
    public static SoundBuffer randomBuffer() {
        List<SoundBuffer> list = getOrLoad();
        if (list.isEmpty()) {
            return null;
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private static List<SoundBuffer> getOrLoad() {
        if (!loaded) {
            buffers = load();
            loaded = true;
        }
        return buffers;
    }

    /** 目录形式 peco 包 → OggReader 解码 11 个子集文件; 失败退回 TLM 缓存全随机 */
    private static List<SoundBuffer> load() {
        List<SoundBuffer> result = new ArrayList<>();
        Path packFolder = CustomPackLoader.PACK_FOLDER;
        if (!Files.isDirectory(packFolder)) {
            LOGGER.warn("tlm_custom_pack 目录不存在, 退回全随机 idle: {}", packFolder);
            return fallback();
        }
        try (Stream<Path> walk = Files.walk(packFolder)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().replace('\\', '/').contains("/sounds/maid/mode/"))
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("idle") && name.endsWith(".ogg")
                                && IDLE_SUBSET.contains(name.substring(0, name.length() - 4));
                    })
                    .forEach(p -> decodeFile(p.toFile(), result));
        } catch (IOException e) {
            LOGGER.warn("遍历 tlm_custom_pack 失败, 退回全随机 idle", e);
            return fallback();
        }
        if (result.isEmpty()) {
            LOGGER.warn("peco 包 11 文件子集未找到 (zip 包?), 退回全随机 idle");
            return fallback();
        }
        LOGGER.debug("对主人哈气语音子集已加载: {} 个文件", result.size());
        return result;
    }

    private static void decodeFile(File file, List<SoundBuffer> out) {
        try {
            List<SoundData> sounds = new ArrayList<>();
            OggReader.readSoundDataFromFile(file, sounds, MARKER);
            for (SoundData sound : sounds) {
                out.add(new SoundBuffer(sound.byteBuffer(), sound.audioFormat()));
            }
        } catch (Exception e) {
            LOGGER.warn("解码失败: {}", file.getAbsolutePath(), e);
        }
    }

    /** 兜底: TLM 声音缓存 (peco 包已加载时) 全 idle 随机 — 偏离 11 文件子集, 仅资源缺失时 */
    private static List<SoundBuffer> fallback() {
        try {
            SoundCacheProxy cache = new SoundCacheProxy(PECO_PACK_ID);
            SoundBuffer buffer = cache.bufferOf(MAID_IDLE_RL);
            if (buffer != null) {
                return new ArrayList<>(List.of(buffer));
            }
        } catch (Exception e) {
            LOGGER.warn("TLM 声音缓存兜底失败", e);
        }
        return new ArrayList<>();
    }

    /** 1.20/1.21 同构的 CustomSoundLoader 缓存访问薄封装 */
    private record SoundCacheProxy(String packId) {
        SoundBuffer bufferOf(String location) {
            var cache = CustomSoundLoader.getSoundCache(packId);
            if (cache == null) {
                return null;
            }
            ResourceLocation rl =
//? if 1.20.1 {
                    new ResourceLocation(location);
//?} else {
                    ResourceLocation.parse(location);
//?}
            return cache.getBuffer(rl);
        }
    }
}
