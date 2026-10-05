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

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Installs the mod's tint over {@code minecraft:redstone_wire}, and nothing else.
 *
 * <p>This is the 1.19.4 - 1.21.x half of the version split: vanilla has a
 * functional {@code BlockColor} here, registered with
 * {@code BlockColors#register(BlockColor, Block...)}.
 *
 * <p>Note that the mod does <em>not</em> go through Fabric's
 * {@code ColorProviderRegistry.BLOCK}. That API wraps rather than replaces
 * whenever vanilla already has a provider for the block, which is exactly this
 * situation, so it would not actually override the tint. Vanilla's own registry
 * bottoms out in {@code IdMapper#addMapping}, which is a plain
 * {@code List#set} — registering after {@code createDefault()} simply overwrites.
 * Fewer moving parts, and no dependency on how a given Fabric API release happens
 * to implement the wrapping.
 *
 * <p>Registration happens once, on client init: after
 * {@code BlockColors.createDefault()} and long before anything is meshed.
 */
public final class OrderTint
{
	private OrderTint() {}

	public static void install()
	{
		Minecraft client = Minecraft.getInstance();

		client.getBlockColors().register((state, level, pos, tintIndex) -> {
			int vanilla = vanillaColor(state);

			// A null level or position means we are off the world grid: the
			// creative-mode inventory block preview, or an item render. Those
			// should keep looking like vanilla redstone.
			if (level == null || pos == null || client.level == null) return vanilla;

			return OrderColors.colorAt(pos.getX(), pos.getY(), pos.getZ(), vanilla);
		}, Blocks.REDSTONE_WIRE);
	}

	/** Vanilla's power-based colour, straight from the block itself. */
	static int vanillaColor(BlockState state)
	{
		return RedStoneWireBlock.getColorForPower(state.getValue(RedStoneWireBlock.POWER));
	}
}