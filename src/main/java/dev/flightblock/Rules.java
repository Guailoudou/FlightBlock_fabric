package dev.flightblock;

import java.time.Clock;
import java.util.UUID;

public final class Rules {
    private Rules() {}
    public static boolean inRange(String dimension, double x, double y, double z,
                                  String anchorDimension, int bx, int by, int bz, int radius) {
        double dx = x - (bx + .5), dy = y - (by + .5), dz = z - (bz + .5);
        return dimension.equals(anchorDimension) && dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }
    public record ItemState(int level, UUID id, long activatedAt, long expiresAt) {
        public ItemState {
            if (level < 1 || level > 3) throw new IllegalArgumentException("level 必须为 1～3");
            if (!(activatedAt == 0 && expiresAt == 0 && id == null)
                && (id == null || activatedAt <= 0 || expiresAt <= activatedAt
                || expiresAt - activatedAt > 31_536_000_000L))
                throw new IllegalArgumentException("实例标识或到期时间无效");
        }
        public boolean activated() { return activatedAt != 0; }
        public boolean expired(long now) { return activated() && expiresAt <= now; }
        public ItemState activate(UUID instanceId, long seconds, Clock clock) {
            if (activated()) return this;
            long now = clock.millis();
            return new ItemState(level, instanceId, now, Math.addExact(now, Math.multiplyExact(seconds, 1000L)));
        }
    }
    public static boolean acceptsCenter(int outputLevel, boolean marked, ItemState center) {
        return outputLevel == 1 ? !marked
            : center != null && !center.activated() && center.level() == outputLevel - 1;
    }
}
