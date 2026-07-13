package littlemaidmoreaction.littlemaidmoreaction.api;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 模组核心 API — v7 重构为委托存根。
 *
 * <p>实际逻辑已拆分到：
 * <ul>
 *   <li>{@link AnimationDurationManager} — 动画时长/ID 管理</li>
 *   <li>{@link WeaponAnimationMapper} — 武器→动画映射</li>
 *   <li>{@link AnimationResourceRegistrar} — 动画资源注册/扫描</li>
 * </ul>
 *
 * <p>此类保留公开 API 入口 + 资源重载监听器 + {@link #findMaidById(int)}。
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MoreActionAPI {

    // ======================== 资源重载 ========================

    @SubscribeEvent
    public static void onClientReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier, rm, prep, reload, back, game) ->
                barrier.wait(null).thenRunAsync(() -> {
                    littlemaidmoreaction.littlemaidmoreaction.storage.StartupLoader.reload();
                    littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage.reload();
                    var animRes = littlemaidmoreaction.littlemaidmoreaction.resource.DynamicAnimationResources.instance;
                    if (animRes != null) animRes.reload();
                    AnimationDurationManager.loadClientDurations();
                    try {
                        com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader.reloadPacks();
                    } catch (Exception e) {
                        LittleMaidMoreAction.LOGGER.warn("[LMA] reloadPacks: {}", e.getMessage());
                    }
                }, game));
    }

    // ======================== 委托 — 动画时长管理 ========================

    public static void loadServerDurations() { AnimationDurationManager.loadServerDurations(); }

    @OnlyIn(Dist.CLIENT)
    public static void loadClientDurations() { AnimationDurationManager.loadClientDurations(); }

    public static int getAnimationDuration(String animName) { return AnimationDurationManager.getAnimationDuration(animName); }

    public static boolean isAnimationValid(String animName) { return AnimationDurationManager.isAnimationValid(animName); }

    public static boolean checkFallbackAnimations() { return AnimationDurationManager.checkFallbackAnimations(); }

    public static Map<String, Integer> getAllAnimationDurations() { return AnimationDurationManager.getAllAnimationDurations(); }

    // ======================== 委托 — 武器映射 ========================

    public static String getRandomAnimationForWeapon(Item item) { return WeaponAnimationMapper.getRandomAnimationForWeapon(item); }

    public static Map<String, List<String>> getWeaponMappings() { return WeaponAnimationMapper.getWeaponMappings(); }

    public static String pickRandomAnimation(List<String> candidates, String fallback) { return WeaponAnimationMapper.pickRandom(candidates, fallback); }

    // ======================== 实体查询 ========================

    @Nullable
    public static EntityMaid findMaidById(int id) {
        net.minecraft.server.MinecraftServer srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return null;
        for (net.minecraft.server.level.ServerLevel lvl : srv.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e instanceof EntityMaid m) return m;
        }
        return null;
    }

    // ======================== 委托 — 动画资源注册 ========================

    public static void registerCustomAnimations(DefaultGeckoAnimationEvent event) { AnimationResourceRegistrar.registerCustomAnimations(event); }

    @OnlyIn(Dist.CLIENT)
    public static void scanCustomAnimations() { AnimationResourceRegistrar.scanCustomAnimations(); }

    @OnlyIn(Dist.CLIENT)
    public static void registerAnimation(DefaultGeckoAnimationEvent event, ResourceLocation path) { AnimationResourceRegistrar.registerAnimation(event, path); }
}
