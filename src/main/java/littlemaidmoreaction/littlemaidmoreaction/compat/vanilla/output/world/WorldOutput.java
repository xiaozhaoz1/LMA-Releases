package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.world;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class WorldOutput {
    private WorldOutput() {}

    public static void breakBlock(Level world, BlockPos pos, boolean dropItems) { world.destroyBlock(pos, dropItems); }
    /** 设置方块（完整 BlockState + flags） */
    public static boolean setBlock(Level world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flags) {
        return world.setBlock(pos, state, flags);
    }
    public static void placeBlock(Level world, BlockPos pos, String blockId) {
        var rl = ResourceLocation.tryParse(blockId); if (rl == null) return;
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null) world.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }
    public static void spawnEntity(Level world, String entityId, BlockPos pos) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(entityId));
        if (type == null) return; var entity = type.create(world);
        if (entity != null) { entity.setPos(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5); world.addFreshEntity(entity); }
    }
    public static void summonLightning(Level world, BlockPos pos) { summonLightning(world, pos, false); }
    public static void summonLightning(Level world, BlockPos pos, boolean cosmetic) {
        var bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, world);
        bolt.setPos(pos.getX(), pos.getY(), pos.getZ()); if (cosmetic) bolt.setVisualOnly(true); world.addFreshEntity(bolt);
    }
    public static void createExplosion(Level world, BlockPos pos, float power, boolean fire, boolean destroy) { createExplosion(world, null, pos, power, fire, destroy); }
    public static void createExplosion(Level world, net.minecraft.world.entity.Entity source, BlockPos pos, float power, boolean fire, boolean destroy) { world.explode(source, pos.getX(), pos.getY(), pos.getZ(), power, fire, destroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE); }
    public static void setTime(ServerLevel world, int time) { world.setDayTime(time); }
    public static void setWeather(ServerLevel sw, String weather) { setWeather(sw, weather, 6000); }
    public static void setWeather(ServerLevel sw, String weather, int duration) { switch (weather) { case "clear" -> sw.setWeatherParameters(duration,0,false,false); case "rain" -> sw.setWeatherParameters(0,duration,true,false); case "thunder" -> sw.setWeatherParameters(0,duration,true,true); } }
    public static void executeCommand(ServerLevel world, String command) { world.getServer().getCommands().performPrefixedCommand(world.getServer().createCommandSourceStack(), command); }
    public static void executeCommand(ServerLevel world, EntityMaid maid, net.minecraft.world.entity.LivingEntity target, String command, String as, String at) {
        var server = world.getServer(); var executor = switch (as) { case "owner" -> maid.getOwner(); case "server" -> server; default -> maid; };
        if (executor == null) executor = server; var pos = "target".equals(at) && target != null ? target.position() : maid.position();
        server.getCommands().performPrefixedCommand(new net.minecraft.commands.CommandSourceStack(executor, pos, net.minecraft.world.phys.Vec2.ZERO, world, 2, as, Component.literal(as), server, null), command);
    }
    public static void sendChat(Player player, String message) { player.sendSystemMessage(Component.literal(message)); }
    public static void sendChatBroadcast(String message) { var s = ServerLifecycleHooks.getCurrentServer(); if (s != null) s.getPlayerList().broadcastSystemMessage(Component.literal(message), false); }
    public static void sendBubble(EntityMaid maid, String text) { maid.getChatBubbleManager().addTextChatBubble(text); }
    public static void sendBubble(EntityMaid maid, String text, int duration) {
        var bubble = TextChatBubbleData.create(duration, Component.literal(text), ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "textures/gui/chat_bubble/type_1.png"), 0);
        maid.getChatBubbleManager().addChatBubble(bubble);
    }
    public static boolean openGui(Level world, Player player, BlockPos pos) { var state = world.getBlockState(pos); var provider = state.getMenuProvider(world, pos); if (provider == null) return false; player.openMenu(provider); return true; }
    public static void interactBlock(Level world, BlockPos pos, Direction face) { Vec3 hitVec = new Vec3(pos.getX()+0.5+face.getStepX()*0.5, pos.getY()+0.5+face.getStepY()*0.5, pos.getZ()+0.5+face.getStepZ()*0.5); var hit = new BlockHitResult(hitVec, face, pos, false); world.getBlockState(pos).use(world, null, InteractionHand.MAIN_HAND, hit); }
    public static void breakBlockAt(Level world, BlockPos pos, boolean dropItems) { if (world.getBlockState(pos).isAir()) return; world.destroyBlock(pos, dropItems); }
    public static void spawnEntityAt(Level world, String entityId, BlockPos pos, int count, double spread, String nbtStr) {
        var rl = ResourceLocation.tryParse(entityId); if (rl == null) return;
        var type = ForgeRegistries.ENTITY_TYPES.getValue(rl); if (type == null) return;
        for (int i = 0; i < count; i++) { var entity = type.create(world); if (entity == null) continue;
            double ox = (world.random.nextDouble()-0.5)*spread*2, oz = (world.random.nextDouble()-0.5)*spread*2;
            entity.setPos(pos.getX()+0.5+ox, pos.getY()+0.5, pos.getZ()+0.5+oz);
            if (!nbtStr.isEmpty()) { try { var tag = net.minecraft.nbt.TagParser.parseTag(nbtStr); if (tag != null) entity.load(tag); } catch (Exception ex) {} }
            world.addFreshEntity(entity);
        }
    }
    public static boolean tradeWithVillager(Level level, BlockPos center, String profession, int range, Player player) {
        var aabb = new net.minecraft.world.phys.AABB(center).inflate(range, 4, range);
        var villagers = new java.util.ArrayList<>(level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, aabb, v -> v.isAlive() && !v.isTrading() && !v.isBaby()));
        if (!"any".equalsIgnoreCase(profession)) villagers.removeIf(v -> !v.getVillagerData().getProfession().name().toLowerCase().contains(profession.toLowerCase()));
        if (villagers.isEmpty()) return false;
        villagers.sort(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(player)));
        var v = villagers.get(0); v.openTradingScreen(player, v.getDisplayName(), v.getVillagerData().getLevel());
        return true;
    }
    // === Phase 11: TLM ChatBubbleManager API ===
    /** 发送气泡（去重: 相同 text 在 timeout tick 内不重复发送） */
    public static long sendBubbleIfTimeout(EntityMaid maid, String text, long timeout) {
        return maid.getChatBubbleManager().addTextChatBubbleIfTimeout(text, timeout);
    }
    /** 清除女仆头顶气泡 */
    public static void clearBubble(EntityMaid maid, long bubbleId) {
        maid.getChatBubbleManager().removeChatBubble(bubbleId);
    }
}
