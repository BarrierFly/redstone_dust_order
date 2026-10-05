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

/**
 * Forces the chunk meshes to be rebuilt after something changed the tint.
 *
 * <p>Block tints are baked into chunk vertex colours, so changing the palette
 * does nothing on screen until the affected sections are meshed again. There are
 * two ways to get that wrong, and both are bad:
 *
 * <ul>
 *   <li>Doing it <em>during</em> a render frame means rebuilding every chunk
 *       again on the next one, forever. Never do that.</li>
 *   <li>Doing it from several places at once, so the same rebuild is requested
 *       repeatedly.</li>
 * </ul>
 *
 * <p>So callers only ever {@link #request()}, and the single rebuild happens
 * once on the next client tick via {@link #flush(Minecraft)}.
 *
 * <h2>Why not {@code LevelRenderer#allChanged()}</h2>
 *
 * 26.x dropped {@code allChanged()} in favour of
 * {@code invalidateCompiledGeometry(level, options, camera, blockColors)} — the
 * same entry point F3+T goes through. It builds a fresh {@code SectionCompiler}
 * around the current {@code BlockColors} and recreates the whole
 * {@code ViewArea}, so it does genuinely force every section to be re-meshed.
 *
 * <p>It also dereferences the level unconditionally, hence the null check in
 * {@link #flush(Minecraft)}. The cost is that it throws the render-distance view
 * area away rather than nudging it, which is a visible hitch at a large render
 * distance. Acceptable for a config change the player just made; not something
 * to do every frame.
 */
public final class ChunkRebuild
{
	private static boolean pending;

	private ChunkRebuild() {}

	/** Ask for one rebuild on the next tick. Repeated calls collapse into one. */
	public static void request()
	{
		pending = true;
	}

	/** True if a rebuild is queued. */
	public static boolean pending()
	{
		return pending;
	}

	public static void flush(Minecraft client)
	{
		if (!pending) return;
		pending = false;

		if (client.level == null) return;

		client.levelRenderer.invalidateCompiledGeometry(
				client.level, client.options, client.gameRenderer.mainCamera(), client.getBlockColors());
	}
}