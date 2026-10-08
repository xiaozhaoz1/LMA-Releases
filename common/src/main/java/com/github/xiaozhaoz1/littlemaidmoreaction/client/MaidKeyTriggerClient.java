package com.github.xiaozhaoz1.littlemaidmoreaction.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.InteractTriggerPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
//?}
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用按键触发客户端门面 (v79.51 KeyTrigger 线路) — 注册 MC 自带按键绑定
 * (选项→控制 界面可重绑, 默认数字键 0) + 按下检测 → 发送 {@link InteractTriggerPacket}。
 *
 * <p>v67 {@code BlockInteractKeyMapping} 泛化 (原类已删): 双平台事件接线不在本类 —
 * 由各 loader 客户端入口注册 (forge: LmaForgeClientEntry MOD/GAME bus 手动;
 * neoforge: LmaNeoForgeClientEntry 构造器手动 — v79.18 教训: neoforge GAME bus
 * 静态订阅失效)。首个按键默认触发 {@code block_interact} (DEFAULT_KEY_ID)。
 *
 * <p>v79.51 (批次 B2): 单字段 {@link #TRIGGER_KEY} → 注册序稳定的
 * {@link LinkedHashMap} 映射表 {@code KeyMapping → keyId}; 双平台 entry
 * RegisterKeyMappingsEvent 遍历 {@link #getAllBindings()} 全注册,
 * {@link #handleKeyInput()} 遍历检测。新增按键: {@link #registerBinding}
 * 在 RegisterKeyMappingsEvent 前调用即可 (零网络/服务端改动 —
 * keyId 与 {@link com.github.xiaozhaoz1.littlemaidmoreaction.network.KeyTriggerRegistry}
 * 注册 id 对应)。
 */
@OnlyIn(Dist.CLIENT)
public final class MaidKeyTriggerClient {

    /** 首个消费者 — 默认按键触发的 keyId (KeyTriggerRegistry 注册 id) */
    public static final String DEFAULT_KEY_ID = "block_interact";

    /** 按键绑定 — 双平台 KeyMapping 构造 6 参签名一致 (v67 实测);
     *  兼容保留字段 = 映射表首个注册项。 */
    public static final KeyMapping TRIGGER_KEY = new KeyMapping(
            "key.lma.trigger",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "key.categories.lma");

    /** 按键 → keyId 映射表 (LinkedHashMap — 注册序稳定; 只存 KeyMapping 代码引用, 无实体) */
    private static final Map<KeyMapping, String> KEY_BINDINGS = new LinkedHashMap<>();

    static {
        KEY_BINDINGS.put(TRIGGER_KEY, DEFAULT_KEY_ID);
    }

    private MaidKeyTriggerClient() {}

    /** 注册新按键绑定 — keyId 须先在
     *  {@link com.github.xiaozhaoz1.littlemaidmoreaction.network.KeyTriggerRegistry} 注册;
     *  必须在客户端 RegisterKeyMappingsEvent 之前调用 (entry 遍历注册时才生效);
     *  重复 KeyMapping 覆盖 (注册序确定性, 调用方自控)。 */
    public static void registerBinding(KeyMapping mapping, String keyId) {
        KEY_BINDINGS.put(mapping, keyId);
    }

    /** 全部绑定 (双平台 entry RegisterKeyMappingsEvent 遍历注册用, 只读) */
    public static Collection<KeyMapping> getAllBindings() {
        return List.copyOf(KEY_BINDINGS.keySet());
    }

    /** 按键检测入口 (客户端 entry 的 InputEvent.Key 监听调用) — 遍历映射表, 命中即发对应 keyId */
    public static void handleKeyInput() {
        if (!isInGame()) {
            return;
        }
        for (Map.Entry<KeyMapping, String> e : KEY_BINDINGS.entrySet()) {
            if (e.getKey().consumeClick()) {
                InteractTriggerPacket.sendToServer(e.getValue());
            }
        }
    }

    /** v67 四查守卫: 覆盖层/屏幕/鼠标未抓取/窗口未激活 → 忽略 (原样保留) */
    private static boolean isInGame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return false;
        if (mc.screen != null) return false;
        if (!mc.mouseHandler.isMouseGrabbed()) return false;
        return mc.isWindowActive();
    }
}
