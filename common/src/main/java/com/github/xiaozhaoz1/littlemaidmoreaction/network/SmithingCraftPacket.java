package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
//? if 1.20.1 {
import net.minecraftforge.network.NetworkEvent;
//?}
//? if !1.20.1 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}
import java.util.UUID;
import java.util.function.Supplier;

//? if 1.20.1 {
public record SmithingCraftPacket(UUID maidUuid) {
//?} else {
public record SmithingCraftPacket(UUID maidUuid) implements CustomPacketPayload {
//?}
    public static void encode(SmithingCraftPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.maidUuid); }
    public static SmithingCraftPacket decode(FriendlyByteBuf buf) { return new SmithingCraftPacket(buf.readUUID()); }

//? if 1.20.1 {
    public static void handle(SmithingCraftPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> doCraft(pkt, ctx.get().getSender()));
        ctx.get().setPacketHandled(true);
    }
    public static void send(UUID maidUuid) { LmaNetwork.sender.sendToServer(new SmithingCraftPacket(maidUuid)); }
//?} else {
    public static final CustomPacketPayload.Type<SmithingCraftPacket> TYPE =
        new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "smithing_craft"));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<ByteBuf, SmithingCraftPacket> STREAM_CODEC =
        PacketCodecs.wrap(SmithingCraftPacket::encode, SmithingCraftPacket::decode);

    public static void handlePayload(SmithingCraftPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var p = ctx.player();
            if (p instanceof ServerPlayer sp) doCraft(pkt, sp);
        });
    }
    public static void send(UUID maidUuid) { LmaNetwork.sender.sendToServer(new SmithingCraftPacket(maidUuid)); }
//?}

    /**
     * v79.62.1 无模板锻造升级 — 遍历 RecipeManager 全部 smithing 配方 (数据驱动, 含所有 mod 模板配方),
     * <b>跳过模板需求</b> (本任务定位 = 不使用模板升级): 背包有 base + addition 即触发 transform 配方。
     * 纹章配方 (SmithingTrimRecipe) 跳过 (不打纹章); 原版下界合金升级走原版配方 (钻石+锭, 无模板)。
     */
    private static void doCraft(SmithingCraftPacket pkt, ServerPlayer player) {
        if (player == null) return;
        var level = player.serverLevel();
        if (!(level.getEntity(pkt.maidUuid) instanceof EntityMaid maid)) return;
        var inv = maid.getAvailableInv(true);
//? if 1.20.1 {
        for (SmithingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
//?} else {
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            SmithingRecipe recipe = holder.value();
//?}
            // 只处理升级配方 (transform), 跳过纹章配方 (trim)
            if (!(recipe instanceof SmithingTransformRecipe)) continue;
            int baseSlot = findSlot(inv, recipe::isBaseIngredient, -1);
            if (baseSlot < 0) continue;
            int addSlot = findSlot(inv, recipe::isAdditionIngredient, baseSlot);
            if (addSlot < 0) continue;
            ItemStack base = inv.getStackInSlot(baseSlot);
            ItemStack addition = inv.getStackInSlot(addSlot);
            ItemStack out = assemble(level, recipe, base, addition);
            if (out.isEmpty()) continue;
            // 应用: 消耗 1 材料 + base 替换为产物
            inv.setStackInSlot(baseSlot, out);
            ItemStack remain = addition.copy();
            remain.shrink(1);
            inv.setStackInSlot(addSlot, remain);
            return;
        }
    }

    /** 找背包第一个命中配方的槽位 (排除 skipSlot 防 base/addition 同槽) */
    private static int findSlot(
//? if 1.20.1 {
            net.minecraftforge.items.IItemHandler inv,
//?} else {
            net.neoforged.neoforge.items.IItemHandler inv,
//?}
            java.util.function.Predicate<ItemStack> test, int skipSlot) {
        for (int i = 0; i < inv.getSlots(); i++) {
            if (i == skipSlot) continue;
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && test.test(s)) return i;
        }
        return -1;
    }

    /** 无模板应用配方 — 1.20.1 用 SimpleContainer, 1.21.1 用 SmithingRecipeInput (template 空) */
    private static ItemStack assemble(ServerLevel level, SmithingRecipe recipe, ItemStack base, ItemStack addition) {
//? if 1.20.1 {
        net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(3);
        container.setItem(1, base);
        container.setItem(2, addition);
        return recipe.assemble(container, level.registryAccess());
//?} else {
        return recipe.assemble(
                new net.minecraft.world.item.crafting.SmithingRecipeInput(ItemStack.EMPTY, base, addition),
                level.registryAccess());
//?}
    }
}

