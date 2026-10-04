package dev.flightblock;

import java.time.*;
import java.util.UUID;

/** Runnable without a server, EULA acceptance or third-party test frameworks. */
public final class RulesCheck {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("检查 " + checks + " 失败");
    }
    private static void rejects(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException e) { return; }
        throw new AssertionError("检查 " + checks + " 应拒绝非法输入");
    }
    public static void main(String[] args) {
        Config original = Config.defaults();
        check(original.first() == 300 && original.second() == 900 && original.third() == 21600);
        Config oldDurations = Config.parse(Config.DEFAULT_JSON.replace("{\"1\": 300, \"2\": 900, \"3\": 21600}",
            "{\"1\": 1800, \"2\": 7200, \"3\": 28800}"));
        check(oldDurations.first() == 1800 && oldDurations.second() == 7200 && oldDurations.third() == 28800);
        FlightBindings users = new FlightBindings();
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        check(users.owner("first") == null);
        check(users.bind(alice, "first"));
        check(alice.equals(users.owner("first")));
        check(!users.bind(bob, "first"));
        check(alice.equals(users.owner("first")));
        check(users.get(bob).isEmpty() && users.get(alice).equals(java.util.Set.of("first")));
        check(users.bind(UUID.fromString(alice.toString()), "first"));
        check(users.get(alice).size() == 1);
        check(users.bind(alice, "second"));
        check(users.bind(bob, "third"));
        users.clear(alice);
        check(users.owner("first") == null && users.owner("second") == null && bob.equals(users.owner("third")));
        check(users.get(alice).isEmpty() && users.get(bob).equals(java.util.Set.of("third")));
        check(users.bind(bob, "first") && users.bind(bob, "second"));
        users.clear(alice);
        check(!users.bind(alice, "first"));
        users.forget("first");
        check(users.owner("first") == null);
        check(!users.get(bob).contains("first") && users.get(bob).contains("second"));
        check(users.bind(alice, "first"));
        check(!users.bind(alice, "second"));
        users.forget("missing");
        users.forget("first");
        check(users.get(alice).isEmpty());
        users.clear(bob);
        check(users.bind(alice, "second") && users.bind(alice, "third"));
        check(!users.unbind(bob, "second"));
        check(users.get(alice).equals(java.util.Set.of("second", "third")));
        check(users.unbind(alice, "second"));
        check(users.owner("second") == null && alice.equals(users.owner("third")));
        check(users.get(alice).equals(java.util.Set.of("third")));
        check(users.bind(bob, "second"));
        check(bob.equals(users.owner("second")));
        check(!users.unbind(alice, "second"));
        check(bob.equals(users.owner("second")));
        check(users.get(bob).equals(java.util.Set.of("second")));
        check(!users.unbind(alice, "missing"));
        check(users.unbind(alice, "third"));
        check(users.get(alice).isEmpty() && users.get(bob).contains("second"));
        check(original.interval() == 5 && original.slowSeconds() == 10);
        // Reproduce the dangerous ordering: callback fires before its chunk future completes.
        var chunkReady = new java.util.concurrent.CompletableFuture<Boolean>();
        java.util.Queue<Runnable> chunkTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
        int[] processed = {0};
        chunkTasks.add(() -> {
            check(chunkReady.getNow(false));
            processed[0]++;
            chunkTasks.add(() -> processed[0]++);
        });
        check(processed[0] == 0 && !chunkReady.isDone());
        chunkReady.complete(true);
        FlightBlock.drainPending(chunkTasks);
        check(processed[0] == 1 && chunkTasks.size() == 1);
        FlightBlock.drainPending(chunkTasks);
        check(processed[0] == 2 && chunkTasks.isEmpty());
        chunkTasks.add(() -> { throw new AssertionError("Shutdown work must be discarded"); });
        chunkTasks.clear();
        FlightBlock.drainPending(chunkTasks);
        check(chunkTasks.isEmpty());
        check(original.radius(1) == 64 && original.radius(2) == 128 && original.radius(3) == 256);
        Config legacy = Config.parse(Config.DEFAULT_JSON.replace("  \"radiiBlocks\": {\"1\": 64, \"2\": 128, \"3\": 256},\n", ""));
        check(legacy.equals(original));
        Config custom = Config.parse(Config.DEFAULT_JSON.replace("{\"1\": 64, \"2\": 128, \"3\": 256}", "{\"1\": 10, \"2\": 20, \"3\": 30}"));
        check(custom.radius(1) == 10 && custom.radius(2) == 20 && custom.radius(3) == 30);
        rejects(() -> original.radius(0));
        rejects(() -> original.radius(4));
        for (Config config : new Config[]{original, custom}) {
            for (int level = 1; level <= 3; level++) {
                int radius = config.radius(level);
                for (double distance : new double[]{radius - 1, radius, radius + .01}) {
                    check(Rules.inRange("overworld", .5 + distance, .5, .5, "overworld", 0, 0, 0, radius) == (distance <= radius));
                    check(Rules.inRange("overworld", .5, .5 + distance, .5, "overworld", 0, 0, 0, radius) == (distance <= radius));
                }
                check(!Rules.inRange("nether", .5, .5, .5, "overworld", 0, 0, 0, radius));
                check(!Rules.inRange("overworld", .5 + radius, .5 + radius, .5, "overworld", 0, 0, 0, radius));
            }
        }
        Config maximum = Config.parse(Config.DEFAULT_JSON.replace("\"1\": 64", "\"1\": 4096"));
        check(Rules.inRange("overworld", 4096.5, .5, .5, "overworld", 0, 0, 0, maximum.radius(1)));
        check(!Rules.inRange("overworld", 4096.51, .5, .5, "overworld", 0, 0, 0, maximum.radius(1)));
        for (String distance : new String[]{"255", "256", "256.01"}) {
            double d = Double.parseDouble(distance);
            check(Rules.inRange("overworld", .5 + d, .5, .5, "overworld", 0, 0, 0, 256) == (d <= 256));
            check(Rules.inRange("overworld", .5, .5 + d, .5, "overworld", 0, 0, 0, 256) == (d <= 256));
        }
        check(!Rules.inRange("nether", .5, .5, .5, "overworld", 0, 0, 0, 256));
        check(!Rules.inRange("overworld", 200.5, 200.5, .5, "overworld", 0, 0, 0, 256));
        check(Rules.inRange("overworld", -99.5, -99.5, -99.5, "overworld", -100, -100, -100, 256));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        Rules.ItemState fresh = new Rules.ItemState(1, null, 0, 0);
        Rules.ItemState active = fresh.activate(id, original.first(), clock);
        check(active.expiresAt() == clock.millis() + 300_000L);
        check(active.activate(UUID.randomUUID(), 1, Clock.offset(clock, Duration.ofDays(2))) == active);
        check(!active.expired(active.expiresAt() - 1) && active.expired(active.expiresAt()));
        check(Rules.acceptsCenter(1, false, null));
        check(!Rules.acceptsCenter(1, true, fresh));
        check(!Rules.acceptsCenter(1, true, null));
        check(Rules.acceptsCenter(2, true, fresh));
        check(!Rules.acceptsCenter(2, false, null));
        check(!Rules.acceptsCenter(2, true, active));
        check(!Rules.acceptsCenter(3, true, fresh));
        check(Rules.acceptsCenter(3, true, new Rules.ItemState(2, null, 0, 0)));
        check(!Rules.acceptsCenter(2, true, new Rules.ItemState(3, null, 0, 0)));
        rejects(() -> new Rules.ItemState(0, null, 0, 0));
        rejects(() -> new Rules.ItemState(4, null, 0, 0));
        rejects(() -> new Rules.ItemState(1, id, 0, 0));
        rejects(() -> new Rules.ItemState(1, id, 123, 122));
        rejects(() -> new Rules.ItemState(1, null, 123, 124));
        rejects(() -> fresh.activate(id, 1, Clock.fixed(Instant.ofEpochMilli(Long.MAX_VALUE - 500), ZoneOffset.UTC)));
        Config next = Config.parse(Config.DEFAULT_JSON.replace("\"1\": 300", "\"1\": 10").replace("\"2\": 900", "\"2\": 20").replace("\"3\": 21600", "\"3\": 30"));
        check(fresh.activate(id, next.first(), clock).expiresAt() == clock.millis() + 10000);
        check(active.activate(id, next.first(), clock).expiresAt() == active.expiresAt());
        String[] invalid = {
            "{", Config.DEFAULT_JSON + " true", Config.DEFAULT_JSON.replace("\"1\": 300", "\"1\": 0"),
            Config.DEFAULT_JSON.replace("\"1\": 300", "\"1\": -1"), Config.DEFAULT_JSON.replace("\"1\": 300", "\"1\": 1.5"),
            Config.DEFAULT_JSON.replace("\"1\": 300", "\"1\": 1.0"), Config.DEFAULT_JSON.replace("\"1\": 300", "\"1\": \"300\""),
            Config.DEFAULT_JSON.replace("\"1\": 300", "\"1\": 31536001"), Config.DEFAULT_JSON.replace("\"1\": 300", "\"1\": 999999999999999999999999"),
            Config.DEFAULT_JSON.replace("\"1\": 300,", ""), Config.DEFAULT_JSON.replace("Asia/Shanghai", "Invalid/Zone"),
            Config.DEFAULT_JSON.replace("\"configVersion\": 1", "\"configVersion\": 2"),
            Config.DEFAULT_JSON.replace("\"flightCheckIntervalTicks\": 5", "\"flightCheckIntervalTicks\": 0"),
            Config.DEFAULT_JSON.replace("\"flightCheckIntervalTicks\": 5", "\"flightCheckIntervalTicks\": 21"),
            Config.DEFAULT_JSON.replace("\"slowFallingSeconds\": 10", "\"slowFallingSeconds\": 61"),
            Config.DEFAULT_JSON.replace("\"1\": 64", "\"1\": 0"),
            Config.DEFAULT_JSON.replace("\"1\": 64", "\"1\": -1"),
            Config.DEFAULT_JSON.replace("\"1\": 64", "\"1\": 1.5"),
            Config.DEFAULT_JSON.replace("\"1\": 64", "\"1\": \"64\""),
            Config.DEFAULT_JSON.replace("\"2\": 128", "\"2\": 4097"),
            Config.DEFAULT_JSON.replace("\"3\": 256", "\"3\": 999999999999999999999999"),
            Config.DEFAULT_JSON.replace("\"1\": 64,", ""),
            Config.DEFAULT_JSON.replace("{\"1\": 64, \"2\": 128, \"3\": 256}", "null"),
            Config.DEFAULT_JSON.replace("{\"1\": 64, \"2\": 128, \"3\": 256}", "[]"),
            Config.DEFAULT_JSON.replace("\"3\": 256", "\"3\": 256, \"4\": 512")
        };
        Config snapshot = original;
        for (String bad : invalid) {
            rejects(() -> Config.parse(bad));
            try { Config candidate = Config.parse(bad); snapshot = candidate; } catch (IllegalArgumentException expected) { }
            check(snapshot == original);
        }
        WorldState world = new WorldState();
        var anchor = new WorldState.Anchor("minecraft:overworld", net.minecraft.core.BlockPos.ZERO.asLong(), 1,
            id.toString(), active.activatedAt(), active.expiresAt(), false);
        check(anchor.contains("minecraft:overworld", 50.5, .5, .5, original.radius(anchor.level())));
        check(!anchor.contains("minecraft:overworld", 50.5, .5, .5, custom.radius(anchor.level())));
        check(anchor.contains("minecraft:overworld", 50.5, .5, .5, original.radius(anchor.level())));
        check(anchor.expiresAt() == active.expiresAt());
        check(anchor.margin(.5, .5, .5, 64) == 64);
        check(anchor.margin(64.5, .5, .5, 64) == 0);
        check(anchor.margin(.5, 65.5, .5, 64) == -1);
        check(anchor.margin(3.5, 4.5, .5, 64) == 59);
        var larger = new WorldState.Anchor("minecraft:overworld", net.minecraft.core.BlockPos.ZERO.asLong(), 2,
            UUID.randomUUID().toString(), active.activatedAt(), active.expiresAt(), false);
        var longer = new WorldState.Anchor("minecraft:overworld", net.minecraft.core.BlockPos.ZERO.asLong(), 2,
            UUID.randomUUID().toString(), active.activatedAt(), active.expiresAt() + 1000, false);
        var unactivated = new WorldState.Anchor("minecraft:overworld", net.minecraft.core.BlockPos.ZERO.asLong(), 3,
            UUID.randomUUID().toString(), 0, 0, false);
        var otherDimension = new WorldState.Anchor("minecraft:the_nether", net.minecraft.core.BlockPos.ZERO.asLong(), 3,
            UUID.randomUUID().toString(), active.activatedAt(), active.expiresAt(), false);
        WorldState previews = new WorldState();
        previews.put(unactivated);
        check(FlightBlock.showsPreviewParticles(unactivated, false, clock.millis()));
        var previewJson = WorldState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, previews).getOrThrow();
        WorldState previewRestored = WorldState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, previewJson).getOrThrow();
        check(FlightBlock.showsPreviewParticles(previewRestored.byId.get(unactivated.id()), false, clock.millis()));
        var idleActivated = unactivated.activate(original, clock);
        check(FlightBlock.showsPreviewParticles(idleActivated, false, clock.millis()));
        check(!FlightBlock.showsPreviewParticles(idleActivated, true, clock.millis()));
        check(!FlightBlock.showsPreviewParticles(idleActivated, false, idleActivated.expiresAt()));
        check(!FlightBlock.showsPreviewParticles(unactivated.pending(), false, clock.millis()));
        check(!FlightBlock.showsPreviewParticles(idleActivated.pending(), false, clock.millis()));
        check(!FlightBlock.showsPreviewParticles(unactivated, true, clock.millis()));
        FlightBindings visualUsers = new FlightBindings();
        check(!BlockGlow.shouldGlow(unactivated, true, clock.millis()));
        check(!BlockGlow.shouldGlow(idleActivated, visualUsers.owner(idleActivated.id()) != null, clock.millis()));
        check(visualUsers.bind(alice, idleActivated.id()));
        check(BlockGlow.shouldGlow(idleActivated, visualUsers.owner(idleActivated.id()) != null, clock.millis()));
        check(!visualUsers.unbind(bob, idleActivated.id()));
        check(BlockGlow.shouldGlow(idleActivated, visualUsers.owner(idleActivated.id()) != null, clock.millis()));
        check(visualUsers.unbind(alice, idleActivated.id()));
        check(!BlockGlow.shouldGlow(idleActivated, visualUsers.owner(idleActivated.id()) != null, clock.millis()));
        check(FlightBlock.showsPreviewParticles(idleActivated, false, clock.millis()));
        check(visualUsers.bind(bob, idleActivated.id()));
        check(BlockGlow.shouldGlow(idleActivated, visualUsers.owner(idleActivated.id()) != null, clock.millis()));
        visualUsers.clear(bob);
        check(!BlockGlow.shouldGlow(idleActivated, visualUsers.owner(idleActivated.id()) != null, clock.millis()));
        check(visualUsers.bind(alice, idleActivated.id()));
        visualUsers.forget(idleActivated.id());
        var replaced = new WorldState.Anchor(idleActivated.dimension(), new net.minecraft.core.BlockPos(10, 64, 10).asLong(),
            idleActivated.level(), idleActivated.id(), idleActivated.activatedAt(), idleActivated.expiresAt(), false);
        check(!BlockGlow.shouldGlow(replaced, visualUsers.owner(replaced.id()) != null, clock.millis()));
        check(FlightBlock.showsPreviewParticles(replaced, false, clock.millis()));
        check(replaced.activatedAt() == idleActivated.activatedAt() && replaced.expiresAt() == idleActivated.expiresAt());
        check(!BlockGlow.shouldGlow(replaced, true, replaced.expiresAt()));
        check(!BlockGlow.shouldGlow(replaced.pending(), true, clock.millis()));
        previews.put(idleActivated);
        var restoredActive = WorldState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
            WorldState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, previews).getOrThrow()).getOrThrow()
            .byId.get(idleActivated.id());
        check(!BlockGlow.shouldGlow(restoredActive, false, clock.millis()));
        check(FlightBlock.showsPreviewParticles(restoredActive, false, clock.millis()));
        check(restoredActive.expiresAt() == idleActivated.expiresAt());
        var candidates = java.util.List.of(anchor, larger, unactivated, otherDimension);
        check(FlightManager.selectAnchor(candidates, original, "minecraft:overworld", 70.5, .5, .5, clock.millis()) == larger);
        check(FlightManager.selectAnchor(candidates, original, "minecraft:overworld", 200.5, .5, .5, clock.millis()) == larger);
        check(FlightManager.selectAnchor(candidates, original, "minecraft:the_nether", .5, .5, .5, clock.millis()) == otherDimension);
        check(FlightManager.selectAnchor(candidates, original, "minecraft:the_end", .5, .5, .5, clock.millis()) == null);
        check(FlightManager.selectAnchor(candidates, original, "minecraft:overworld", .5, .5, .5, active.expiresAt()) == null);
        check(FlightManager.selectAnchor(java.util.List.of(larger.pending(), unactivated), original,
            "minecraft:overworld", .5, .5, .5, clock.millis()) == null);
        check(FlightManager.selectAnchor(java.util.List.of(larger, longer), original,
            "minecraft:overworld", .5, .5, .5, clock.millis()) == longer);
        Config reversed = Config.parse(Config.DEFAULT_JSON.replace("\"1\": 64", "\"1\": 256").replace("\"2\": 128", "\"2\": 1"));
        check(FlightManager.selectAnchor(candidates, reversed, "minecraft:overworld", .5, .5, .5, clock.millis()) == anchor);
        check(FlightManager.selectAnchor(java.util.List.of(), original, "minecraft:overworld", .5, .5, .5, clock.millis()) == null);
        world.put(anchor);
        check(world.isDirty());
        check(world.at("minecraft:overworld", net.minecraft.core.BlockPos.ZERO) == anchor);
        check(world.at("minecraft:the_nether", net.minecraft.core.BlockPos.ZERO) == null);
        check(world.chunk("minecraft:overworld", 0, 0).size() == 1);
        var encodedWorld = WorldState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, world).getOrThrow();
        WorldState restored = WorldState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encodedWorld).getOrThrow();
        check(!restored.isDirty());
        check(restored.byId.get(id.toString()).equals(anchor));
        check(restored.pollExpired(active.expiresAt() - 1) == null);
        check(restored.pollExpired(active.expiresAt()).equals(anchor));
        restored.put(anchor.pending());
        check(!restored.byId.get(id.toString()).valid(active.activatedAt() + 1));
        var pendingJson = WorldState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, restored).getOrThrow();
        WorldState pending = WorldState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, pendingJson).getOrThrow();
        check(pending.byId.get(id.toString()).pendingRemoval());
        check(pending.pollExpired(Long.MAX_VALUE) == null);
        pending.remove(pending.byId.get(id.toString()));
        check(pending.byId.isEmpty() && pending.chunk("minecraft:overworld", 0, 0).isEmpty());
        check(pending.isDirty());
        System.out.println("FLIGHTBLOCK_RULES_OK: " + checks + " checks passed.");
        RecipeMarkerCheck.run();
        PalCompatibilityCheck.run();
    }
}
