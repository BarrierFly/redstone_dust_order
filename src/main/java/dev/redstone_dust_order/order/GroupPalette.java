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
 * The browser simulator's "3 group" mode, reproduced exactly.
 *
 * <p>Six patterns get one colour each: three hues, each in a bright and a dark
 * shade. The two patterns that cover 25% apiece get the bright shades, which
 * lines up with them being the ones you will actually be looking at. Everything
 * that does not fall into the neat 3-group structure — the ~2.93% of positions
 * where the 16-bit hash crosses a window boundary — is a single grey.
 *
 * <p>Note the split from {@link HighContrastPalette}: this one is faithful to
 * the original simulator, {@code HighContrastPalette} is the readable one.
 */
public final class GroupPalette implements Palette
{
/** Group membership, indexed by core. Mirrors the simulator's G_SELF/G_PLUS/G_MINUS. */
	private static final int GROUP_SELF = 0;
	private static final int GROUP_PLUS = 1;   // {-Y, +Z, +X}
	private static final int GROUP_MINUS = 2;  // {+Y, -Z, -X}

	private static final int[] GROUP_OF_CORE = {
			GROUP_SELF,
			GROUP_PLUS, GROUP_MINUS, GROUP_MINUS, GROUP_PLUS, GROUP_MINUS, GROUP_PLUS,
	};

	/** Group pattern index -> colour, straight from the simulator. */
	public static final int[] PATTERN_COLORS = {
			0xFF3B30, 0x8E1B18, 0xFF9A3D, 0xA85200, 0xFF5FA8, 0xA81B63,
	};

	/** Colour for every position that does not fit the 3-group structure. */
	public static final int COLOR_BAD = 0x6E6E6E;

	private final int[] colors;

	public GroupPalette()
	{
		OrderTable table = OrderTable.get();
		this.colors = new int[table.orderCount()];

		for (int i = 0; i < this.colors.length; i++) {
			int pattern = table.groupPattern(i);
			this.colors[i] = HighContrastPalette.opaque(
					pattern < 0 ? COLOR_BAD : PATTERN_COLORS[pattern]);
		}
	}

	@Override
	public int color(int orderIndex)
	{
		return this.colors[orderIndex];
	}

	@Override
	public String translationKey()
	{
		return "redstone_dust_order.palette.group3";
	}
}