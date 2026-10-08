package com.github.xiaozhaoz1.littlemaidmoreaction.client;

import com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower.DefenseTowerMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower.DefenseTowerScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.AiControlConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.BellRingConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.BlockInteractConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.CraftChainConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.DamFillConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.ItemListConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.VoidExcavationConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigMenu;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * **客户端菜单↔屏 绑定表** (2026-09-21 建立, 用户批准) —— 学 TLM `client/init/InitContainerGui` 的"一处集中" ✓
 * + 复用本仓 `network/PacketRegistry.DEFS` 的"清单 = 唯一事实源 + 平台侧消费"模式 ✓ (不发明新法 ✓)。
 *
 * <p><b>为什么放 client/</b> (实测): 本表含 `MenuScreens.ScreenConstructor` = **客户端类型** ✗ ⇒ 任何**服务端可加载**的
 * 类 import 它都会触发**错题 #168**"服务端加载客户端类"炸服 ✗。而 `screen/` **不是**客户端安全包
 * (30 类 0 个 `@OnlyIn`、无 package-info、在 common ⇒ 服务端可达 ✗) ⇒ 放它会重演 #168 ✗；
 * `client/` 是 §1 的**纯客户端层** (只被两平台 ClientEntry 引用 ✓，已含 `@OnlyIn` 类 ✓) ⇒ 落这里 ✓。
 *
 * <p><b>范围</b> (经"旧注册一致性核对"实测 ✓): 两平台各注册 **8 对**、集合逐字一致 ✓ ⇒ 本表 8 条 ✓。
 * 不在表内的 2 个菜单 id (原因见 `MenuScreenPairingGuardTest` 白名单 ✓): `maid_assembly`(屏由 **compat 模块自注册** ✓) ·
 * `passive_toggle_config`(**死对**: 屏全仓 0 引用 ✗)。
 *
 * <p><b>目标 (如实降级)</b>: 加一个 GUI 仍是 **2~4 处** —— 本表 +1 行 ✓ · 两平台 common 入口各 +1 条 `MENU_TYPES.register` ✓ ·
 * 两平台 ClientEntry 的 id→MenuType 映射各 +1 条 ✓ (**不是 1 处** ✗)。收益: 屏侧由 **2 平台 × 8 条手写注册 + FQN 汤** ✗
 * ⇒ **1 张表 + 2 个循环** ✓，且守卫可断言"表 ↔ 两平台菜单 id"**双向差集** ⇒ **漂移不可能** ✓
 * (TLM 同构仅作额外支持 ✓，不是目标本身)。
 *
 * <p><b>泛型说明</b>: 表用"泛型记录 + 每条显式类型实参" ⇒ 构造器引用可被正确推断 ✓；
 * 平台侧遍历 `List<Binding<?, ?>>` 时类型被擦除 ⇒ 各家做**一次性 unchecked 转换** ✓ (常规写法 ✓)。
 */
public final class GuiScreenBindings {

    private GuiScreenBindings() {}

    /** 一条绑定: 菜单 id (与 `MENU_TYPES.register("<id>")` 一致 ✓) + 屏构造器 */
    public record Binding<M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>>(
            String id, MenuScreens.ScreenConstructor<M, S> screen) {}

    /** **唯一屏侧事实源** (id 必须唯一 — 由 MenuScreenPairingGuardTest 断言 ✓；顺序与菜单侧无关 ✓) */
    public static final List<Binding<?, ?>> DEFS = List.of(
            new Binding<BlockInteractConfigMenu, BlockInteractConfigScreen>("block_interact_config", BlockInteractConfigScreen::new),
            new Binding<ItemListConfigMenu, ItemListConfigScreen>("item_list_config", ItemListConfigScreen::new),
            new Binding<CraftChainConfigMenu, CraftChainConfigScreen>("craft_chain_config", CraftChainConfigScreen::new),
            new Binding<BellRingConfigMenu, BellRingConfigScreen>("bell_ring_config", BellRingConfigScreen::new),
            new Binding<DamFillConfigMenu, DamFillConfigScreen>("dam_fill_config", DamFillConfigScreen::new),
            new Binding<VoidExcavationConfigMenu, VoidExcavationConfigScreen>("void_excavation_config", VoidExcavationConfigScreen::new),
            new Binding<AiControlConfigMenu, AiControlConfigScreen>("ai_control_config", AiControlConfigScreen::new),
            new Binding<DefenseTowerMenu, DefenseTowerScreen>("defense_tower", DefenseTowerScreen::new));

    /** 取擦除后的屏构造器 (平台在自己的注册循环里做一次性 unchecked 转换 ✓；两平台注册 API 不同 ⇒ 由平台决定 ✓) */
    @SuppressWarnings("rawtypes")
    public static MenuScreens.ScreenConstructor screenOf(Binding<?, ?> binding) {
        return binding.screen();
    }
}
