package dev.flightblock;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class FlightBlock implements ModInitializer {
    public static FlightBlock INSTANCE;
    public static final Logger LOG = LoggerFactory.getLogger("flightblock");
    public Config config = Config.defaults();
    public Clock clock = Clock.systemUTC();
    public WorldState state;
    public MinecraftServer server;
    public final FlightManager flight = new FlightManager(this);
    public final BlockGlow glow = new BlockGlow();
    public static final ThreadLocal<WorldState.Anchor> MINING = new ThreadLocal<>();
    private Path configPath;
    private int ticks;
    private final Queue<Runnable> pendingChunks = new ConcurrentLinkedQueue<>();
    @Override public void onInitialize() {
        INSTANCE = this;
        configPath = FabricLoader.getInstance().getConfigDir().resolve("flightblock.json");
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, Config.DEFAULT_JSON);
            }
            config = Config.read(configPath);
        } catch (Exception e) { LOG.error("FLIGHTBLOCK 配置错误，使用内置默认值并保留原文件", e); }
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            state = s.overworld().getDataStorage().computeIfAbsent(WorldState.TYPE);
            expire();
            for (ServerLevel level : s.getAllLevels()) {
                for (WorldState.Anchor a : new ArrayList<>(state.byId.values())) {
                    if (a.dimension().equals(dimension(level)) && level.hasChunkAt(a.pos())) reconcile(level, a);
                }
            }
            LOG.info("FlightBlock {} 已启动：{} 个登记实例", FabricLoader.getInstance().getModContainer("flightblock").orElseThrow().getMetadata().getVersion().getFriendlyString(), state.byId.size());
            if (Boolean.getBoolean("flightblock.verify")) RuntimeChecks.run();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            pendingChunks.clear();
            for (ServerPlayer p : s.getPlayerList().getPlayers()) flight.clear(p, false);
            glow.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { pendingChunks.clear(); state = null; server = null; });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (state == null) return;
            drainPending(pendingChunks);
            expire();
            if (++ticks % config.interval() == 0) {
                for (ServerPlayer p : s.getPlayerList().getPlayers()) flight.check(p);
            }
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            // The FULL chunk future is not completed inside this callback.
            // Queue work instead of reading blocks or creating entities here.
            pendingChunks.add(() -> {
                if (state != null && server == level.getServer())
                    for (WorldState.Anchor a : state.chunk(dimension(level), chunk.getPos().x(), chunk.getPos().z())) reconcile(level, a);
            });
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (state != null) for (WorldState.Anchor a : state.chunk(dimension(level), chunk.getPos().x(), chunk.getPos().z())) glow.remove(a.id());
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer p) || state == null || p.isSpectator()) return InteractionResult.PASS;
            WorldState.Anchor a = at(level, hit.getBlockPos());
            if (a == null) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
            if (p.isShiftKeyDown()) {
                boolean removed = flight.unbind(p, a.id());
                p.sendSystemMessage(Component.literal(removed ? "已解除你对该飞行方块的绑定，并释放占用。" : "你没有绑定该飞行方块。"));
                return InteractionResult.SUCCESS;
            }
            reconcile(p.level(), a);
            a = at(level, hit.getBlockPos());
            if (a == null) return InteractionResult.SUCCESS;
            if (!a.valid(clock.millis())) {
                if (a.activatedAt() > 0) return InteractionResult.SUCCESS;
                a = a.activate(config, clock);
                state.put(a);
            }
            glow.ensure(p.level(), a, clock.millis());
            if (!flight.bind(p, a.id())) {
                p.sendSystemMessage(Component.literal("该飞行方块正被其他玩家使用，最多同时供一名玩家使用；请等待对方解绑或离线。"));
                return InteractionResult.SUCCESS;
            }
            p.sendSystemMessage(Component.literal("飞行方块 " + a.level() + " 级：半径 " + config.radius(a.level()) + " 格，剩余 " + remaining(a) + " 秒；双击跳跃起飞。"));
            return InteractionResult.SUCCESS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> refresh(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> flight.clear(handler.player, false));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
            if (entity instanceof ServerPlayer p) flight.clear(p, false);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, p, alive) -> { flight.clear(old, false); refresh(p); });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((p, from, to) -> flight.clear(p, true));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
            Commands.literal("flightblock")
                .then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> reload(c.getSource())))
                .then(Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                            .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "players"), IntegerArgumentType.getInteger(c, "level"), 1))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "players"), IntegerArgumentType.getInteger(c, "level"), IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("off").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) { c.getSource().sendFailure(Component.literal("请由玩家执行 /flightblock off")); return 0; }
                    flight.clear(p, true);
                    p.sendSystemMessage(Component.literal("已清空你的飞行方块绑定。")); return 1;
                }))));
        if (Boolean.getBoolean("flightblock.verify")) RuntimeChecks.checkMixinTargets();
    }
    static void drainPending(Queue<Runnable> tasks) {
        // Work triggered by this batch waits for the next tick, too.
        for (int remaining = tasks.size(); remaining > 0; remaining--) {
            Runnable task = tasks.poll();
            if (task == null) break;
            task.run();
        }
    }
    public static String dimension(Level level) { return level.dimension().identifier().toString(); }
    public WorldState.Anchor at(Level level, BlockPos pos) {
        return !(level instanceof ServerLevel) || state == null ? null : state.at(dimension(level), pos);
    }
    public void invalidate(WorldState.Anchor a) {
        if (a == null || state == null) return;
        state.remove(a);
        glow.remove(a.id());
        flight.forget(a.id());
        if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) flight.check(p);
    }
    public boolean canPlace(ItemStack stack, ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!FlightItems.marked(stack)) return true;
        Rules.ItemState item = FlightItems.read(stack);
        String reason = item == null ? "飞行方块数据损坏，拒绝放置。"
            : item.expired(clock.millis()) ? "该飞行方块已经过期。"
            : item.activated() && state.byId.containsKey(item.id().toString()) ? "同一实例已在其他位置放置。" : null;
        if (reason != null) {
            if (item != null && item.expired(clock.millis())) stack.shrink(1);
            if (player != null) player.sendSystemMessage(Component.literal(reason));
            return false;
        }
        return true;
    }
    public void placed(ItemStack stack, ServerLevel level, BlockPos pos) {
        WorldState.Anchor old = at(level, pos);
        if (old != null) invalidate(old);
        Rules.ItemState item = FlightItems.read(stack);
        if (item == null || !level.getBlockState(pos).is(Blocks.TARGET)) return;
        String id = item.activated() ? item.id().toString() : UUID.randomUUID().toString();
        state.put(new WorldState.Anchor(dimension(level), pos.asLong(), item.level(), id, item.activatedAt(), item.expiresAt(), false));
    }
    public void reconcile(ServerLevel level, WorldState.Anchor a) {
        if (state.byId.get(a.id()) != a) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(a.pos().getX() >> 4, a.pos().getZ() >> 4);
        if (chunk == null) return;
        if (!chunk.getBlockState(a.pos()).is(Blocks.TARGET)) { invalidate(a); return; }
        if (a.pendingRemoval() || (a.activatedAt() > 0 && a.expiresAt() <= clock.millis())) {
            level.setBlock(a.pos(), Blocks.AIR.defaultBlockState(), 3);
            invalidate(a);
        } else {
            glow.ensure(level, a, clock.millis());
        }
    }
    private void expire() {
        if (state == null) return;
        WorldState.Anchor a;
        while ((a = state.pollExpired(clock.millis())) != null) {
            glow.remove(a.id());
            flight.forget(a.id());
            WorldState.Anchor pending = a.pending();
            state.put(pending);
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(a.dimension())));
            if (level != null && level.hasChunkAt(a.pos())) reconcile(level, pending);
        }
    }
    public void refresh(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) FlightItems.refresh(p.getInventory().getItem(i), config);
        for (net.minecraft.world.inventory.Slot slot : p.containerMenu.slots) FlightItems.refresh(slot.getItem(), config);
        FlightItems.refresh(p.containerMenu.getCarried(), config);
    }
    private long remaining(WorldState.Anchor a) { return Math.max(0, (a.expiresAt() - clock.millis() + 999) / 1000); }
    private int reload(CommandSourceStack source) {
        try {
            Config next = Config.read(configPath);
            config = next;
            for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) { refresh(p); flight.check(p); }
            source.sendSuccess(() -> Component.literal("已重载：三级时长 " + next.first() + "/" + next.second() + "/" + next.third()
                + " 秒，三级半径 " + next.firstRadius() + "/" + next.secondRadius() + "/" + next.thirdRadius()
                + " 格，检查周期 " + next.interval() + " tick；范围立即更新，已激活方块到期时间保持不变。"), true);
            return 1;
        } catch (Exception e) {
            LOG.error("flightblock reload 失败，保留整份旧配置：{}", e.getMessage());
            source.sendFailure(Component.literal(e.getMessage())); return 0;
        }
    }
    private int give(CommandSourceStack source, Collection<ServerPlayer> players, int level, int count) {
        for (ServerPlayer p : players) {
            ItemStack stack = FlightItems.create(new Rules.ItemState(level, null, 0, 0), config);
            stack.setCount(count);
            p.getInventory().add(stack);
            int dropped = stack.getCount();
            if (!stack.isEmpty()) {
                var entity = p.drop(stack, false, net.minecraft.util.Prediction.PREDICTED);
                if (entity != null) { entity.setNoPickUpDelay(); entity.setTarget(p.getUUID()); }
                p.sendSystemMessage(Component.literal("背包空间不足，" + dropped + " 件飞行方块掉落在脚下。"));
            }
            p.containerMenu.broadcastChanges();
            LOG.info("管理员 {} 向 {} 发放 {} 级飞行方块 {} 件（入包 {}，掉落 {}）", source.getTextName(), p.getName().getString(), level, count, count - dropped, dropped);
            source.sendSuccess(() -> Component.literal("已向 " + p.getName().getString() + " 发放 " + level + " 级飞行方块 " + count + " 件。"), true);
        }
        return players.size();
    }
    private int status(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        if (p == null) { source.sendFailure(Component.literal("请由玩家执行 /flightblock status；控制台可用 give/reload。")); return 0; }
        Set<String> ids = flight.bindings(p);
        if (ids.isEmpty()) { p.sendSystemMessage(Component.literal("没有绑定飞行方块，请先右键已放置的飞行方块。")); return 1; }
        for (String id : ids) {
            WorldState.Anchor a = state.byId.get(id);
            if (a != null) p.sendSystemMessage(Component.literal(a.dimension() + " " + a.pos().toShortString() + "；" + a.level()
                + " 级；半径 " + config.radius(a.level()) + " 格；剩余 " + remaining(a) + " 秒；范围内：" + (a.valid(clock.millis()) && a.contains(dimension(p.level()), p.getX(), p.getY(), p.getZ(), config.radius(a.level())))));
        }
        return 1;
    }
}
