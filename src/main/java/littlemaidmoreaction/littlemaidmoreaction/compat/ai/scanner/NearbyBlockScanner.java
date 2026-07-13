package littlemaidmoreaction.littlemaidmoreaction.compat.ai.scanner;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 周围功能方块扫描器 (v10 AI context)。
 *
 * <p>以女仆为中心扫描方块实体，按类别分组，报告每类最近方块的距离和数量。
 * 最大范围 100 格，默认 32 格。分类名用 LLM 可理解的英文关键词。
 *
 * <h3>方块分类</h3>
 * 按功能性分为 storage/crafting/enchanting/altar/redstone/other 六类。
 * 每个分类内记录: block_id@nearest_distance (count)
 */
public final class NearbyBlockScanner {

    /** 最大搜索范围（方块） */
    public static final int MAX_RANGE = 100;
    /** 默认搜索范围 */
    public static final int DEFAULT_RANGE = 16;
    /** 默认垂直范围 (匹配 TLM IMaidTask.VERTICAL_SEARCH_RANGE = 4) */
    public static final int DEFAULT_VERTICAL = 4;

    private NearbyBlockScanner() {}

    /** 扫描结果：一个方块类型在范围内的统计 */
    public record BlockGroup(String category, String blockId, String displayName,
                              int count, double nearestDist, double farthestDist) {
        /** LLM 可读的格式化输出 */
        public String toContextLine() {
            return String.format("- %s (%s): %d blocks, nearest %.1f blocks away",
                displayName, blockId, count, nearestDist);
        }
    }

    /** 扫描结果汇总 */
    public record ScanResult(int totalBlocks, int range, List<BlockGroup> groups) {
        /** 格式化为 LLM 上下文字符串 */
        public String toContextString() {
            if (groups.isEmpty()) {
                return "No functional blocks found within " + range + " blocks.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Nearby functional blocks (%d blocks in %d block range):\n",
                totalBlocks, range));

            // 按分类分组输出
            Map<String, List<BlockGroup>> byCategory = new LinkedHashMap<>();
            for (BlockGroup g : groups) {
                byCategory.computeIfAbsent(g.category, k -> new ArrayList<>()).add(g);
            }
            for (var entry : byCategory.entrySet()) {
                sb.append("[").append(entry.getKey()).append("]\n");
                for (BlockGroup g : entry.getValue()) {
                    sb.append(g.toContextLine()).append("\n");
                }
            }
            return sb.toString().trim();
        }
    }

    /**
     * 扫描女仆周围的功能方块 (v12 P0修复: BlockState 替代 getBlockEntity)。
     *
     * <p>v12 修复: 不再依赖 {@code level.getBlockEntity()}，改为直接检查 {@code BlockState}。
     * 原因: 工作台/火把/制箭台等功能方块没有 BlockEntity，旧实现会漏掉。
     * 祭坛多方块结构按 {@code canPlaceItemPosList} 去重。
     *
     * @param maid  女仆
     * @param range 水平范围 (最大 100)
     * @param vertical 垂直范围
     * @return 按分类+距离排序的扫描结果
     */
    public static ScanResult scan(EntityMaid maid, int range, int vertical) {
        range = Math.min(range, MAX_RANGE);
        BlockPos center = maid.blockPosition();
        Level level = maid.level();

        // blockId → BlockPos 列表（非 BlockEntity，因为大多功能方块没有 TE）
        Map<String, List<BlockPos>> byId = new LinkedHashMap<>();
        // 祭坛多方块去重: canPlaceItemPosList hashCode → 已计数
        Set<Integer> seenAltarStructures = new HashSet<>();
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

        for (int y = -vertical; y <= vertical; y++) {
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    mPos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(mPos);
                    if (state.isAir()) continue;

                    Block block = state.getBlock();
                    ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(block);
                    if (rl == null) continue;

                    String blockId = rl.toString();
                    String category = classify(blockId);
                    if ("other".equals(category)) continue; // 非功能方块跳过

                    // 祭坛去重: 同多方块结构的 TileEntityAltar 共享 canPlaceItemPosList
                    if ("altar".equals(category)) {
                        BlockEntity be = level.getBlockEntity(mPos);
                        if (be instanceof TileEntityAltar altar && altar.isCanPlaceItem()) {
                            int hash = altar.getCanPlaceItemPosList().getData().hashCode();
                            if (!seenAltarStructures.add(hash)) continue;
                        } else {
                            continue; // 未配置的祭坛跳过
                        }
                    }

                    byId.computeIfAbsent(blockId, k -> new ArrayList<>()).add(mPos.immutable());
                }
            }
        }

        // 构建分组结果
        List<BlockGroup> groups = new ArrayList<>();
        int total = 0;
        for (var entry : byId.entrySet()) {
            String blockId = entry.getKey();
            List<BlockPos> list = entry.getValue();
            total += list.size();

            // 计算距离统计
            double nearest = Double.MAX_VALUE;
            double farthest = 0;
            for (BlockPos pos : list) {
                double dist = Math.sqrt(pos.distSqr(center));
                if (dist < nearest) nearest = dist;
                if (dist > farthest) farthest = dist;
            }

            String category = classify(blockId);
            String displayName = toDisplayName(blockId);
            groups.add(new BlockGroup(category, blockId, displayName,
                list.size(), nearest, farthest));
        }

        // 按分类 → 最近距离排序
        groups.sort(Comparator.comparing(BlockGroup::category)
            .thenComparingDouble(BlockGroup::nearestDist));

        return new ScanResult(total, range, groups);
    }

    /** 方块 ID → 人可读名称 */
    private static String toDisplayName(String blockId) {
        // 截取路径最后一段作为显示名
        int lastSlash = blockId.lastIndexOf('/');
        String name = lastSlash >= 0 ? blockId.substring(lastSlash + 1) : blockId;
        // 下划线 → 空格, 首字母大写
        return name.replace('_', ' ');
    }

    /** 方块 ID → 功能分类 */
    static String classify(String blockId) {
        String name = blockId.toLowerCase();

        // ── TLM 功能方块 — 精确匹配 ──
        if (name.startsWith("touhou_little_maid:")) {
            String tlmBlock = name.substring("touhou_little_maid:".length());
            return switch (tlmBlock) {
                case "altar" -> "altar";
                case "maid_bed" -> "bed";
                case "garage_kit", "statue", "model_switcher" -> "model";
                case "maid_beacon" -> "beacon";
                case "picnic_mat" -> "rest";
                case "shrine" -> "shrine";
                case "computer" -> "trade";
                case "bookshelf" -> "enchanting";
                case "snack_cabinet" -> "storage";
                case "scarecrow" -> "protection";
                default -> "tlm";
            };
        }

        // ── 原版/Minecraft 方块 — 子字符串匹配 ──

        // 光照方块 (火把/灯笼/荧石等 — ★ v12 新增)
        if (name.contains("torch") || name.contains("lantern")
            || name.contains("glowstone") || name.contains("sea_lantern")
            || name.contains("shroomlight") || name.contains("end_rod")
            || name.contains("campfire") || name.contains("candle")
            || name.contains("jack_o_lantern") || name.contains("lava_cauldron")) {
            return "light";
        }

        // 床（原版 + 其他 mod 的床）— ★ v12 P3 精准匹配，排除 bedrock
        if ((name.endsWith(":bed") || name.contains("_bed")) && !name.contains("bedrock")) {
            return "bed";
        }

        // 祭坛
        if (name.contains("altar")) return "altar";

        // 存储容器
        if (name.contains("chest") || name.contains("barrel")
            || name.contains("shulker_box") || name.contains("ender_chest")) {
            return "storage";
        }

        // 合成设备
        if (name.contains("crafting_table") || name.contains("furnace")
            || name.contains("blast_furnace") || name.contains("smoker")
            || name.contains("brewing_stand")) {
            return "crafting";
        }

        // 附魔/修理
        if (name.contains("enchanting_table") || name.contains("anvil")
            || name.contains("grindstone") || name.contains("smithing_table")) {
            return "enchanting";
        }

        // 红石设备
        if (name.contains("dispenser") || name.contains("dropper")
            || name.contains("hopper") || name.contains("observer")
            || name.contains("piston") || name.contains("comparator")
            || name.contains("repeater")) {
            return "redstone";
        }

        // 其他功能方块
        if (name.contains("beacon") || name.contains("conduit")
            || name.contains("lodestone") || name.contains("respawn_anchor")
            || name.contains("jukebox") || name.contains("note_block")
            || name.contains("bell") || name.contains("lectern")
            || name.contains("loom") || name.contains("stonecutter")
            || name.contains("cartography_table") || name.contains("fletching_table")
            || name.contains("cauldron") || name.contains("composter")) {
            return "utility";
        }

        return "other";
    }
}
