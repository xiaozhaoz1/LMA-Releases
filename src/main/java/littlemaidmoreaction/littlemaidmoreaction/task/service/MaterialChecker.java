package littlemaidmoreaction.littlemaidmoreaction.task.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialChecker {
    private static final System.Logger LOG = System.getLogger("LMA-V16-MaterialChecker");

    private MaterialChecker() {}

    public static <T> MaterialReport<T> check(Map<T, Integer> required,
                                               Map<T, Integer> available) {
        LOG.log(System.Logger.Level.INFO, "[V16] [MaterialChecker] checking: {0} required items vs {1} available",
                required.size(), available.size());
        Map<T, Integer> missing = new LinkedHashMap<>();
        for (var entry : required.entrySet()) {
            int have = available.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }
        boolean sufficient = missing.isEmpty();
        LOG.log(System.Logger.Level.INFO, "[V16] [MaterialChecker] result: {0}", sufficient ? "SUFFICIENT" : "INSUFFICIENT");
        if (sufficient) {
            return MaterialReport.ofSufficient();
        }
        return MaterialReport.ofMissing(missing);
    }
}
