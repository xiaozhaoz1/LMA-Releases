package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api;

/**
 * @deprecated 使用 {@link SlotLayout} 替代。v30 后 FurnaceSlotMapping 委托到 SlotLayout。
 */
@Deprecated
public record FurnaceSlotMapping(int input, int fuel, int output) {
    public static final FurnaceSlotMapping VANILLA = new FurnaceSlotMapping(0, 1, 2);

    public SlotLayout toSlotLayout() {
        return SlotLayout.builder()
            .role("input", input).role("fuel", fuel).role("output", output).build();
    }
}
