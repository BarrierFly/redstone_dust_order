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
import net.minecraft.client.color.block.BlockColors;
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
 * <h2>Why installation is deferred</h2>
 *
 * {@link #install()} cannot do the work itself. Fabric invokes client
 * entrypoints from inside the {@code Minecraft} constructor, and
 * {@code blockColors = BlockColors.createDefault()} is a good thousand
 * instructions later — so {@code getBlockColors()} is still null at entrypoint
 * time. {@link #ensureInstalled(Minecraft)} does the work on the first client
 * tick instead, retrying until {@code BlockColors} actually exists. That is early
 * enough by a wide margin: chunk meshing needs a {@code ClientLevel}, and there
 * cannot be one before the client is running.
 */
public final class OrderTint
{
	private static boolean requested;
	private static boolean installed;
	private static int attempts;

	private OrderTint() {}

	/** Ask for the tint to be installed. Safe to call at any point. */
	public static void install()
	{
		requested = true;
	}

	/** Installs on the first call where {@code BlockColors} is available. */
	public static void ensureInstalled(Minecraft client)
	{
		if (installed || !requested) return;

		attempts++;

		BlockColors colors = client.getBlockColors();
		if (colors == null) return;

		installed = true;
		requested = false;

		colors.register((state, level, pos, tintIndex) -> {
			int vanilla = vanillaColor(state);

			// A null level or position means we are off the world grid: the
			// creative-mode inventory block preview, or an item render. Those
			// should keep looking like vanilla redstone.
			if (level == null || pos == null || client.level == null) return vanilla;

			return OrderColors.colorAt(pos.getX(), pos.getY(), pos.getZ(), vanilla);
		}, Blocks.REDSTONE_WIRE);

		RedstoneDustOrderClient.LOGGER.info("Redstone dust tint installed ({} tick(s) after startup)", attempts);
	}

	/** True once the tint is actually in place. */
	public static boolean installed()
	{
		return installed;
	}

	/** Vanilla's power-based colour, straight from the block itself. */
	static int vanillaColor(BlockState state)
	{
		return RedStoneWireBlock.getColorForPower(state.getValue(RedStoneWireBlock.POWER));
	}
}