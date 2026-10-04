package dev.flightblock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class FlightItems {
    private FlightItems() {}
    public static boolean marked(ItemStack stack) {
        return stack.is(Items.TARGET) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("flightblock");
    }
    public static Rules.ItemState read(ItemStack stack) {
        if (!marked(stack)) return null;
        try {
            CompoundTag data = stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("flightblock").orElseThrow();
            if (!(data.get("schemaVersion") instanceof IntTag) || data.getIntOr("schemaVersion", -1) != 1
                || !(data.get("level") instanceof IntTag)) return null;
            int level = data.getIntOr("level", -1);
            if (!data.contains("instanceId") && !data.contains("activatedAt") && !data.contains("expiresAt"))
                return new Rules.ItemState(level, null, 0, 0);
            if (!(data.get("instanceId") instanceof StringTag) || !(data.get("activatedAt") instanceof LongTag)
                || !(data.get("expiresAt") instanceof LongTag)) return null;
            return new Rules.ItemState(level, UUID.fromString(data.getString("instanceId").orElseThrow()),
                data.getLong("activatedAt").orElseThrow(), data.getLong("expiresAt").orElseThrow());
        } catch (RuntimeException e) { return null; }
    }
    public static ItemStack create(Rules.ItemState state, Config config) {
        ItemStack stack = new ItemStack(Items.TARGET);
        CompoundTag data = new CompoundTag();
        data.putInt("schemaVersion", 1);
        data.putInt("level", state.level());
        if (state.activated()) {
            data.putString("instanceId", state.id().toString());
            data.putLong("activatedAt", state.activatedAt());
            data.putLong("expiresAt", state.expiresAt());
        }
        CompoundTag root = new CompoundTag();
        root.put("flightblock", data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        decorate(stack, state, config);
        return stack;
    }
    public static void refresh(ItemStack stack, Config config) {
        Rules.ItemState state = read(stack);
        if (state != null) decorate(stack, state, config);
    }
    public static ChatFormatting levelColor(int level) {
        return switch (level) { case 1 -> ChatFormatting.GOLD; case 2 -> ChatFormatting.AQUA;
            case 3 -> ChatFormatting.LIGHT_PURPLE; default -> throw new IllegalArgumentException("level"); };
    }
    private static void decorate(ItemStack stack, Rules.ItemState state, Config config) {
        String roman = switch (state.level()) { case 1 -> "I"; case 2 -> "II"; default -> "III"; };
        ChatFormatting color = levelColor(state.level());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("飞行方块 · " + roman + " 级").withStyle(color).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        List<Component> lore = new ArrayList<>();
        lore.add(line("等级：" + roman + " 级"));
        lore.add(line("飞行范围：半径 " + config.radius(state.level()) + " 格（同维度）"));
        if (state.activated()) {
            lore.add(line("到期时间：" + DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx").withZone(config.zone()).format(Instant.ofEpochMilli(state.expiresAt()))));
            lore.add(line("已激活，离线及停服期间仍计时"));
            lore.add(line("不可用于升级"));
        } else {
            lore.add(line("首次右键激活后有效：" + config.seconds(state.level()) + " 秒"));
            lore.add(line("放置后右键获得飞行资格"));
            lore.add(line(state.level() == 3 ? "未激活，已达最高等级" : "未激活，可用于升级"));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }
    private static Component line(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false));
    }
}
