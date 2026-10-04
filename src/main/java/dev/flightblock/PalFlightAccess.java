package dev.flightblock;

import io.github.ladysnake.pal.AbilitySource;
import io.github.ladysnake.pal.AbilityTracker;
import io.github.ladysnake.pal.Pal;
import io.github.ladysnake.pal.VanillaAbilities;
import net.minecraft.server.level.ServerPlayer;

/** PAL is embedded in the Fabric/Quilt distribution. */
public final class PalFlightAccess implements FlightAccess {
    static final AbilitySource FLIGHT = Pal.getAbilitySource("flightblock", "flight");
    @Override public void grant(ServerPlayer player) { FLIGHT.grantTo(player, VanillaAbilities.ALLOW_FLYING); }
    @Override public boolean revoke(ServerPlayer player) {
        if (!FLIGHT.grants(player, VanillaAbilities.ALLOW_FLYING)) return false;
        FLIGHT.revokeFrom(player, VanillaAbilities.ALLOW_FLYING);
        return true;
    }
    static void withoutGrant(AbilityTracker tracker, Runnable save) {
        boolean owned = tracker.isGrantedBy(FLIGHT);
        if (owned) tracker.removeSource(FLIGHT);
        try { save.run(); }
        finally { if (owned) tracker.addSource(FLIGHT); }
    }
    @Override public void save(ServerPlayer player, Runnable save) {
        boolean flying = player.getAbilities().flying;
        try { withoutGrant(VanillaAbilities.ALLOW_FLYING.getTracker(player), save); }
        finally {
            if (flying && player.getAbilities().mayfly && !player.getAbilities().flying) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
        }
    }
}
