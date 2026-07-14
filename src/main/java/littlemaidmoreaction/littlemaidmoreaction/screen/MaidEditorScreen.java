package littlemaidmoreaction.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** v34.1: 4列×3行 12字段/页, 组内翻页, 中文 */
public final class MaidEditorScreen extends Screen {
    private static final int COLS = 4, ROWS = 3, PER_PAGE = COLS * ROWS;

    private static final List<Group> GROUPS = List.of(
        new Group("基础", List.of(
            f("生命",     "health",        true,  "maxHealth",   "/"),
            f("攻击",     "attack",        true,  null,          ""),
            f("移速",     "speed",         true,  null,          ""),
            f("跟随距离", "followRange",   true,  null,          ""),
            f("经验",     "experience",    true,  null,          ""),
            f("好感度",   "favorability",  true,  "favorLevel",  "Lv."),
            f("饱食度",   "hunger",        true,  null,          ""),
            f("幸运",     "luck",          false, null,          ""),
            f("护甲",     "armor",         false, null,          ""),
            f("护甲韧性", "armorTough",    false, null,          ""),
            f("幼年",     "isBaby",        false, null,          ""),
            f("坐着",     "isSitting",     false, null,          ""),
            f("回家模式", "isHomeMode",    false, null,          ""),
            f("已驯服",   "isTamed",       false, null,          ""),
            f("着火",     "isOnFire",      false, null,          ""),
            f("游泳",     "isSwimming",    false, null,          ""),
            f("水中",     "isInWater",     false, null,          ""),
            f("冲刺",     "isSprinting",   false, null,          ""),
            f("潜行",     "isSneaking",    false, null,          ""),
            f("摔落距离", "fallDistance",  false, null,          "")
        )),
        new Group("战斗", List.of(
            f("攻击力",   "attackDamage",  true,  null,          ""),
            f("攻速",     "attackSpeed",   true,  null,          ""),
            f("击退抗性", "knockbackRes",  true,  null,          ""),
            f("近战范围", "meleeRange",    false, null,          ""),
            f("无敌",     "isInvulnerable",false, null,          ""),
            f("可举盾",   "canUseShield",  false, null,          ""),
            f("格挡中",   "isBlocking",    false, null,          ""),
            f("有武器",   "hasWeapon",     false, null,          ""),
            f("有盾牌",   "hasShield",     false, null,          ""),
            f("挥臂中",   "isSwinging",    false, null,          ""),
            f("瞄准中",   "isAiming",      false, null,          ""),
            f("摔落距离", "fallDistance",  false, null,          "")
        )),
        new Group("AI行为", List.of(
            f("拾取物品", "isPickup",      false, null,          ""),
            f("开门",     "isOpenDoor",    false, null,          ""),
            f("栅栏门",   "isOpenFenceGate",false, null,          ""),
            f("主动攀爬", "isActiveClimbing",false,null,          ""),
            f("恐慌",     "enablePanic",   false, null,          ""),
            f("进食",     "enableEating",  false, null,          ""),
            f("随机走",   "enableLookAndWalk",false,null,         ""),
            f("可骑乘",   "isRideable",    false, null,          ""),
            f("可脑移动", "canBrainMove",  false, null,          ""),
            f("可栓绳",   "canBeLeashed",  false, null,          ""),
            f("雷劈",     "isStruckByLightning",false,null,      ""),
            f("结构生成", "isStructureSpawn",false,null,         "")
        )),
        new Group("显示", List.of(
            f("显示背包", "isShowBackpack",false, null,          ""),
            f("显示背饰", "isShowBackItem",false, null,          ""),
            f("聊天气泡", "isChatBubbleShow",false,null,         ""),
            f("音效频率", "soundFreq",     true,  null,          ""),
            f("发光",     "isGlowing",     false, null,          ""),
            f("隐形",     "isInvisible",   false, null,          ""),
            f("静音",     "isSilent",      false, null,          ""),
            f("背包类型", "backpackType",  false, null,          ""),
            f("背包格子", "backpackSlots", false, null,          ""),
            f("有背包",   "hasBackpack",   false, null,          ""),
            f("模型ID",   "modelId",       false, null,          ""),
            f("声音包ID", "soundPackId",   false, null,          "")
        )),
        new Group("抗性", List.of(
            f("无敌",     "isInvulnerable",false, null,          ""),
            f("着火中",   "isOnFire",      false, null,          ""),
            f("雨中",     "isInRain",      false, null,          ""),
            f("钓鱼竿",   "hasFishingHook",false, null,          "")
        )),
        new Group("日程", List.of(
            f("日程模式", "getSchedule",   false, null,          ""),
            f("当前活动", "scheduleDetail", false, null,          ""),
            f("已配置",   "isScheduleConfigured",false,null,     ""),
            f("工作中心", "restrictCenter", false, null,          ""),
            f("工作半径", "restrictRadius", false, null,          ""),
            f("搜索维度", "searchDimension",false, null,          ""),
            f("搜索半径", "searchRadius",   false, null,          ""),
            f("工作坐标", "workPos",       false, null,          ""),
            f("空闲坐标", "idlePos",       false, null,          ""),
            f("睡眠坐标", "sleepPos",      false, null,          "")
        )),
        new Group("自定义属性", List.of(
            f("物品速度",  "useItemSpeed", true,   null,         ""),
            f("弩速度",    "crossbowSpeed",true,   null,         ""),
            f("枪速度",    "gunSpeed",     true,   null,         ""),
            f("射击冷却",  "shootCooldown",true,   null,         ""),
            f("戟冷却",    "tridentCooldown",true, null,         ""),
            f("拾取范围",  "pickupRange",  true,   null,         ""),
            f("盾牌时间",  "shieldTick",   true,   null,         ""),
            f("饱食属性",  "maidHunger",   true,   null,         ""),
            f("最大生命",  "maxHealth",    true,   null,         ""),
            f("击退抗性",  "knockbackRes", true,   null,         ""),
            f("氧气值",    "airSupply",    false,  null,         ""),
            f("最大氧气",  "maxAir",       true,   null,         "")
        )),
        new Group("主人", List.of(
            f("主人名",   "ownerName",     false, null,          ""),
            f("主人UUID", "ownerUUID",     false, null,          ""),
            f("距离主人", "ownerDistance", false, null,          ""),
            f("主人生命", "ownerHealth",   false, null,          ""),
            f("主人手持", "ownerHoldingId",false, null,          ""),
            f("主人副手", "ownerOffhandId",false, null,          ""),
            f("主人护甲", "ownerArmor",    false, null,          ""),
            f("驯服物品", "tamedItem",     false, null,          ""),
            f("诱惑物品", "temptationItem",false, null,          ""),
            f("满好感度", "favorability",  false, "favorLevel",  "Lv.")
        ))
    );

    private final Screen parent;
    private final EntityMaid maid;
    private int groupIdx;
    private int pageIdx;
    private final Map<String, String> fieldCache = new LinkedHashMap<>();

    private int colW, startX, startY;
    private static final int FIELD_W = 80, FIELD_H = 20, GAP_X = 24, GAP_Y = 38;
    private final List<AbstractWidget> fieldWidgets = new ArrayList<>();

    public MaidEditorScreen(Screen parent, EntityMaid maid) {
        super(Component.literal("女仆编辑: " + maid.getName().getString()));
        this.parent = parent;
        this.maid = maid;
    }

    @Override
    protected void init() {
        startX = (width - COLS * (FIELD_W + GAP_X) + GAP_X) / 2;
        startY = 80;
        colW = FIELD_W + GAP_X;

        int navY = 35;
        addRenderableWidget(Button.builder(Component.literal("← 返回"), b -> onClose())
                .pos(5, navY - 10).size(46, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀ 上一组"), b -> prevGroup())
                .pos(width / 2 - 110, navY - 10).size(50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一组 ▶"), b -> nextGroup())
                .pos(width / 2 + 60, navY - 10).size(50, 20).build());

        var gn = GROUPS.stream().map(g -> g.name).toList();
        addRenderableWidget(CycleButton.builder((String s) -> Component.literal(s))
                .withValues(gn).withInitialValue(GROUPS.get(groupIdx).name)
                .create(width / 2 - 58, navY - 10, 116, 20, Component.literal(""),
                        (b, v) -> switchGroup(v)));

        addRenderableWidget(Button.builder(Component.literal("应用"),
                b -> applyChanges()).pos(width - 70, height - 26).size(50, 20).build());

        buildFields();
    }

    private void buildFields() {
        for (var w : fieldWidgets) removeWidget(w);
        fieldWidgets.clear();

        Group g = GROUPS.get(groupIdx);
        int totalPages = (g.fields.size() + PER_PAGE - 1) / PER_PAGE;
        if (pageIdx >= totalPages) pageIdx = 0;
        int start = pageIdx * PER_PAGE;
        int end = Math.min(start + PER_PAGE, g.fields.size());

        if (totalPages > 1) {
            int px = startX + COLS * colW / 2 - 30;
            addRenderableWidget(Button.builder(Component.literal("◀"),
                    __ -> { if (pageIdx > 0) { pageIdx--; buildFields(); } })
                    .pos(px, 65).size(16, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▶"),
                    __ -> { if (pageIdx < totalPages - 1) { pageIdx++; buildFields(); } })
                    .pos(px + 44, 65).size(16, 16).build());
        }

        for (int i = start; i < end; i++) {
            Field f = g.fields.get(i);
            int idx = i - start;
            int col = idx % COLS, row = idx / COLS;
            int x = startX + col * colW, y = startY + row * GAP_Y;
            String val = readField(f);

            AbstractWidget w;
            if (f.isBool) {
                boolean b = val.equals("true") || val.equals("✓");
                w = CycleButton.booleanBuilder(Component.literal("✓"), Component.literal("✗"))
                        .withInitialValue(b).create(x, y + 12, FIELD_W, FIELD_H,
                                Component.literal(f.label),
                                (cb, v) -> fieldCache.put(f.key, v ? "true" : "false"));
            } else {
                var edit = new EditBox(font, x, y + 12, FIELD_W, FIELD_H, Component.literal(f.key));
                edit.setValue(val);
                w = edit;
            }
            addRenderableWidget(w);
            fieldWidgets.add(w);
        }
    }

    private void rebuildFields() {
        for (var w : new ArrayList<>(children())) {
            if (w instanceof AbstractWidget aw && !(aw instanceof Button))
                removeWidget(aw);
        }
        fieldWidgets.clear();
        init();
    }

    private String readField(Field f) {
        var m = maid;
        return switch (f.key) {
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
            case "canBeLeashed" -> b(false);
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
            case "workPos", "idlePos", "sleepPos" -> fkey(f.key);
            case "isScheduleConfigured" -> b(MaidStateReader.isScheduleConfigured(m));
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
    private void applyChanges() { /* TODO */ }

    private void prevGroup() { groupIdx = (groupIdx - 1 + GROUPS.size()) % GROUPS.size(); pageIdx = 0; rebuildFields(); }
    private void nextGroup() { groupIdx = (groupIdx + 1) % GROUPS.size(); pageIdx = 0; rebuildFields(); }
    private void switchGroup(String name) {
        for (int i = 0; i < GROUPS.size(); i++)
            if (GROUPS.get(i).name.equals(name)) { groupIdx = i; break; }
        pageIdx = 0; rebuildFields();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        Group g2 = GROUPS.get(groupIdx);
        int totalPages = (g2.fields.size() + PER_PAGE - 1) / PER_PAGE;
        int start = pageIdx * PER_PAGE, end = Math.min(start + PER_PAGE, g2.fields.size());
        g.drawString(font, g2.name + " (" + (pageIdx + 1) + "/" + totalPages + ")", width / 2 + 64, 38, 0xCCCCCC);

        for (int i = start; i < end; i++) {
            Field f = g2.fields.get(i);
            int idx = i - start, col = idx % COLS, row = idx / COLS;
            int x = startX + col * colW, y = startY + row * GAP_Y;
            g.drawString(font, f.label, x, y, 0xCCCCCC);
            if (f.secKey != null) {
                String sec = readField(new Field("", f.secKey, false, null, ""));
                String txt = f.secPrefix + sec;
                g.drawString(font, txt, x + FIELD_W + 4, y + 16, 0x999999);
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    record Group(String name, List<Field> fields) {}
    record Field(String label, String key, boolean isBool, String secKey, String secPrefix) {}
    private static Field f(String l, String k, boolean editable, String secKey, String secPrefix) {
        return new Field(l, k, !editable, secKey, secPrefix);
    }
}
