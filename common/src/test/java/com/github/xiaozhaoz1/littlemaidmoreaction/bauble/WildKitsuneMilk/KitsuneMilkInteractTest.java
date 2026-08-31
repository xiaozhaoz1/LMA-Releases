package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KitsuneMilkInteract#decide(UUID, UUID, boolean, boolean)} 纯函数测试 (v79.6x)。
 * 三态判定: 驯服主人 / 驯服别人 / 未驯服, 叠加主/副开关。
 */
class KitsuneMilkInteractTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    @DisplayName("主开关关 → 一律不能挤")
    void mainOff_never() {
        assertNull(KitsuneMilkInteract.decide(OWNER, PLAYER, false, true));
        assertNull(KitsuneMilkInteract.decide(null, PLAYER, false, true));
    }

    @Test
    @DisplayName("已驯服 + 主人 → 酒狐奶桶")
    void tamedOwner_tamedMilk() {
        // 主人 = ownerUuid == playerUuid
        assertEquals(MilkKind.TAMED, KitsuneMilkInteract.decide(PLAYER, PLAYER, true, true));
        assertEquals(MilkKind.TAMED, KitsuneMilkInteract.decide(PLAYER, PLAYER, true, false));
    }

    @Test
    @DisplayName("已驯服 + 别人 → 不能挤")
    void tamedOther_none() {
        assertNull(KitsuneMilkInteract.decide(OTHER, PLAYER, true, true));
    }

    @Test
    @DisplayName("未驯服 + 副开关 → 野生奶 / 奶桶")
    void wild_extraToggle() {
        assertEquals(MilkKind.WILD, KitsuneMilkInteract.decide(null, PLAYER, true, true));
        assertEquals(MilkKind.TAMED, KitsuneMilkInteract.decide(null, PLAYER, true, false));
    }

    @Test
    @DisplayName("玩家 null → 不能挤 (防御)")
    void nullPlayer_none() {
        assertNull(KitsuneMilkInteract.decide(null, null, true, true));
        assertNull(KitsuneMilkInteract.decide(OWNER, null, true, true));
    }
}
