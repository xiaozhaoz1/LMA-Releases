package com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower;
import com.github.xiaozhaoz1.littlemaidmoreaction.defense.garage.DefenseGarageKitBlockEntity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.SimpleBedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockModelLoader;
import com.github.tartaricacid.touhoulittlemaid.compat.ysm.YsmCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockModelLoader.STATUE_BASE;
import static com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil.clearMaidDataResidue;

/**
 * 防御塔方块实体渲染器 (v79.62.3) — 自绘版 TLM {@code TileEntityGarageKitRenderer}.
 *
 * <p>泛型绑定 {@link DefenseGarageKitBlockEntity} (继承 TLM TileEntityGarageKit, 数据字段一致:
 * getExtraData/getFacing), 修复 1.21.1 BER 泛型不匹配导致的手办不渲染。
 * 逻辑逐行复制 TLM (底座 + 女仆实体模型), 双平台签名差异 stonecutter 分支。
 */
public class DefenseTowerRenderer implements BlockEntityRenderer<DefenseGarageKitBlockEntity> {
//? if 1.20.1 {
    private static final ResourceLocation TEXTURE = new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/statue_base.png");
//?} else {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/statue_base.png");
//?}
    private final SimpleBedrockModel<Entity> BASE_MODEL;

    public DefenseTowerRenderer(BlockEntityRendererProvider.Context context) {
        BASE_MODEL = BedrockModelLoader.getModel(STATUE_BASE);
    }

    @Override
    public void render(DefenseGarageKitBlockEntity te, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn) {
        poseStack.pushPose();
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.translate(1, 1.5, 1);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        VertexConsumer buffer = bufferIn.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
//? if 1.20.1 {
        BASE_MODEL.renderToBuffer(poseStack, buffer, combinedLightIn, combinedOverlayIn, 1.0F, 1.0F, 1.0F, 1.0F);
//?} else {
        BASE_MODEL.renderToBuffer(poseStack, buffer, combinedLightIn, combinedOverlayIn);
//?}
        poseStack.popPose();

        CompoundTag data = te.getExtraData();
        Level world = Minecraft.getInstance().level;
        if (data.isEmpty() || world == null) {
            return;
        }

        EntityType.byString(data.getString("id")).ifPresent(type -> {
                    try {
                        renderEntity(te, poseStack, bufferIn, combinedLightIn, data, world, type);
                    } catch (ExecutionException e) {
                        TouhouLittleMaid.LOGGER.error("Failed to render defense tower entity", e);
                    }
                }
        );
    }

    private void renderEntity(DefenseGarageKitBlockEntity te, PoseStack poseStack, MultiBufferSource bufferIn,
                              int combinedLightIn, CompoundTag data, Level world, EntityType<?> type) throws ExecutionException {
        Entity entity;
        if (type.equals(InitEntities.MAID.get())) {
            long posId = te.getBlockPos().asLong();
            entity = EntityCacheUtil.STATUE_CACHE.get(posId, () -> new EntityMaid(world));
        } else {
            entity = EntityCacheUtil.ENTITY_CACHE.get(type, () -> {
                Entity e = type.create(world);
                return Objects.requireNonNullElseGet(e, () -> new EntityMaid(world));
            });
        }

        entity.load(data);
        if (entity instanceof EntityMaid maid) {
            clearMaidDataResidue(maid, true);
            maid.renderState = MaidRenderState.GARAGE_KIT;
            // ★★ v79.66c/d (用户两次洞察 ✓): 上面 `clearEquipmentData=true` 会**清空手办装备** —
            //   于是"拉弓"姿势手里**没有弓** ⇒ 看起来只是空手挥手 ✗
            //   但**不能一刀切画原版弓**: 实测不同模型弓的来源不同 —
            //     · `05_magical` 的 use_mainhand:bow 只动手臂骨 ⇒ 弓需**物品**渲染 ✓ 画
            //     · `21_saint`(圣女酒狐)/`22_elf` 同名动画动了 `ysmGlow_MagicBow`（**模型自带弓**）
            //       ⇒ 再画原版弓会**多一把** ✗ ⇒ 不画
            //   ⇒ 自动判别: 该模型"拉弓"动画是否含弓骨骼 (YsmAnimAvailability.hasOwnBowInAnimation) ✓
            //   配置覆盖: `defense_tower.show_weapon` = auto(默认) / always / never
            if (com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.ANIM_ENABLED.get()) {
                // ★ v79.66l (用户实测根因): 必须用**同步下来的武器镜像** — 客户端容器只有打开 GUI 时
                //   才经菜单同步拿到 ⇒ 直接用容器会"不打开界面就没弓 / 重进游戏又消失" ✗
                var weapon = te.getWeaponDisplay();
                if (weapon.isEmpty()) weapon = te.getAmmo().getItem(0);   // 兜底 (单机/同侧)
                if (!weapon.isEmpty() && shouldRenderWeapon(maid)) {
                    maid.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon.copy());
                }
            }
            // ★ 2026-09-20 实机定因 (用户"看不到拉弓动画"): TLM 原版手办对**非 YSM 模型**把 tickCount 恒置 0
            //   ⇒ GeckoLib 动画**永远停在第 0 帧** ⇒ 只有 YSM 模型能动 (参考模组也只支持 YSM, 同因) ✗
            //   ⇒ 我们的塔要"像女仆一样"播动作: 原生模型也必须推进 tickCount ✓
            //   配置闸门: `defense_tower.anim_enabled=false` ⇒ 退回 TLM 原样 (静态手办, 零行为变化) ✓
            if (YsmCompat.isInstalled() && maid.isYsmModel()) {
                maid.tickCount = (int) world.getGameTime();
            } else if (com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.ANIM_ENABLED.get()) {
                maid.tickCount = (int) world.getGameTime();
            } else {
                maid.tickCount = 0;
            }
        }

        poseStack.pushPose();
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.translate(1, 0.21328125, 1);
        switch (te.getFacing()) {
            case EAST:
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
                break;
            case WEST:
                poseStack.mulPose(Axis.YP.rotationDegrees(270));
                break;
            case SOUTH:
                break;
            case NORTH:
            default:
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
                break;
        }
        EntityRenderDispatcher render = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean isShowHitBox = render.shouldRenderHitBoxes();
        render.setRenderHitBoxes(false);
        render.render(entity, 0, 0, 0, 0, 0,
                poseStack, bufferIn, combinedLightIn);
        render.setRenderHitBoxes(isShowHitBox);
        poseStack.popPose();
    }

    /**
     * 是否把手里的原版武器画出来 (v79.66d, 用户裁定: 不同模型弓来源不同)。
     * <ul>
     *   <li>{@code show_weapon=always} ⇒ 画 (旧行为/强制)</li>
     *   <li>{@code show_weapon=never}  ⇒ 不画 (纯模型动画)</li>
     *   <li>{@code show_weapon=auto} (默认) ⇒ **模型自带弓就不画** (如圣女酒狐 {@code ysmGlow_MagicBow}),
     *       否则画 (如 {@code 05_magical}: 弓由物品提供) ✓ — 判定见
     *       {@link com.github.xiaozhaoz1.littlemaidmoreaction.defense.client.YsmAnimAvailability#hasOwnBowInAnimation}</li>
     * </ul>
     */
    private static boolean shouldRenderWeapon(EntityMaid maid) {
        String mode = com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.SHOW_WEAPON.get();
        if (mode == null || mode.isEmpty() || "always".equalsIgnoreCase(mode)) return true;
        if ("never".equalsIgnoreCase(mode)) return false;
        // auto: 仅 YSM 模型有"自带弓骨骼"概念; 原生 TLM 模型的弓一律是物品 ⇒ 画 ✓
        if (!maid.isYsmModel()) return true;
        String aimAnim = com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig
                .AIM_ANIM.get();
        return !com.github.xiaozhaoz1.littlemaidmoreaction.defense.client.YsmAnimAvailability
                .hasOwnBowInAnimation(maid.getYsmModelId(), aimAnim);
    }
}
