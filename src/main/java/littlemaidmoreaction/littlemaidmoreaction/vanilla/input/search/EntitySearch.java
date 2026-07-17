package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.search;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.function.Predicate;

public final class EntitySearch {
    private EntitySearch() {}

    public record Match(Entity entity, double distSqr) {}

    public static List<Match> findEntities(
            Level level, BlockPos center, int horiz, int vert,
            Predicate<Entity> filter) {
        AABB aabb = new AABB(center).inflate(horiz, vert, horiz);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, aabb, filter);

        List<Match> results = new ArrayList<>(entities.size());
        for (Entity e : entities) {
            results.add(new Match(e, e.blockPosition().distSqr(center)));
        }
        results.sort(Comparator.comparingDouble(Match::distSqr));
        return results;
    }

    public static boolean exists(Level level, BlockPos center, int horiz, int vert,
                                 Predicate<Entity> filter) {
        AABB aabb = new AABB(center).inflate(horiz, vert, horiz);
        return !level.getEntitiesOfClass(Entity.class, aabb, filter).isEmpty();
    }
}
