package dev.flightblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.flightblock.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.*;

@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @WrapMethod(method = "changeGameModeForPlayer")
    private boolean flightblock$mode(GameType type, Operation<Boolean> original) {
        boolean changed = original.call(type);
        if (changed && FlightBlock.INSTANCE != null && FlightBlock.INSTANCE.state != null) FlightBlock.INSTANCE.flight.check(player);
        return changed;
    }
    @WrapMethod(method = "destroyBlock")
    private boolean flightblock$mining(BlockPos pos, Operation<Boolean> original) {
        WorldState.Anchor previous = FlightBlock.MINING.get();
        FlightBlock.MINING.set(FlightBlock.INSTANCE.at(level, pos));
        try { return original.call(pos); }
        finally { FlightBlock.MINING.set(previous); }
    }
}
