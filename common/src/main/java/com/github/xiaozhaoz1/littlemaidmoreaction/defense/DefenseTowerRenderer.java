package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

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
            if (YsmCompat.isInstalled() && maid.isYsmModel()) {
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
}
