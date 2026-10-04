package dev.flightblock;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.*;

public final class FlightManager {
    private final FlightBlock mod;
    private final FlightBindings bindings = new FlightBindings();
    private final Set<UUID> hudPlayers = new HashSet<>();
    public FlightManager(FlightBlock mod) { this.mod = mod; }
    public Set<String> bindings(ServerPlayer player) {
        return bindings.get(player.getUUID());
    }
    public UUID owner(String id) { return bindings.owner(id); }
    public boolean bind(ServerPlayer player, String id) {
        if (!bindings.bind(player.getUUID(), id)) return false;
        check(player);
        return true;
    }
    public void forget(String id) {
        bindings.forget(id);
        mod.glow.remove(id);
    }
    public boolean unbind(ServerPlayer player, String id) {
        if (!bindings.unbind(player.getUUID(), id)) return false;
        mod.glow.remove(id);
        check(player);
        return true;
    }
    public void clear(ServerPlayer player, boolean cushion) {
        for (String id : bindings(player)) mod.glow.remove(id);
        bindings.clear(player.getUUID());
        revoke(player, cushion);
        clearHud(player);
    }
    public void check(ServerPlayer player) {
        if (!player.isAlive()) { clear(player, false); return; }
        for (String id : new HashSet<>(bindings(player))) {
            WorldState.Anchor a = mod.state.byId.get(id);
            if (a != null) {
                var level = mod.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.Identifier.parse(a.dimension())));
                if (level != null) mod.reconcile(level, a);
            }
        }
        WorldState.Anchor shown = selectAnchor(bindings(player).stream().map(mod.state.byId::get).filter(Objects::nonNull).toList(),
            mod.config, FlightBlock.dimension(player.level()), player.getX(), player.getY(), player.getZ(), mod.clock.millis());
        if (shown == null) clearHud(player);
        else {
            double margin = shown.margin(player.getX(), player.getY(), player.getZ(), mod.config.radius(shown.level()));
            long seconds = Math.max(0, (shown.expiresAt() - mod.clock.millis() + 999) / 1000);
            String distance = margin >= 0 ? "可远离 " : "超出范围 ";
            String message = String.format(Locale.ROOT, "飞行方块 %d 级 | %s%.1f 格 | 剩余 %02d:%02d:%02d",
                shown.level(), distance, Math.abs(margin), seconds / 3600, seconds / 60 % 60, seconds % 60);
            player.sendSystemMessage(Component.literal(message).withStyle(margin >= 0 ? ChatFormatting.AQUA : ChatFormatting.RED), true);
            hudPlayers.add(player.getUUID());
        }
        if (player.isCreative() || player.isSpectator()) {
            revoke(player, false);
            return;
        }
        boolean allowed = bindings(player).stream().map(mod.state.byId::get).filter(Objects::nonNull)
            .anyMatch(a -> a.valid(mod.clock.millis()) && a.contains(FlightBlock.dimension(player.level()), player.getX(), player.getY(), player.getZ(), mod.config.radius(a.level())));
        if (allowed) {
            mod.abilities.grant(player);
        } else revoke(player, true);
    }
    public static WorldState.Anchor selectAnchor(Collection<WorldState.Anchor> anchors, Config config,
                                                 String dimension, double x, double y, double z, long now) {
        return anchors.stream().filter(a -> a.valid(now) && a.dimension().equals(dimension))
            .max(Comparator.comparingDouble((WorldState.Anchor a) -> a.margin(x, y, z, config.radius(a.level())))
                .thenComparingLong(WorldState.Anchor::expiresAt).thenComparing(WorldState.Anchor::id)).orElse(null);
    }
    private void clearHud(ServerPlayer player) {
        if (hudPlayers.remove(player.getUUID())) player.sendSystemMessage(Component.empty(), true);
    }
    private void revoke(ServerPlayer player, boolean cushion) {
        boolean airborne = !player.onGround();
        if (!mod.abilities.revoke(player)) return;
        if (player.getAbilities().mayfly) return;
        int ticks = mod.config.slowSeconds() * 20;
        if (cushion && airborne && player.isAlive() && ticks > 0) {
            MobEffectInstance current = player.getEffect(MobEffects.SLOW_FALLING);
            if (current == null || (!current.isInfiniteDuration() && current.getDuration() < ticks && current.getAmplifier() == 0))
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0));
        }
    }
    public void save(ServerPlayer player, Runnable save) { mod.abilities.save(player, save); }
}
