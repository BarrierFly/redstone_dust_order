/*
 * This file is part of the Redstone Dust Update Order project.
 *
 * Released into the public domain under the Unlicense.
 * <https://unlicense.org/>
 *
 * This software is free software: you can redistribute it and/or modify it
 * under the terms of the Unlicense. You may copy, modify, publish,
 * perform, sublicense and/or distribute it, in source code form or as a
 * compiled binary, for any purpose, with or without fee.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 */

package dev.redstone_dust_order.client;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import dev.redstone_dust_order.order.OrderTable;
import dev.redstone_dust_order.order.RedstoneHash;

/**
 * What {@code /rsusoc check} actually checks.
 *
 * <p>Being clear about scope matters more than looking thorough, so: this
 * <em>cannot</em> verify that vanilla really routes dust updates through a
 * {@code HashSet}. That would need a mixin and a packet sniffer, and this mod
 * ships neither on purpose. What it can do is:
 *
 * <ol>
 *   <li>recompute the 7 buckets and the traversal straight from the arithmetic,
 *       with no lookup table involved, and confirm the table agrees 鈥?this is a
 *       real check on the thing most likely to rot, the compressed table drifting
 *       away from the model it was derived from;</li>
 *   <li>report whether the current world randomises update order, in which case
 *       no positional model can be right;</li>
 *   <li>print a position's full derivation so it can be compared against the
 *       browser simulator at the same coordinates.</li>
 * </ol>
 */
public final class OrderSelfCheck
{
	private static final ChatFormatting LABEL = ChatFormatting.DARK_GRAY;

	private OrderSelfCheck() {}

	public static void report(FabricClientCommandSource source)
	{
		boolean tableAgrees = OrderTableCheck.tableMatchesReferenceArithmetic();

		LocalPlayer player = source.getPlayer();

		if (player == null) {
			line(source, "check", "global");
			line(source, "table agrees with reference arithmetic", tableAgrees ? "yes" : "NO");
			line(source, "distinct update orders", String.valueOf(OrderTable.get().orderCount()));
			source.sendFeedback(Component.translatable("redstone_dust_order.command.check_no_player")
					.withStyle(ChatFormatting.YELLOW));
			return;
		}

		BlockPos pos = player.blockPosition();
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		int hash = RedstoneHash.hash(x, y, z);
		int low16 = RedstoneHash.low16(hash);
		int nibbleA = RedstoneHash.nibbleA(hash);
		int nibbleB = RedstoneHash.nibbleB(hash);
		int mode = RedstoneHash.modeOfFast(hash);

		int[] buckets = new int[RedstoneHash.CORE_COUNT];
		RedstoneHash.bucketsAt(hash, buckets);

		int[] traversal = new int[RedstoneHash.CORE_COUNT];
		int referenceKey = RedstoneHash.packOrder(RedstoneHash.traversalAt(hash, traversal));

		OrderTable table = OrderTable.get();
		int orderIndex = table.orderIndexAt(x, y, z);
		int tableKey = table.orderKey(orderIndex);

		line(source, "check", "(" + x + ", " + y + ", " + z + ")");
		line(source, "hash  x + 31y + 961z", Integer.toString(hash) + "  (L=0x" + Integer.toHexString(low16)
				+ " A=" + nibbleA + " B=" + nibbleB + ")");
		line(source, "mode", modeName(mode));
		line(source, "buckets", bucketsText(buckets));
		line(source, "traversal", RedstoneHash.describeOrder(referenceKey));
		line(source, "order index", orderIndex + "  (table agrees: " + (referenceKey == tableKey ? "yes" : "NO") + ")");
		line(source, "frequency", String.format("%.6f%%", table.frequency(orderIndex) * 100.0));
		line(source, "3-group pattern", table.groupPattern(orderIndex) < 0
				? "no (rare position)"
				: String.valueOf(table.groupPattern(orderIndex)));
		line(source, "table vs reference arithmetic", tableAgrees ? "yes" : "NO");
		line(source, "this world", WorldWarnings.isUpdateOrderRandomised(source.getClient().level)
				? "randomised update order - colours are meaningless here"
				: "positional model applies");

		if (!tableAgrees || referenceKey != tableKey) {
			source.sendFeedback(Component.translatable("redstone_dust_order.command.check_failed")
					.withStyle(ChatFormatting.RED));
		}
	}

	private static void line(FabricClientCommandSource source, String label, String value)
	{
		source.sendFeedback(Component.literal("  " + pad(label) + " ").withStyle(LABEL)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE)));
	}

	private static String pad(String label)
	{
		StringBuilder sb = new StringBuilder(label);
		while (sb.length() < 34) sb.append(' ');
		return sb.toString();
	}

	private static String bucketsText(int[] buckets)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < buckets.length; i++) {
			if (i > 0) sb.append(' ');
			sb.append(RedstoneHash.CORE_NAME[i]).append('=').append(buckets[i]);
		}
		return sb.toString();
	}

	private static String modeName(int mode)
	{
		switch (mode) {
			case RedstoneHash.MODE_SAFE:
				return "0 safe (no neighbour crosses the 16-bit window)";
			case RedstoneHash.MODE_CARRY_Z:
				return "1 carry {+Z}";
			case RedstoneHash.MODE_CARRY_ZY:
				return "2 carry {+Z,+Y}";
			case RedstoneHash.MODE_CARRY_ZYX:
				return "3 carry {+Z,+Y,+X}  (A must be 15)";
			case RedstoneHash.MODE_BORROW_Z:
				return "4 borrow {-Z}";
			case RedstoneHash.MODE_BORROW_ZY:
				return "5 borrow {-Z,-Y}";
			default:
				return "6 borrow {-Z,-Y,-X}  (A must be 0)";
		}
	}
}