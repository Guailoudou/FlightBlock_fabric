package dev.flightblock;

import io.github.ladysnake.pal.Pal;
import io.github.ladysnake.pal.SimpleAbilityTracker;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

/** Checks real PAL source ownership and save cleanup without starting Minecraft. */
final class PalCompatibilityCheck {
    static void run() {
        var tracker = new SimpleAbilityTracker(null, null) {
            @Override protected void updateState(boolean enabled) { }
        };
        var other = Pal.getAbilitySource("flightblock_test", "other_mod");
        tracker.addSource(other);
        tracker.addSource(FlightManager.FLIGHT);
        FlightManager.withoutGrant(tracker, () -> {
            assert !tracker.isGrantedBy(FlightManager.FLIGHT);
            assert tracker.isGrantedBy(other) && tracker.isEnabled();
            var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
            tracker.save(output);
            var saved = output.buildResult().getListOrEmpty("ability_sources");
            assert saved.size() == 1 && saved.get(0).equals(StringTag.valueOf(other.getId().toString()));
        });
        assert tracker.isGrantedBy(FlightManager.FLIGHT) && tracker.isGrantedBy(other);
        try {
            FlightManager.withoutGrant(tracker, () -> { throw new IllegalStateException("save failed"); });
            throw new AssertionError("Save failure must propagate");
        } catch (IllegalStateException expected) {
            assert tracker.isGrantedBy(FlightManager.FLIGHT) && tracker.isGrantedBy(other);
        }
        tracker.removeSource(FlightManager.FLIGHT);
        assert tracker.isEnabled() && tracker.isGrantedBy(other);
        FlightManager.withoutGrant(tracker, () -> { assert tracker.isGrantedBy(other); });
        assert !tracker.isGrantedBy(FlightManager.FLIGHT);
        tracker.removeSource(other);
        tracker.addSource(FlightManager.FLIGHT);
        FlightManager.withoutGrant(tracker, () -> { assert !tracker.isEnabled(); });
        assert tracker.isEnabled();
        tracker.removeSource(FlightManager.FLIGHT);
        assert !tracker.isEnabled();
        System.out.println("FLIGHTBLOCK_PAL_OK: source ownership, save serialization and failure recovery passed.");
    }
}
