package littlemaidmoreaction.littlemaidmoreaction.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 内存虚拟资源包 — 将 config/animations/*.animation.json 映射到
 * {@code assets/littlemaidmoreaction/animations/} 虚拟路径。
 *
 * <p>零落盘设计：文件从磁盘流式读取。
 * 构造函数接收 {@link StartupLoader#getAnimationFiles()} 的预扫描数据，
 * 避免独立目录扫描的时序问题。</p>
 *
 * <p>在 {@link net.minecraftforge.event.AddPackFindersEvent} 注册后，
 * TLM 的 GeckoLibCache 即可通过 ResourceLocation 加载动画文件。</p>
 */
public final class DynamicAnimationResources implements PackResources {

    private static final String NAMESPACE = LittleMaidMoreAction.MOD_ID;
    private static final String PACK_ID = "lma_dynamic_animations";
    private static final Path ANIM_DIR = LittleMaidMoreAction.CONFIG_DIR.resolve("animations");

    private final Map<ResourceLocation, IoSupplier<InputStream>> resources = new LinkedHashMap<>();
    private final Set<String> animFileNames = new LinkedHashSet<>();  // ★ 直接存文件名，不依赖 ResourceLocation.getPath()
    private final Set<String> namespaces = Set.of(NAMESPACE);
    private final JsonObject packMeta;

    /** 当前活跃实例，供热重载时调用 {@link #reload()}。 */
    public static DynamicAnimationResources instance;

    /**
     * @param animFiles StartupLoader 预扫描的动画文件名列表（如 {@code ["execution.animation.json", ...]}）
     */
    public DynamicAnimationResources(List<String> animFiles) {
        instance = this;
        packMeta = buildPackMeta();
        buildResources(animFiles);
    }

    /** 热重载 — 重新扫描 config 目录，更新资源映射。 */
    public void reload() {
        resources.clear();
        animFileNames.clear();
        // 重载时直接扫描目录（此时 StartupLoader.reload() 已更新文件）
        buildResourcesFromDisk();
        LittleMaidMoreAction.LOGGER.info("[LMA/AnimRes] 重载完成 — {} 个动画文件", resources.size());
    }

    /** 从 StartupLoader 预扫描列表构建资源映射（构造函数使用）。 */
    private void buildResources(List<String> animFiles) {
        int skipped = 0;
        for (String fileName : animFiles) {
            Path filePath = ANIM_DIR.resolve(fileName);
            if (!Files.isRegularFile(filePath)) {
                LittleMaidMoreAction.LOGGER.warn("[LMA/AnimRes] 动画文件不存在: {}", filePath);
                skipped++;
                continue;
            }
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    NAMESPACE, "animations/" + fileName);
            resources.put(loc, () -> {
                try { return Files.newInputStream(filePath); }
                catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            });
            animFileNames.add(fileName);
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/AnimRes] 初始构建 — {} 个动画文件 ({} 预扫描, {} 跳过)",
                resources.size(), animFiles.size(), skipped);
    }

    /** 从磁盘直接扫描（热重载使用）。 */
    private void buildResourcesFromDisk() {
        if (!Files.isDirectory(ANIM_DIR)) {
            LittleMaidMoreAction.LOGGER.warn("[LMA/AnimRes] 动画目录不存在: {}", ANIM_DIR);
            return;
        }
        try (var files = Files.list(ANIM_DIR)) {
            files.filter(p -> {
                        String fn = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return fn.endsWith(".animation.json") && Files.isRegularFile(p);
                    })
                 .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                 .forEach(p -> {
                     String fileName = p.getFileName().toString();
                     ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                             NAMESPACE, "animations/" + fileName);
                     resources.put(loc, () -> {
                         try { return Files.newInputStream(p); }
                         catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                     });
                     animFileNames.add(fileName);
                 });
        } catch (IOException e) {
            LittleMaidMoreAction.LOGGER.error("[LMA/AnimRes] 扫描动画目录失败: {}", e.getMessage(), e);
        }
    }

    private static JsonObject buildPackMeta() {
        var meta = new JsonObject();
        var pack = new JsonObject();
        pack.addProperty("pack_format", 15);
        pack.addProperty("description", "LMA 自定义动画（自动扫描）");
        meta.add("pack", pack);
        return meta;
    }

    public Set<String> getAnimationFiles() {
        return Collections.unmodifiableSet(animFileNames);
    }

    @Override @Nullable
    public IoSupplier<InputStream> getRootResource(String... segments) {
        if (segments.length == 1 && "pack.mcmeta".equals(segments[0]))
            return () -> new ByteArrayInputStream(packMeta.toString().getBytes());
        return null;
    }

    @Override @Nullable
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation loc) {
        return resources.get(loc);
    }

    @Override
    public void listResources(PackType type, String ns, String path, ResourceOutput out) {
        if (!NAMESPACE.equals(ns)) return;
        String prefix = path.isEmpty() ? "" : path + "/";
        for (var e : resources.entrySet())
            if (e.getKey().getPath().startsWith(prefix))
                out.accept(e.getKey(), e.getValue());
    }

    @Override public Set<String> getNamespaces(PackType t) { return namespaces; }

    @Override @Nullable @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> ser) {
        if ("pack".equals(ser.getMetadataSectionName())) {
            try { return ser.fromJson(packMeta.getAsJsonObject("pack")); }
            catch (Exception e) { return null; }
        }
        return null;
    }

    @Override public String packId() { return PACK_ID; }
    @Override public boolean isHidden() { return true; }
    @Override public void close() { resources.clear(); }
}
