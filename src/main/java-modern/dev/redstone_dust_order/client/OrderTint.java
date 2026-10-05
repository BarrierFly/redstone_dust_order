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

import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Installs the mod's tint over {@code minecraft:redstone_wire}, and nothing else.
 *
 * <p>This is the 26.x half of the version split. Vanilla replaced the functional
 * {@code BlockColor} with {@code BlockTintSource}, which splits one method into
 * four, and all four matter here:
 *
 * <ul>
 *   <li>{@code color} gets no world, so it serves the item icon and the
 *       creative-inventory block preview. Both keep looking vanilla.</li>
 *   <li>{@code colorInWorld} is the actual world tint.</li>
 *   <li>{@code colorAsTerrainParticle} defaults to {@code colorInWorld}, which
 *       would put order colours on break and terrain particles. It is overridden
 *       so those keep reading as "redstone at this power".</li>
 *   <li>{@code relevantProperties} must include {@code POWER}, or the tint cache
 *       will hand back a stale colour whenever alpha &lt; 1.</li>
 * </ul>
 *
 * <p>As on the older versions this goes through vanilla's own
 * {@code BlockColors#register}, which overwrites by construction, rather than
 * through Fabric's {@code ColorProviderRegistry}.
 *
 * <p>Installation is deferred to the first client tick; see the 1.19.4 - 1.21.x
 * copy of this class for why {@code install()} cannot do the work itself.
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

		colors.register(List.of(new BlockTintSource()
		{
			@Override
			public int color(BlockState state)
			{
				return vanillaColor(state);
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos)
			{
				return OrderColors.colorAt(pos.getX(), pos.getY(), pos.getZ(), vanillaColor(state));
			}

			@Override
			public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos)
			{
				return vanillaColor(state);
			}

			@Override
			public Set<Property<?>> relevantProperties()
			{
				return Set.of(RedstoneWireBlock.POWER);
			}
		}), Blocks.REDSTONE_WIRE);

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
		return RedstoneWireBlock.getColorForPower(state.getValue(RedstoneWireBlock.POWER));
	}
}