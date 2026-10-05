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

import dev.redstone_dust_order.order.OrderKind;
import dev.redstone_dust_order.order.OrderTable;
import dev.redstone_dust_order.order.Palette;

/**
 * The render-path state: what the tint source should currently produce.
 *
 * <p>Deliberately free of Minecraft imports. Everything the tint needs is
 * passed in as plain numbers, which keeps the interesting logic — the table
 * lookup and the alpha blend — unit-testable without a game on the classpath,
 * and keeps the one place that has to care about six different vanilla tint APIs
 * down to a single version-branched file.
 *
 * <p>The fields are {@code volatile} and written only from the client thread
 * while the chunk meshing threads read them. That is enough for "eventually
 * consistent after a rebuild", which is all we need: a config change sets
 * {@link ChunkRebuild#request()}, and the new values are fully visible before
 * the rebuild that shows them runs on the next tick.
 */
public final class OrderColors
{
	private static volatile boolean enabled;
	private static volatile OrderKind kind = Config.DEFAULT_KIND;
	private static volatile float alpha = Config.DEFAULT_ALPHA;
	private static volatile Palette palette = Config.DEFAULT_KIND.palette();

	private OrderColors() {}

	/** Snapshots the config into the render path. Rebuilds are requested separately. */
	public static void apply(Config config)
	{
		enabled = config.enabled();
		kind = config.kind();
		alpha = config.alpha();
		palette = kind.palette();
	}

	public static boolean enabled()
	{
		return enabled;
	}

	public static OrderKind kind()
	{
		return kind;
	}

	public static float alpha()
	{
		return alpha;
	}

	/**
	 * The colour for one dust position.
	 *
	 * @param x,y,z  world position of the dust
	 * @param vanillaPowerColor  what vanilla would have tinted it to, given its
	 *                            current power; used for the {@code alpha}
	 *                            blend and returned unchanged when the mod is off
	 */
	public static int colorAt(int x, int y, int z, int vanillaPowerColor)
	{
		if (!enabled) return vanillaPowerColor;

		int orderIndex = OrderTable.get().orderIndexAt(x, y, z);

		// Only ever -1 for a (mode, A) pair the model says is unreachable, which
		// a real position cannot hit. Falling back to vanilla keeps a future
		// model break visible as "no colour" instead of as garbage.
		if (orderIndex < 0) return vanillaPowerColor;

		float mix = alpha;

		if (mix >= 1.0f) return palette.color(orderIndex);
		if (mix <= 0.0f) return vanillaPowerColor;

		return blend(vanillaPowerColor, palette.color(orderIndex), mix);
	}

	/** Alpha blend two opaque ARGB colours; the result stays opaque. */
	public static int blend(int from, int to, float t)
	{
		int r = (int) (((from >> 16) & 0xFF) * (1.0f - t) + ((to >> 16) & 0xFF) * t);
		int g = (int) (((from >> 8) & 0xFF) * (1.0f - t) + ((to >> 8) & 0xFF) * t);
		int b = (int) ((from & 0xFF) * (1.0f - t) + (to & 0xFF) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}