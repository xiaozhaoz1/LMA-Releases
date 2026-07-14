package littlemaidmoreaction.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** v34: 女仆属性编辑 — 5列×4行 网格 + 8组切换 */
public final class MaidEditorScreen extends Screen {
    private static final int COLS = 5, ROWS = 4, PER_PAGE = COLS * ROWS;

    private static final List<Group> GROUPS = List.of(
        new Group("Basic", List.of(
            field("❤生命",     "health",        true,  "maxHealth",   "/"),
            field("⚔攻击",     "attack",        true,  null,          ""),
            field("🏃移速",     "speed",         true,  null,          ""),
            field("📏跟随",     "followRange",   true,  null,          ""),
            field("⭐经验",     "experience",    true,  null,          ""),
            field("💝好感",     "favorability",  true,  "favorLevel",  "Lv."),
            field("🍖饱食",     "hunger",        true,  null,          ""),
            field("🍀幸运",     "luck",          false, null,          ""),
            field("🛡护甲",     "armor",         false, null,          ""),
            field("🛡韧性",     "armorTough",    false, null,          ""),
            field("👶幼年",     "isBaby",        false, null,          ""),
            field("🪑坐着",     "isSitting",     false, null,          ""),
            field("🏠回家",     "isHomeMode",    false, null,          ""),
            field("🐕驯服",     "isTamed",       false, null,          ""),
            field("🔥着火",     "isOnFire",      false, null,          ""),
            field("🏊游泳",     "isSwimming",    false, null,          ""),
            field("💧水中",     "isInWater",     false, null,          ""),
            field("🏃冲刺",     "isSprinting",   false, null,          ""),
            field("🚶潜行",     "isSneaking",    false, null,          ""),
            field("📐摔落",     "fallDistance",  false, null,          "")
        )),
        new Group("Combat", List.of(
            field("⚔攻击力",   "attackDamage",  true,  null,          ""),
            field("💨攻速",     "attackSpeed",   true,  null,          ""),
            field("🛡击退",     "knockbackRes",  true,  null,          ""),
            field("🎯范围",     "meleeRange",    false, null,          ""),
            field("💀无敌",     "isInvulnerable",false, null,          ""),
            field("🛡举盾",     "canUseShield",  false, null,          ""),
            field("🛡格挡",     "isBlocking",    false, null,          ""),
            field("⚔武器",     "hasWeapon",     false, null,          ""),
            field("🛡有盾",     "hasShield",     false, null,          ""),
            field("💪挥臂",     "isSwinging",    false, null,          ""),
            field("🎯瞄准",     "isAiming",      false, null,          ""),
            field("📐摔落",     "fallDistance",  false, null,          "")
        )),
        new Group("AI", List.of(
            field("📋拾取",     "isPickup",      false, null,          ""),
            field("🚪开门",     "isOpenDoor",    false, null,          ""),
            field("🚧栅栏",     "isOpenFenceGate",false, null,          ""),
            field("🧗攀爬",     "isActiveClimbing",false,null,          ""),
            field("😱恐慌",     "enablePanic",   false, null,          ""),
            field("🍽进食",     "enableEating",  false, null,          ""),
            field("🚶随机走",   "enableLookAndWalk",false,null,         ""),
            field("🐴骑乘",     "isRideable",    false, null,          ""),
            field("🏠回家模式", "isHomeMode",    false, null,          ""),
            field("🧠可移动",   "canBrainMove",  false, null,          ""),
            field("🔍寻路可达", "canPathReach",  false, null,          ""),
            field("👁视线可达", "canSee",        false, null,          ""),
            field("⛏破坏方块", "canDestroyBlock",false,null,           ""),
            field("🧱放置方块", "canPlaceBlock", false, null,          ""),
            field("🐕可栓绳",   "canBeLeashed",  false, null,          ""),
            field("⚡雷劈",     "isStruckByLightning",false,null,      "")
        )),
        new Group("Display", List.of(
            field("🎒背包显",   "isShowBackpack",false, null,          ""),
            field("💍背饰显",   "isShowBackItem",false, null,          ""),
            field("💬气泡显",   "isChatBubbleShow",false,null,         ""),
            field("🔊音效率",   "soundFreq",     true,  null,          ""),
            field("✨发光",     "isGlowing",     false, null,          ""),
            field("👻隐形",     "isInvisible",   false, null,          ""),
            field("🔇静音",     "isSilent",      false, null,          ""),
            field("🎒类型",     "backpackType",  false, null,          ""),
            field("📦格子",     "backpackSlots", false, null,          ""),
            field("🎒有背包",   "hasBackpack",   false, null,          ""),
            field("🎭模型ID",   "modelId",       false, null,          ""),
            field("🔊声包ID",   "soundPackId",   false, null,          "")
        )),
        new Group("Resist", List.of(
            field("🛡无敌",     "isInvulnerable",false, null,          ""),
            field("🔥防火",     "isOnFire",      false, null,          ""),
            field("💧雨中",     "isInRain",      false, null,          ""),
            field("⚡雷劈",     "isStruckByLightning",false,null,      ""),
            field("🎣钓鱼竿",   "hasFishingHook",false, null,          ""),
            field("🏗结构生成", "isStructureSpawn",false,null,         ""),
            field("💀无敌2",    "isInvulnerable",false, null,          ""),
            field("🛡防弹",     "armor",         false, null,          "")
        )),
        new Group("Schedule", List.of(
            field("📅日程模式", "getSchedule",   false, null,          ""),
            field("⏰当前活动", "scheduleDetail", false, null,          ""),
            field("💼工作",     "workPos",       false, null,          ""),
            field("☕空闲",     "idlePos",       false, null,          ""),
            field("😴睡眠",     "sleepPos",      false, null,          ""),
            field("⚙已配置",   "isScheduleConfigured",false,null,     ""),
            field("📍工作中心", "restrictCenter", false, null,          ""),
            field("📏工作半径", "restrictRadius", false, null,          ""),
            field("🌍搜索维度", "searchDimension",false, null,          ""),
            field("🔍搜索半径", "searchRadius",   false, null,          "")
        )),
        new Group("Custom", List.of(
            field("🔫物品速度",  "useItemSpeed", true,   null,         ""),
            field("🏹弩速度",    "crossbowSpeed",true,   null,         ""),
            field("🔫枪速度",    "gunSpeed",     true,   null,         ""),
            field("⏱射击冷却",  "shootCooldown",true,   null,         ""),
            field("🔱戟冷却",    "tridentCooldown",true, null,         ""),
            field("📦拾取范围",  "pickupRange",  true,   null,         ""),
            field("🛡盾时间",    "shieldTick",   true,   null,         ""),
            field("🍖饱食度",    "maidHunger",   true,   null,         ""),
            field("💪最大生命",  "maxHealth",    true,   null,         ""),
            field("🛡击退抗性",  "knockbackRes", true,   null,         ""),
            field("🫁氧气",      "airSupply",    false,  "maxAir",     "/"),
            field("🫁最大氧气",  "maxAir",       true,   null,         "")
        )),
        new Group("Owner", List.of(
            field("👤主人名",   "ownerName",     false, null,          ""),
            field("🆔主人UUID", "ownerUUID",     false, null,          ""),
            field("📏距离",     "ownerDistance", false, null,          ""),
            field("❤主生命",    "ownerHealth",   false, null,          ""),
            field("🗡主手持",   "ownerHoldingId",false, null,          ""),
            field("🛡主副手",   "ownerOffhandId",false, null,          ""),
            field("🛡主护甲",   "ownerArmor",    false, null,          ""),
            field("🍖驯服品",   "tamedItem",     false, null,          ""),
            field("🍬诱惑品",   "temptationItem",false, null,          ""),
            field("⭐满好感",   "favorability",  false, "favorLevel",  "Lv.")
        ))
    );

    private final Screen parent;
    private final EntityMaid maid;
    private int groupIdx;
    private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
    private final Map<String, String> fieldCache = new LinkedHashMap<>();

    // layout calc
    private int colW, startX, startY;
    private static final int FIELD_W = 80, FIELD_H = 20, GAP_X = 30, GAP_Y = 40;

    public MaidEditorScreen(Screen parent, EntityMaid maid) {
        super(Component.literal("女仆编辑: " + maid.getName().getString()));
        this.parent = parent;
        this.maid = maid;
    }

    @Override
    protected void init() {
        widgets.clear();
        startX = (width - COLS * (FIELD_W + GAP_X) + GAP_X) / 2;
        startY = 50;
        colW = FIELD_W + GAP_X;

        // top bar
        addRenderableWidget(Button.builder(Component.literal("←"), b -> onClose())
                .pos(5, 10).size(20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> prevGroup())
                .pos(width / 2 - 70, 10).size(16, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> nextGroup())
                .pos(width / 2 + 54, 10).size(16, 20).build());

        // group dropdown
        var groupNames = GROUPS.stream().map(g -> g.name).toList();
        addRenderableWidget(CycleButton.builder((String s) -> Component.literal(s))
                .withValues(groupNames)
                .withInitialValue(GROUPS.get(groupIdx).name)
                .create(width / 2 - 52, 10, 104, 20, Component.literal(""), (b, v) -> switchGroup(v)));

        // bottom buttons
        addRenderableWidget(Button.builder(Component.literal("应用"),
                b -> applyChanges()).pos(width - 120, height - 26).size(50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("导出"),
                b -> exportTemplate()).pos(width - 60, height - 26).size(50, 20).build());

        buildFields();
    }

    private void buildFields() {
        widgets.clear();
        Group g = GROUPS.get(groupIdx);
        for (int i = 0; i < Math.min(g.fields.size(), PER_PAGE); i++) {
            Field f = g.fields.get(i);
            int col = i % COLS, row = i / COLS;
            int x = startX + col * colW, y = startY + row * GAP_Y;

            String val = readField(f);
            if (f.isBool) {
                boolean b = val.equals("true") || val.equals("✓");
                var btn = CycleButton.booleanBuilder(
                        Component.literal("✓"), Component.literal("✗"))
                        .withInitialValue(b)
                        .create(x, y + 12, FIELD_W, FIELD_H, Component.literal(f.label),
                                (cb, v) -> fieldCache.put(f.key, v ? "true" : "false"));
                addRenderableWidget(btn);
                widgets.put(f.key, btn);
            } else {
                var edit = new EditBox(font, x, y + 12, FIELD_W, FIELD_H, Component.literal(f.key));
                edit.setValue(val);
                addRenderableWidget(edit);
                widgets.put(f.key, edit);
            }
        }
    }

    private void rebuildFields() {
        clearMyWidgets();
        widgets.clear();
        init();
    }

    private void clearMyWidgets() {
        for (var w : new ArrayList<>(children())) {
            if (w instanceof AbstractWidget aw) removeWidget(aw);
        }
    }

    private String readField(Field f) {
        var m = maid;
        return switch (f.key) {
            // Basic
            case "health" -> String.valueOf((int) m.getHealth());
            case "maxHealth" -> String.valueOf((int) m.getMaxHealth());
            case "attack" -> String.format("%.1f", m.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            case "speed" -> String.format("%.2f", m.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
            case "followRange" -> String.format("%.1f", m.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
            case "experience" -> String.valueOf(m.getExperience());
            case "favorability" -> String.valueOf(MaidStateReader.getFavorability(m));
            case "favorLevel" -> String.valueOf(MaidStateReader.getFavorLevel(m));
            case "hunger" -> String.valueOf(m.getHunger());
            case "luck" -> String.valueOf((int) m.getLuck());
            case "armor" -> String.valueOf(m.getArmorValue());
            case "armorTough" -> String.format("%.1f", m.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS));
            case "knockbackRes" -> String.format("%.1f", m.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
            case "attackDamage" -> String.format("%.1f", m.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            case "attackSpeed" -> String.format("%.1f", m.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
            case "meleeRange" -> String.format("%.1f", MaidStateReader.getMeleeAttackRangeSqr(m, m));

            // Booleans
            case "isBaby" -> b(m.isBaby());
            case "isSitting" -> b(m.isMaidInSittingPose());
            case "isHomeMode" -> b(m.isHomeModeEnable());
            case "isTamed" -> b(MaidStateReader.isTamed(m));
            case "isOnFire" -> b(m.isOnFire());
            case "isSwimming" -> b(m.isSwimming());
            case "isInWater" -> b(m.isInWater());
            case "isSprinting" -> b(m.isSprinting());
            case "isSneaking" -> b(m.isCrouching());
            case "fallDistance" -> String.format("%.1f", m.fallDistance);
            case "isInvulnerable" -> b(m.getIsInvulnerable());
            case "canUseShield" -> b(MaidStateReader.canUseShield(m));
            case "isBlocking" -> b(m.isBlocking());
            case "hasWeapon" -> b(MaidStateReader.hasWeapon(m));
            case "hasShield" -> b(MaidStateReader.hasShield(m));
            case "isSwinging" -> b(m.isSwingingArms());
            case "isAiming" -> b(m.isAiming());
            case "isPickup" -> b(m.isPickup());
            case "isRideable" -> b(m.isRideable());
            case "canBrainMove" -> b(MaidStateReader.canBrainMove(m));
            case "isOpenDoor" -> b(MaidStateReader.isOpenDoor(m));
            case "isOpenFenceGate" -> b(MaidStateReader.isOpenFenceGate(m));
            case "isActiveClimbing" -> b(MaidStateReader.isActiveClimbing(m));
            case "enablePanic" -> b(MaidStateReader.isEnablePanic(m));
            case "enableEating" -> b(MaidStateReader.isEnableEating(m));
            case "enableLookAndWalk" -> b(MaidStateReader.isEnableLookAndRandomWalk(m));
            case "canPathReach" -> b(MaidStateReader.canPathReachPos(m, m.blockPosition()));
            case "canSee" -> b(false); // needs target entity
            case "canDestroyBlock" -> b(MaidStateReader.canDestroyBlock(m, m.blockPosition()));
            case "canPlaceBlock" -> b(MaidStateReader.canPlaceBlock(m, m.blockPosition()));
            case "canBeLeashed" -> b(false); // needs player
            case "isStruckByLightning" -> b(m.isStruckByLightning());
            case "isShowBackpack" -> b(MaidStateReader.isShowBackpack(m));
            case "isShowBackItem" -> b(MaidStateReader.isShowBackItem(m));
            case "isChatBubbleShow" -> b(MaidStateReader.isChatBubbleShow(m));
            case "soundFreq" -> String.format("%.2f", MaidStateReader.getSoundFreq(m));
            case "isGlowing" -> b(m.isCurrentlyGlowing());
            case "isInvisible" -> b(m.isInvisible());
            case "isSilent" -> b(m.isSilent());
            case "backpackType" -> String.valueOf(MaidStateReader.getBackpackType(m));
            case "backpackSlots" -> String.valueOf(MaidStateReader.getBackpackSlots(m));
            case "hasBackpack" -> b(MaidStateReader.hasBackpack(m));
            case "modelId" -> String.valueOf(MaidStateReader.getModelId(m));
            case "soundPackId" -> String.valueOf(MaidStateReader.getSoundPackId(m));
            case "isInRain" -> b(MaidStateReader.isInRain(m));
            case "hasFishingHook" -> b(MaidStateReader.hasFishingHook(m));
            case "isStructureSpawn" -> b(MaidStateReader.isStructureSpawn(m));
            case "getSchedule" -> String.valueOf(MaidStateReader.getSchedule(m));
            case "scheduleDetail" -> String.valueOf(MaidStateReader.getScheduleDetail(m));
            case "restrictCenter" -> String.valueOf(MaidStateReader.getRestrictCenter(m));
            case "restrictRadius" -> String.format("%.0f", MaidStateReader.getRestrictRadius(m));
            case "searchDimension" -> String.valueOf(MaidStateReader.getSearchDimension(m));
            case "searchRadius" -> String.format("%.1f", MaidStateReader.getSearchRadius(m));
            case "workPos" -> fkey("schedule_work");
            case "idlePos" -> fkey("schedule_idle");
            case "sleepPos" -> fkey("schedule_sleep");
            case "isScheduleConfigured" -> b(MaidStateReader.isScheduleConfigured(m));

            // Custom
            case "useItemSpeed" -> String.format("%.0f", MaidStateReader.getUseItemSpeed(m));
            case "crossbowSpeed" -> String.format("%.0f", MaidStateReader.getCrossbowAttackSpeed(m));
            case "gunSpeed" -> String.format("%.0f", MaidStateReader.getGunAttackSpeed(m));
            case "shootCooldown" -> String.format("%.0f", MaidStateReader.getShootCooldown(m));
            case "tridentCooldown" -> String.format("%.0f", MaidStateReader.getTridentCooldown(m));
            case "pickupRange" -> String.format("%.1f", MaidStateReader.getPickupRange(m));
            case "shieldTick" -> String.format("%.0f", MaidStateReader.getPassiveUseShieldTick(m));
            case "maidHunger" -> String.format("%.0f", m.getAttributeValue(
                    com.github.tartaricacid.touhoulittlemaid.init.InitAttribute.MAID_HUNGER.get()));
            case "airSupply" -> String.valueOf(m.getAirSupply());
            case "maxAir" -> String.valueOf(m.getMaxAirSupply());

            // Owner
            case "ownerName" -> MaidStateReader.getOwnerName(m);
            case "ownerUUID" -> {
                var u = MaidStateReader.getOwnerUUID(m);
                yield u != null ? u.toString().substring(0, 8) : "none";
            }
            case "ownerDistance" -> String.format("%.1f", MaidStateReader.getOwnerDistance(m));
            case "ownerHealth" -> String.format("%.0f", MaidStateReader.getOwnerHealth(m));
            case "ownerHoldingId" -> MaidStateReader.getOwnerHoldingItemId(m);
            case "ownerOffhandId" -> MaidStateReader.getOwnerOffhandId(m);
            case "ownerArmor" -> String.format("%.0f", MaidStateReader.getOwnerArmor(m));
            case "tamedItem" -> {
                var s = MaidStateReader.getTamedItem(m);
                yield s.isEmpty() ? "none" : s.getHoverName().getString();
            }
            case "temptationItem" -> {
                var s = MaidStateReader.getTemptationItem(m);
                yield s.isEmpty() ? "none" : s.getHoverName().getString();
            }
            default -> "?";
        };
    }

    private String fkey(String k) { return fieldCache.getOrDefault(k, "-"); }
    private static String b(boolean v) { return v ? "true" : "false"; }

    private void applyChanges() {
        // TODO: parse values from fieldCache/widgets and call MaidStateWriter
    }

    private void exportTemplate() {
        // TODO: serialize to JSON
    }

    private void prevGroup() { groupIdx = (groupIdx - 1 + GROUPS.size()) % GROUPS.size(); rebuildFields(); }
    private void nextGroup() { groupIdx = (groupIdx + 1) % GROUPS.size(); rebuildFields(); }
    private void switchGroup(String name) {
        for (int i = 0; i < GROUPS.size(); i++) {
            if (GROUPS.get(i).name.equals(name)) { groupIdx = i; break; }
        }
        rebuildFields();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        super.render(g, mx, my, pt);

        // draw labels above each field
        Group g2 = GROUPS.get(groupIdx);
        for (int i = 0; i < Math.min(g2.fields.size(), PER_PAGE); i++) {
            Field f = g2.fields.get(i);
            int col = i % COLS, row = i / COLS;
            int x = startX + col * colW, y = startY + row * GAP_Y;
            g.drawString(font, f.label, x, y, 0xCCCCCC);

            // secondary info
            if (f.secKey != null) {
                String sec = readField(new Field("", f.secKey, false, null, ""));
                String txt = f.secPrefix + sec;
                int secX = x + FIELD_W + 4;
                g.drawString(font, txt, secX, y + 16, 0x999999);
            }
        }

        // page indicator
        g.drawString(font, (groupIdx + 1) + "/" + GROUPS.size(), width - 30, 14, 0x666666);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    // -- data --
    record Group(String name, List<Field> fields) {}
    record Field(String label, String key, boolean isBool, String secKey, String secPrefix) {}
    private static Field field(String l, String k, boolean editable, String secKey, String secPrefix) {
        return new Field(l, k, !editable, secKey, secPrefix);
    }
}
