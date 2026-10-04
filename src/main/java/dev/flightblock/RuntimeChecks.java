package dev.flightblock;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Optional checks run inside Fabric's actual Mixin-enabled dedicated-server JVM. */
public final class RuntimeChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void checkMixinTargets() {
        try {
            for (String name : List.of("net.minecraft.world.inventory.CraftingMenu", "net.minecraft.world.inventory.ResultSlot",
                "net.minecraft.world.item.BlockItem", "net.minecraft.world.level.chunk.LevelChunk", "net.minecraft.world.level.block.Block",
                "net.minecraft.world.level.block.piston.PistonBaseBlock", "net.minecraft.server.level.ServerPlayerGameMode",
                "net.minecraft.server.players.PlayerList", "net.minecraft.world.inventory.AbstractContainerMenu", "net.minecraft.world.item.crafting.ShapedRecipe",
                "net.minecraft.world.level.block.state.BlockBehaviour")) Class.forName(name);
            FlightBlock.LOG.info("FLIGHTBLOCK_MIXIN_TARGETS_OK：11 个 Mixin 目标类加载通过");
        } catch (Exception e) { throw new IllegalStateException("FlightBlock Mixin 目标检查失败", e); }
    }
    public static void run() {
        try {
            for (String name : List.of("net.minecraft.world.inventory.CraftingMenu", "net.minecraft.world.inventory.ResultSlot",
                "net.minecraft.world.item.BlockItem", "net.minecraft.world.level.chunk.LevelChunk", "net.minecraft.world.level.block.Block",
                "net.minecraft.world.level.block.piston.PistonBaseBlock", "net.minecraft.server.level.ServerPlayerGameMode",
                "net.minecraft.server.players.PlayerList", "net.minecraft.world.inventory.AbstractContainerMenu")) Class.forName(name);
            Config config = Config.defaults();
            var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            for (int tier = 1; tier <= 3; tier++) {
                ItemStack item = FlightItems.create(new Rules.ItemState(tier, null, 0, 0), config);
                check(FlightItems.read(item).level() == tier, "等级数据往返");
                check(item.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE), "原版光效组件");
                check(ItemStack.isSameItemSameComponents(item, FlightItems.create(new Rules.ItemState(tier, null, 0, 0), config)), "未激活物品可堆叠");
                JsonElement encoded = ItemStack.CODEC.encodeStart(ops, item).getOrThrow();
                check(FlightItems.read(ItemStack.CODEC.parse(ops, encoded).getOrThrow()).equals(FlightItems.read(item)), "原版物品 codec 往返");
                try (InputStream stream = RuntimeChecks.class.getResourceAsStream("/data/flightblock/recipe/level_" + tier + ".json")) {
                    Recipe<?> decoded = Recipe.DIRECT_CODEC.parse(ops, JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))).getOrThrow();
                    check(decoded instanceof ShapedRecipe, "配方使用原版序列化器");
                    ShapedRecipe recipe = (ShapedRecipe)decoded;
                    check(recipe.isSpecial(), "隐藏不能表达严格组件校验的配方书条目");
                    List<ItemStack> grid = switch (tier) {
                        case 1 -> List.of(new ItemStack(Items.FEATHER), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.FEATHER),
                            new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.TARGET), new ItemStack(Items.GOLD_INGOT),
                            new ItemStack(Items.FEATHER), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.FEATHER));
                        case 2 -> List.of(new ItemStack(Items.FEATHER), new ItemStack(Items.DIAMOND), new ItemStack(Items.FEATHER),
                            new ItemStack(Items.DIAMOND), FlightItems.create(new Rules.ItemState(1, null, 0, 0), config), new ItemStack(Items.DIAMOND),
                            new ItemStack(Items.FEATHER), new ItemStack(Items.DIAMOND), new ItemStack(Items.FEATHER));
                        default -> List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR), new ItemStack(Items.DIAMOND),
                            new ItemStack(Items.NETHER_STAR), FlightItems.create(new Rules.ItemState(2, null, 0, 0), config), new ItemStack(Items.NETHER_STAR),
                            new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.DIAMOND));
                    };
                    CraftingInput input = CraftingInput.of(3, 3, grid);
                    check(!recipe.matches(input, null), "自动合成器上下文拒绝匹配");
                    check(recipe.assemble(input).isEmpty(), "自动合成器不产出替代标靶");
                    FlightRecipes.WORKBENCH.set(true);
                    try {
                        check(recipe.matches(input, null), "工作台严格合法配方");
                        check(FlightItems.read(recipe.assemble(input)).level() == tier, "工作台实际产出等级");
                        check(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "各槽无返还材料，原版每槽扣一件");
                        List<ItemStack> invalid = new ArrayList<>(grid);
                        invalid.set(4, tier == 1 ? FlightItems.create(new Rules.ItemState(1, null, 0, 0), config) : new ItemStack(Items.TARGET));
                        check(!recipe.matches(CraftingInput.of(3, 3, invalid), null), "拒绝降级或普通标靶升级");
                        if (tier > 1) {
                            Rules.ItemState used = new Rules.ItemState(tier - 1, null, 0, 0).activate(UUID.randomUUID(), 10,
                                Clock.fixed(Instant.ofEpochMilli(1700000000000L), ZoneOffset.UTC));
                            invalid.set(4, FlightItems.create(used, config));
                            check(!recipe.matches(CraftingInput.of(3, 3, invalid), null), "已激活物品拒绝升级");
                            check(recipe.assemble(CraftingInput.of(3, 3, invalid)).isEmpty(), "非法配方无有效结果");
                        }
                    } finally { FlightRecipes.WORKBENCH.set(false); }
                }
            }
            ItemStack renamed = new ItemStack(Items.TARGET);
            renamed.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("飞行方块 · III 级"));
            renamed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            check(!FlightItems.marked(renamed), "名称光效不授予资格");
            CompoundTag malformed = new CompoundTag(), root = new CompoundTag();
            malformed.putInt("schemaVersion", 1); malformed.putInt("level", 99); root.put("flightblock", malformed);
            renamed.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            check(FlightItems.marked(renamed) && FlightItems.read(renamed) == null, "坏数据不退化成普通升级材料");
            WorldState state = new WorldState();
            String id = UUID.randomUUID().toString();
            var a = new WorldState.Anchor("minecraft:overworld", new BlockPos(1, 2, 3).asLong(), 1, id, 1700000000000L, 1700000010000L, false);
            state.put(a);
            JsonElement saved = WorldState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
            WorldState loaded = WorldState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
            check(loaded.byId.get(id).equals(a), "世界记录 codec 重启往返");
            check(loaded.chunk("minecraft:overworld", 0, 0).size() == 1, "区块索引恢复");
            check(loaded.pollExpired(a.expiresAt() - 1) == null, "未到期队列不弹出");
            check(loaded.pollExpired(a.expiresAt()).equals(a), "精确到期队列弹出");
            loaded.put(a.pending());
            check(!loaded.byId.get(id).valid(a.activatedAt() + 1), "待销毁记录不提供资格");
            WorldState pending = WorldState.CODEC.parse(JsonOps.INSTANCE, WorldState.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow()).getOrThrow();
            check(pending.byId.get(id).pendingRemoval(), "卸载期间待销毁状态持久化");
            pending.remove(pending.byId.get(id));
            check(pending.byId.isEmpty() && pending.chunk("minecraft:overworld", 0, 0).isEmpty(), "拆除清理实例和索引");
            FlightBlock.LOG.info("FLIGHTBLOCK_RUNTIME_CHECKS_OK：{} 项实际 Fabric/Mixin/API 检查通过", checks);
        } catch (Exception e) { throw new IllegalStateException("FlightBlock 运行时检查失败", e); }
    }
}
