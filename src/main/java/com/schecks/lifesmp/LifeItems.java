package com.schecks.lifesmp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public final class LifeItems {
    public static final String LIFE_SHARD_TAG = "lifesmp_life_shard";
    public static final String REVIVAL_CRYSTAL_TAG = "lifesmp_revival_crystal";

    private LifeItems() {}

    private static MutableComponent styled(String text, int rgb) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false));
    }

    private static MutableComponent styled(String text, ChatFormatting color) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    public static ItemStack createLifeShard(int count) {
        ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING, count);
        stack.set(DataComponents.CUSTOM_NAME, styled("❤ Life Shard", 0xFF4C4C));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            styled("Redeem this to gain ", ChatFormatting.GRAY)
                .append(styled("1 life", ChatFormatting.YELLOW))
                .append(styled(".", ChatFormatting.GRAY)),
            styled("Obtained from /life withdraw", ChatFormatting.DARK_GRAY)
        )));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(LIFE_SHARD_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isLifeShard(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.TOTEM_OF_UNDYING)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(LIFE_SHARD_TAG).orElse(false);
    }

    public static ItemStack createRevivalCrystal(int count) {
        ItemStack stack = new ItemStack(Items.NETHER_STAR, count);
        stack.set(DataComponents.CUSTOM_NAME, styled("Revival Crystal", 0xFFE17B));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            styled("Revives a banned player with ", ChatFormatting.GRAY)
                .append(styled("3 lives", ChatFormatting.YELLOW)),
            styled("Hold and run ", ChatFormatting.DARK_GRAY)
                .append(styled("/life crystal <player>", ChatFormatting.GRAY))
        )));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(REVIVAL_CRYSTAL_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isRevivalCrystal(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.NETHER_STAR)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(REVIVAL_CRYSTAL_TAG).orElse(false);
    }
}
