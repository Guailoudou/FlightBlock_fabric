package dev.flightblock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.*;
import java.util.*;

public final class WorldState extends SavedData {
    public record Anchor(String dimension, long position, int level, String id, long activatedAt,
                         long expiresAt, boolean pendingRemoval) {
        public static final Codec<Anchor> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(Anchor::dimension),
            Codec.LONG.fieldOf("position").forGetter(Anchor::position),
            Codec.intRange(1, 3).fieldOf("level").forGetter(Anchor::level),
            Codec.STRING.fieldOf("id").forGetter(Anchor::id),
            Codec.LONG.fieldOf("activatedAt").forGetter(Anchor::activatedAt),
            Codec.LONG.fieldOf("expiresAt").forGetter(Anchor::expiresAt),
            Codec.BOOL.fieldOf("pendingRemoval").forGetter(Anchor::pendingRemoval)
        ).apply(i, Anchor::new));
        public Anchor {
            Identifier.parse(dimension);
            UUID.fromString(id);
            if (activatedAt == 0 && expiresAt == 0) {
                new Rules.ItemState(level, null, 0, 0);
            } else new Rules.ItemState(level, UUID.fromString(id), activatedAt, expiresAt);
        }
        public BlockPos pos() { return BlockPos.of(position); }
        public Rules.ItemState item() {
            return new Rules.ItemState(level, activatedAt == 0 ? null : UUID.fromString(id), activatedAt, expiresAt);
        }
        public boolean valid(long now) { return !pendingRemoval && activatedAt > 0 && expiresAt > now; }
        public Anchor activate(Config config, java.time.Clock clock) {
            Rules.ItemState s = item().activate(UUID.fromString(id), config.seconds(level), clock);
            return new Anchor(dimension, position, level, id, s.activatedAt(), s.expiresAt(), false);
        }
        public Anchor pending() { return new Anchor(dimension, position, level, id, activatedAt, expiresAt, true); }
        public boolean contains(String dim, double x, double y, double z, int radius) {
            BlockPos p = pos();
            return Rules.inRange(dim, x, y, z, dimension, p.getX(), p.getY(), p.getZ(), radius);
        }
        public double margin(double x, double y, double z, int radius) {
            BlockPos p = pos();
            double dx = x - (p.getX() + .5), dy = y - (p.getY() + .5), dz = z - (p.getZ() + .5);
            return radius - Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
    public static final Codec<WorldState> CODEC = Anchor.CODEC.listOf().fieldOf("anchors").codec()
        .xmap(WorldState::new, s -> new ArrayList<>(s.byId.values()));
    public static final SavedDataType<WorldState> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("flightblock", "anchors"), WorldState::new, CODEC, null);
    public final Map<String, Anchor> byId = new HashMap<>();
    private final Map<String, String> byPosition = new HashMap<>();
    private final Map<String, Set<String>> byChunk = new HashMap<>();
    private final PriorityQueue<Anchor> deadlines = new PriorityQueue<>(Comparator.comparingLong(Anchor::expiresAt));
    private final Set<String> inactive = new HashSet<>();
    public WorldState() {}
    private WorldState(List<Anchor> anchors) {
        for (Anchor a : anchors) {
            if (byId.containsKey(a.id()) || at(a.dimension(), a.pos()) != null)
                throw new IllegalArgumentException("世界记录包含重复实例或位置");
            put(a);
        }
        setDirty(false);
    }
    private String key(String dim, long pos) { return dim + ":" + pos; }
    private String chunkKey(String dim, BlockPos pos) { return dim + ":" + (pos.getX() >> 4) + ":" + (pos.getZ() >> 4); }
    public Anchor at(String dim, BlockPos pos) { return byId.get(byPosition.get(key(dim, pos.asLong()))); }
    public void put(Anchor a) {
        byId.put(a.id(), a);
        byPosition.put(key(a.dimension(), a.position()), a.id());
        byChunk.computeIfAbsent(chunkKey(a.dimension(), a.pos()), k -> new HashSet<>()).add(a.id());
        if (a.activatedAt() > 0 && !a.pendingRemoval()) deadlines.add(a);
        if (a.activatedAt() == 0 && !a.pendingRemoval()) inactive.add(a.id());
        else inactive.remove(a.id());
        setDirty();
    }
    public void remove(Anchor a) {
        if (!byId.remove(a.id(), a)) return;
        inactive.remove(a.id());
        byPosition.remove(key(a.dimension(), a.position()), a.id());
        Set<String> chunk = byChunk.get(chunkKey(a.dimension(), a.pos()));
        if (chunk != null) {
            chunk.remove(a.id());
            if (chunk.isEmpty()) byChunk.remove(chunkKey(a.dimension(), a.pos()));
        }
        setDirty();
    }
    public List<Anchor> chunk(String dim, int x, int z) {
        Set<String> ids = byChunk.getOrDefault(dim + ":" + x + ":" + z, Set.of());
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
    public List<Anchor> inactive() { return inactive.stream().map(byId::get).filter(Objects::nonNull).toList(); }
    public Anchor pollExpired(long now) {
        while (!deadlines.isEmpty() && deadlines.peek().expiresAt() <= now) {
            Anchor a = deadlines.remove();
            if (byId.get(a.id()) == a) return a;
        }
        return null;
    }
}
