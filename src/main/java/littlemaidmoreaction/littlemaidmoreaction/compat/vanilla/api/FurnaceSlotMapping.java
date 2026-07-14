package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api;

/**
 * 熔炉栏位映射 — 不同模组熔炉可配置 input/fuel/output 槽位。
 * 原版: slot 0=input, 1=fuel, 2=output.
 */
public record FurnaceSlotMapping(int input, int fuel, int output) {
    public static final FurnaceSlotMapping VANILLA = new FurnaceSlotMapping(0, 1, 2);
}
