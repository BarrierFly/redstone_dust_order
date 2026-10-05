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

package dev.redstone_dust_order.order;

/**
 * Turns an update-order index into an opaque ARGB colour.
 *
 * <p>Implementations are immutable and stateless, and {@link #color(int)} is
 * called from the chunk meshing threads, so it must stay allocation-free.
 */
public interface Palette
{
	/**
	 * Colour for an order index, packed ARGB. The alpha channel is always 0xFF;
	 * vanilla tints are opaque and a translucent tint would fight with the
	 * render layer.
	 */
	int color(int orderIndex);

	/** Translation key for the human-readable name of this palette. */
	String translationKey();
}