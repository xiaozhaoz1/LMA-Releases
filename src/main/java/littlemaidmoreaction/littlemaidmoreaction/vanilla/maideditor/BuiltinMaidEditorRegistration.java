package littlemaidmoreaction.littlemaidmoreaction.vanilla.maideditor;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitAttribute;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import littlemaidmoreaction.littlemaidmoreaction.api.maideditor.FieldType;
import littlemaidmoreaction.littlemaidmoreaction.api.maideditor.MaidEditorRegistry;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 内置女仆编辑器字段注册（9组）。
 * 在 VanillaCompat.init() 中调用。只执行一次。
 */
public final class BuiltinMaidEditorRegistration {
    private static volatile boolean registered;
    private BuiltinMaidEditorRegistration() {}

    public static void init() {
        if (registered) return;
        registered = true;
        registerBasic();
        registerCombat();
        registerAI();
        registerDisplay();
        registerResist();
        registerSchedule();
        registerCustomAttr();
        registerOwner();
        registerBaubles();
    }

    // === 基础 ===
    private static void registerBasic() {
        MaidEditorRegistry.addGroup("基础");
        f("基础", "生命",     "health",        FieldType.INT,   "maxHealth",   "/",
                m -> s((int) m.getHealth()),       (m, v) -> MaidStateWriter.setHealth(m, Float.parseFloat(v)));
        f("基础", "攻击",     "attack",        FieldType.FLOAT, null,          "",
                m -> f1(m.getAttributeValue(Attributes.ATTACK_DAMAGE)),
                (m, v) -> MaidStateWriter.setAttackDamage(m, Double.parseDouble(v)));
        f("基础", "移速",     "speed",         FieldType.FLOAT, null,          "",
                m -> f2(m.getAttributeValue(Attributes.MOVEMENT_SPEED)),
                (m, v) -> MaidStateWriter.setMovementSpeed(m, Double.parseDouble(v)));
        f("基础", "跟随距离", "followRange",   FieldType.FLOAT, null,          "",
                m -> f1(m.getAttributeValue(Attributes.FOLLOW_RANGE)),
                (m, v) -> MaidStateWriter.setFollowRange(m, Double.parseDouble(v)));
        f("基础", "经验",     "experience",    FieldType.INT,   null,          "",
                m -> s(m.getExperience()),         (m, v) -> MaidStateWriter.setExperience(m, Integer.parseInt(v)));
        f("基础", "好感度",   "favorability",  FieldType.INT,   "favorLevel",  "Lv.",
                m -> s(MaidStateReader.getFavorability(m)), (m, v) -> MaidStateWriter.setFavor(m, Integer.parseInt(v)));
        f("基础", "饱食度",   "hunger",        FieldType.INT,   null,          "",
                m -> s(m.getHunger()),             (m, v) -> MaidStateWriter.setHunger(m, Integer.parseInt(v)));
        f("基础", "幸运(只读)", "luck",       FieldType.INT,   null,          "",
                m -> s((int) m.getLuck()),         null);
        f("基础", "护甲(只读)", "armor",       FieldType.INT,   null,          "",
                m -> s(m.getArmorValue()),         null);
        f("基础", "护甲韧性",    "armorTough", FieldType.FLOAT, null,          "",
                m -> f1(m.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),
                (m, v) -> MaidStateWriter.setArmorToughness(m, Double.parseDouble(v)));
        b("基础", "幼年",     "isBaby",        MaidStateReader::isBaby,        MaidStateWriter::setBaby);
        b("基础", "坐着",     "isSitting",     m -> m.isMaidInSittingPose(),  MaidStateWriter::setSitting);
        b("基础", "回家模式", "isHomeMode",    EntityMaid::isHomeModeEnable,  MaidStateWriter::setHomeMode);
        b("基础", "已驯服(只读)", "isTamed",   MaidStateReader::isTamed,       null);
        b("基础", "着火(只读)",   "isOnFire",  EntityMaid::isOnFire,           null);
        b("基础", "游泳",         "isSwimming",EntityMaid::isSwimming,         MaidStateWriter::setSwimming);
        b("基础", "水中(只读)",   "isInWater", EntityMaid::isInWater,          null);
        b("基础", "冲刺",         "isSprinting",EntityMaid::isSprinting,       MaidStateWriter::setSprinting);
        b("基础", "潜行",         "isSneaking",EntityMaid::isCrouching,        MaidStateWriter::setSneaking);
        f("基础", "摔落距离(只读)","fallDistance",FieldType.FLOAT, null,       "",
                m -> f1(m.fallDistance),           null);
    }

    // === 战斗 ===
    private static void registerCombat() {
        MaidEditorRegistry.addGroup("战斗");
        f("战斗", "攻击力",   "attackDamage",  FieldType.FLOAT, null, "",
                m -> f1(m.getAttributeValue(Attributes.ATTACK_DAMAGE)),
                (m, v) -> MaidStateWriter.setAttackDamage(m, Double.parseDouble(v)));
        f("战斗", "攻速",     "attackSpeed",   FieldType.FLOAT, null, "",
                m -> f1(m.getAttributeValue(Attributes.ATTACK_SPEED)),
                (m, v) -> MaidStateWriter.setAttackSpeed(m, Double.parseDouble(v)));
        f("战斗", "击退抗性", "knockbackRes",  FieldType.FLOAT, null, "",
                m -> f1(m.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)),
                (m, v) -> MaidStateWriter.setKnockbackResistance(m, Double.parseDouble(v)));
        f("战斗", "近战范围(只读)","meleeRange",FieldType.FLOAT, null, "",
                m -> f1(MaidStateReader.getMeleeAttackRangeSqr(m, m)), null);
        b("战斗", "无敌",        "isInvulnerable",EntityMaid::getIsInvulnerable, MaidStateWriter::setInvulnerable);
        b("战斗", "可举盾(只读)","canUseShield",  MaidStateReader::canUseShield,  null);
        b("战斗", "格挡中(只读)","isBlocking",    EntityMaid::isBlocking,         null);
        b("战斗", "有武器(只读)","hasWeapon",     MaidStateReader::hasWeapon,     null);
        b("战斗", "有盾牌(只读)","hasShield",     MaidStateReader::hasShield,     null);
        b("战斗", "挥臂中(只读)","isSwinging",    EntityMaid::isSwingingArms,     null);
        b("战斗", "瞄准中",      "isAiming",      EntityMaid::isAiming,           MaidStateWriter::setAiming);
        f("战斗", "摔落距离(只读)","fallDistance",FieldType.FLOAT, null,          "",
                m -> f1(m.fallDistance),           null);
    }

    // === AI行为 ===
    private static void registerAI() {
        MaidEditorRegistry.addGroup("AI行为");
        b("AI行为", "拾取物品",  "isPickup",      EntityMaid::isPickup,       MaidStateWriter::setPickup);
        b("AI行为", "开门",      "isOpenDoor",    MaidStateReader::isOpenDoor, MaidStateWriter::setOpenDoor);
        b("AI行为", "栅栏门",    "isOpenFenceGate",MaidStateReader::isOpenFenceGate, MaidStateWriter::setOpenFenceGate);
        b("AI行为", "主动攀爬",  "isActiveClimbing",MaidStateReader::isActiveClimbing,MaidStateWriter::setActiveClimbing);
        b("AI行为", "恐慌(只读)",     "enablePanic",   MaidStateReader::isEnablePanic, null);
        b("AI行为", "进食(只读)",     "enableEating",  MaidStateReader::isEnableEating, null);
        b("AI行为", "随机走(只读)",   "enableLookAndWalk",MaidStateReader::isEnableLookAndRandomWalk, null);
        b("AI行为", "可骑乘",         "isRideable",    EntityMaid::isRideable,      MaidStateWriter::setRideable);
        b("AI行为", "可移动(只读)",   "canBrainMove",  MaidStateReader::canBrainMove, null);
        b("AI行为", "可栓绳(只读)",   "canBeLeashed",  m -> false,                  null);
        b("AI行为", "雷劈(只读)",     "isStruckByLightning",EntityMaid::isStruckByLightning, null);
    }

    // === 显示 ===
    private static void registerDisplay() {
        MaidEditorRegistry.addGroup("显示");
        b("显示", "显示背包",  "isShowBackpack",  MaidStateReader::isShowBackpack,  MaidStateWriter::setShowBackpack);
        b("显示", "显示背饰",  "isShowBackItem",  MaidStateReader::isShowBackItem,  MaidStateWriter::setShowBackItem);
        b("显示", "聊天气泡",  "isChatBubbleShow",MaidStateReader::isChatBubbleShow,MaidStateWriter::setChatBubbleShow);
        f("显示", "音效频率",  "soundFreq",       FieldType.FLOAT, null, "",
                m -> f2(MaidStateReader.getSoundFreq(m)), (m, v) -> MaidStateWriter.setSoundFreq(m, Float.parseFloat(v)));
        b("显示", "发光",      "isGlowing",       EntityMaid::isCurrentlyGlowing,   MaidStateWriter::setGlowing);
        b("显示", "隐形",      "isInvisible",     EntityMaid::isInvisible,          MaidStateWriter::setInvisible);
        b("显示", "静音",      "isSilent",        EntityMaid::isSilent,             MaidStateWriter::setSilent);
        f("显示", "背包类型(只读)","backpackType", FieldType.STRING, null, "",
                m -> MaidStateReader.getBackpackType(m), null);
        f("显示", "背包格子(只读)","backpackSlots",FieldType.INT, null, "",
                m -> s(MaidStateReader.getBackpackSlots(m)), null);
        b("显示", "有背包(只读)",  "hasBackpack", MaidStateReader::hasBackpack,      null);
        f("显示", "模型ID",    "modelId",         FieldType.STRING, null, "",
                m -> MaidStateReader.getModelId(m), (m, v) -> MaidStateWriter.setModel(m, v));
        f("显示", "声音包ID",  "soundPackId",     FieldType.STRING, null, "",
                m -> MaidStateReader.getSoundPackId(m), (m, v) -> MaidStateWriter.setSoundPack(m, v));
    }

    // === 抗性 ===
    private static void registerResist() {
        MaidEditorRegistry.addGroup("抗性");
        b("抗性", "火焰抗性",   "resistFire",       m -> hasResist(m, "fire"),    (m, v) -> setResist(m, "fire", v));
        b("抗性", "爆炸抗性",   "resistExplosion",  m -> hasResist(m, "explosion"),(m, v) -> setResist(m, "explosion", v));
        b("抗性", "弹射物抗性", "resistProjectile", m -> hasResist(m, "projectile"),(m, v) -> setResist(m, "projectile", v));
        b("抗性", "摔落抗性",   "resistFall",       m -> hasResist(m, "fall"),    (m, v) -> setResist(m, "fall", v));
        b("抗性", "溺水抗性",   "resistDrown",      m -> hasResist(m, "drown"),   (m, v) -> setResist(m, "drown", v));
        b("抗性", "魔法抗性",   "resistMagic",      m -> hasResist(m, "magic"),   (m, v) -> setResist(m, "magic", v));
        b("抗性", "击退抗性",   "resistKnockback",  m -> hasResist(m, "knockback"),(m, v) -> setResist(m, "knockback", v));
        b("抗性", "无敌",       "resistInvuln",     EntityMaid::getIsInvulnerable,MaidStateWriter::setInvulnerable);
    }
    private static boolean hasResist(EntityMaid m, String type) {
        return m.getPersistentData().getBoolean("lma_resist_" + type);
    }
    private static void setResist(EntityMaid m, String type, boolean v) {
        m.getPersistentData().putBoolean("lma_resist_" + type, v);
    }

    // === 日程 ===
    private static void registerSchedule() {
        MaidEditorRegistry.addGroup("日程");
        f("日程", "日程模式",   "getSchedule",    FieldType.STRING, null, "",
                m -> MaidStateReader.getSchedule(m),
                (m, v) -> MaidStateWriter.setSchedule(m, v));
        f("日程", "当前活动",   "scheduleDetail", FieldType.STRING, null, "",
                m -> MaidStateReader.getScheduleDetail(m), null);
        b("日程", "已配置",     "isScheduleConfigured",MaidStateReader::isScheduleConfigured, null);
        f("日程", "工作中心",   "restrictCenter", FieldType.STRING, null, "",
                m -> s(MaidStateReader.getRestrictCenter(m)), null);
        f("日程", "工作半径",   "restrictRadius", FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getRestrictRadius(m)),
                (m, v) -> MaidStateWriter.setRestrictRadius(m, Float.parseFloat(v)));
        f("日程", "搜索维度",   "searchDimension",FieldType.STRING, null, "",
                m -> MaidStateReader.getSearchDimension(m), null);
        f("日程", "搜索半径",   "searchRadius",   FieldType.FLOAT, null, "",
                m -> f1(MaidStateReader.getSearchRadius(m)), null);
        f("日程", "工作坐标",   "workPos",        FieldType.STRING, null, "",
                m -> s(MaidStateReader.getWorkPos(m)), null);
        f("日程", "空闲坐标",   "idlePos",        FieldType.STRING, null, "",
                m -> s(MaidStateReader.getIdlePos(m)), null);
        f("日程", "睡眠坐标",   "sleepPos",       FieldType.STRING, null, "",
                m -> s(MaidStateReader.getSleepPos(m)), null);
    }

    // === 自定义属性 ===
    private static void registerCustomAttr() {
        MaidEditorRegistry.addGroup("自定义属性");
        f("自定义属性", "物品速度", "useItemSpeed",  FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getUseItemSpeed(m)),
                (m, v) -> MaidStateWriter.setUseItemSpeed(m, Double.parseDouble(v)));
        f("自定义属性", "弩速度",   "crossbowSpeed", FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getCrossbowAttackSpeed(m)),
                (m, v) -> MaidStateWriter.setCrossbowAttackSpeed(m, Double.parseDouble(v)));
        f("自定义属性", "枪速度",   "gunSpeed",      FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getGunAttackSpeed(m)),
                (m, v) -> MaidStateWriter.setGunAttackSpeed(m, Double.parseDouble(v)));
        f("自定义属性", "射击冷却", "shootCooldown", FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getShootCooldown(m)),
                (m, v) -> MaidStateWriter.setShootCooldown(m, Double.parseDouble(v)));
        f("自定义属性", "戟冷却",   "tridentCooldown",FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getTridentCooldown(m)),
                (m, v) -> MaidStateWriter.setTridentCooldown(m, Double.parseDouble(v)));
        f("自定义属性", "拾取范围", "pickupRange",   FieldType.FLOAT, null, "",
                m -> f1(MaidStateReader.getPickupRange(m)),
                (m, v) -> MaidStateWriter.setPickupRange(m, Double.parseDouble(v)));
        f("自定义属性", "盾牌时间", "shieldTick",    FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getPassiveUseShieldTick(m)),
                (m, v) -> MaidStateWriter.setPassiveUseShieldTick(m, Double.parseDouble(v)));
        f("自定义属性", "饱食属性", "maidHunger",    FieldType.FLOAT, null, "",
                m -> f0(m.getAttributeValue(InitAttribute.MAID_HUNGER.get())), null);
        f("自定义属性", "最大生命", "maxHealth",     FieldType.FLOAT, null, "",
                m -> f1(m.getMaxHealth()),
                (m, v) -> MaidStateWriter.setMaxHealth(m, Double.parseDouble(v)));
        f("自定义属性", "击退抗性", "knockbackRes",  FieldType.FLOAT, null, "",
                m -> f1(m.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)),
                (m, v) -> MaidStateWriter.setKnockbackResistance(m, Double.parseDouble(v)));
        f("自定义属性", "氧气值",   "airSupply",     FieldType.INT, null, "",
                m -> s(m.getAirSupply()), null);
        f("自定义属性", "最大氧气", "maxAir",        FieldType.INT, null, "",
                m -> s(m.getMaxAirSupply()), null);
    }

    // === 主人 ===
    private static void registerOwner() {
        MaidEditorRegistry.addGroup("主人");
        f("主人", "主人名",     "ownerName",      FieldType.STRING, null, "",
                m -> MaidStateReader.getOwnerName(m), null);
        f("主人", "主人UUID",   "ownerUUID",      FieldType.STRING, null, "",
                m -> { var u = MaidStateReader.getOwnerUUID(m); return u != null ? u.toString() : "none"; }, null);
        f("主人", "距离主人",   "ownerDistance",  FieldType.FLOAT, null, "",
                m -> f1(MaidStateReader.getOwnerDistance(m)), null);
        f("主人", "主人生命",   "ownerHealth",    FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getOwnerHealth(m)), null);
        f("主人", "主人手持",   "ownerHoldingId", FieldType.STRING, null, "",
                m -> MaidStateReader.getOwnerHoldingItemId(m), null);
        f("主人", "主人副手",   "ownerOffhandId", FieldType.STRING, null, "",
                m -> MaidStateReader.getOwnerOffhandId(m), null);
        f("主人", "主人护甲",   "ownerArmor",     FieldType.FLOAT, null, "",
                m -> f0(MaidStateReader.getOwnerArmor(m)), null);
        f("主人", "驯服物品",   "tamedItem",      FieldType.STRING, null, "",
                m -> { var s = MaidStateReader.getTamedItem(m); return s.isEmpty() ? "none" : ForgeRegistries.ITEMS.getKey(s.getItem()).toString(); }, null);
        f("主人", "诱惑物品",   "temptationItem", FieldType.STRING, null, "",
                m -> { var s = MaidStateReader.getTemptationItem(m); return s.isEmpty() ? "none" : ForgeRegistries.ITEMS.getKey(s.getItem()).toString(); }, null);
    }

    // === 饰品 ===
    private static void registerBaubles() {
        MaidEditorRegistry.addGroup("饰品");
        bo("饰品", "水下呼吸饰品",  "hasDrownBauble",     m -> hasBauble(m, InitItems.DROWN_PROTECT_BAUBLE.get()));
        bo("饰品", "爆炸保护饰品",  "hasExplosionBauble", m -> hasBauble(m, InitItems.EXPLOSION_PROTECT_BAUBLE.get()));
        bo("饰品", "额外生命饰品",  "hasExtraLifeBauble", m -> hasBauble(m, InitItems.ULTRAMARINE_ORB_ELIXIR.get()));
        bo("饰品", "摔落保护饰品",  "hasFallBauble",      m -> hasBauble(m, InitItems.FALL_PROTECT_BAUBLE.get()));
        bo("饰品", "火焰抗性饰品",  "hasFireBauble",      m -> hasBauble(m, InitItems.FIRE_PROTECT_BAUBLE.get()));
        bo("饰品", "物品磁铁饰品",  "hasItemMagnet",      m -> hasBauble(m, InitItems.ITEM_MAGNET_BAUBLE.get()));
        bo("饰品", "魔法保护饰品",  "hasMagicBauble",     m -> hasBauble(m, InitItems.MAGIC_PROTECT_BAUBLE.get()));
        bo("饰品", "静音饰品",      "hasMuteBauble",      m -> hasBauble(m, InitItems.MUTE_BAUBLE.get()));
        bo("饰品", "弹射物闪避饰品","hasNimbleFabric",    m -> hasBauble(m, InitItems.NIMBLE_FABRIC.get()));
        bo("饰品", "弹射物保护饰品","hasProjectileBauble",m -> hasBauble(m, InitItems.PROJECTILE_PROTECT_BAUBLE.get()));
        bo("饰品", "不死图腾",      "hasTotem",           m -> hasBauble(m, net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        bo("饰品", "无线IO",        "hasWirelessIO",      m -> hasBauble(m, InitItems.WIRELESS_IO.get()));
    }
    private static boolean hasBauble(EntityMaid m, Item item) {
        var b = m.getMaidBauble();
        for (int i = 0; i < b.getSlots(); i++) {
            if (b.getStackInSlot(i).is(item)) return true;
        }
        return false;
    }

    // === 便捷方法 ===
    private static String s(Object v) { return String.valueOf(v); }
    private static String s(BlockPos v) { return v != null ? v.toShortString() : "-"; }
    private static String f0(double v) { return String.format("%.0f", v); }
    private static String f1(double v) { return String.format("%.1f", v); }
    private static String f2(double v) { return String.format("%.2f", v); }

    private static void f(String g, String label, String key, FieldType type,
                          String secKey, String secPrefix,
                          java.util.function.Function<EntityMaid, String> reader,
                          java.util.function.BiConsumer<EntityMaid, String> writer) {
        MaidEditorRegistry.addField(g, label, key, type, secKey, secPrefix, reader,
                writer != null ? writer : (m, v) -> {});
    }

    private static void b(String g, String label, String key,
                          java.util.function.Function<EntityMaid, Boolean> reader,
                          java.util.function.BiConsumer<EntityMaid, Boolean> writer) {
        MaidEditorRegistry.addField(g, label, key, FieldType.BOOL, null, "",
                m -> reader.apply(m) ? "true" : "false",
                writer != null ? (m, v) -> writer.accept(m, "true".equals(v)) : (m, v) -> {});
    }

    private static void bo(String g, String label, String key,
                           java.util.function.Function<EntityMaid, Boolean> reader) {
        MaidEditorRegistry.addField(g, label, key, FieldType.BOOL, null, "",
                m -> reader.apply(m) ? "true" : "false",
                (m, v) -> {}); // 饰品只读
    }
}
