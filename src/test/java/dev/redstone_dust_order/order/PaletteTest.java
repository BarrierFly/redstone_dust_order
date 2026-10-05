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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the palettes: the simulator-derived ones must reproduce the colours
 * recorded in the design notes exactly, and the hand-picked default must stay
 * readable.
 */
class PaletteTest
{
	private static int rgb(int argb)
	{
		return argb & 0xFFFFFF;
	}

	@Test
	@DisplayName("the Fourier palette reproduces the six colours from the design notes")
	void fourierMatchesRecordedColours()
	{
		FourierPalette palette = new FourierPalette();

		// Recorded independently in the design notes as the six main colours.
		int[] expected = {0xB26D31, 0x9E8D2D, 0x5BA739, 0x4B7C64, 0x6B6752, 0x5F8146};
		Set<Integer> expectedSet = new HashSet<Integer>();
		for (int c : expected) expectedSet.add(Integer.valueOf(c));

		// The notes list them as an unordered set, so compare as a set: which of
		// the 60 orders gets which of the six depends on the frequency tie-break.
		for (int i = 0; i < expected.length; i++) {
			assertTrue(expectedSet.contains(Integer.valueOf(rgb(palette.color(i)))),
					"order " + i + " is " + Integer.toHexString(rgb(palette.color(i))));
		}

		Set<Integer> actual = new HashSet<Integer>();
		for (int i = 0; i < expected.length; i++) {
			actual.add(Integer.valueOf(rgb(palette.color(i))));
		}
		assertEquals(expectedSet, actual);
	}

	@Test
	@DisplayName("the Fourier palette gives all 60 orders their own colour")
	void fourierIsInjective()
	{
		FourierPalette palette = new FourierPalette();
		Set<Integer> seen = new HashSet<Integer>();

		for (int i = 0; i < OrderTable.get().orderCount(); i++) {
			assertTrue(seen.add(Integer.valueOf(rgb(palette.color(i)))),
					"two orders share a Fourier colour: " + i);
		}

		assertEquals(60, seen.size());
	}

	@Test
	@DisplayName("the Fourier palette is exactly why it is not the default")
	void fourierHasCollidingColours()
	{
		// Some pair of the 60 ends up only ~8 apart in RGB. That is fine for an
		// exploration mode and fatal as a default, and this test exists so that
		// nobody "fixes" the number by quietly changing the algorithm.
		assertTrue(FourierPalette.worstCaseSeparation() < 12.0,
				"unexpected worst case " + FourierPalette.worstCaseSeparation());
	}

	@Test
	@DisplayName("the 3-group palette uses the simulator's six colours and greys the rest")
	void groupPaletteMatchesSimulator()
	{
		OrderTable table = OrderTable.get();
		GroupPalette palette = new GroupPalette();

		for (int i = 0; i < table.orderCount(); i++) {
			int pattern = table.groupPattern(i);

			if (i < 6) {
				assertTrue(pattern >= 0, "order " + i + " should fit the 3-group structure");
				assertEquals(rgb(GroupPalette.PATTERN_COLORS[pattern]), rgb(palette.color(i)));
			} else {
				assertEquals(-1, pattern, "order " + i + " should not fit the 3-group structure");
				assertEquals(GroupPalette.COLOR_BAD, rgb(palette.color(i)));
			}
		}
	}

	@Test
	@DisplayName("the default palette separates its six main colours")
	void highContrastStaysReadable()
	{
		// Measured over every uniform gain vanilla applies to the neutral dust
		// texture (grey levels 254/235/217 -> gains 0.996/0.920/0.851).
		assertTrue(HighContrastPalette.mainWorstCaseSeparation() > 100.0,
				"main colours drifted together: " + HighContrastPalette.mainWorstCaseSeparation());
	}

@Test
	@DisplayName("the default palette is opaque, with six distinct main colours")
	void highContrastIsOpaque()
	{
		OrderTable table = OrderTable.get();
		HighContrastPalette palette = new HighContrastPalette();

		for (int i = 0; i < table.orderCount(); i++) {
			assertEquals(0xFF000000, palette.color(i) & 0xFF000000, "order " + i + " is not opaque");
		}

		// Only the six common orders are required to be distinguishable from each
		// other; the rare family deliberately shares five greys across 54 orders.
		Set<Integer> main = new HashSet<Integer>();
		for (int i = 0; i < 6; i++) {
			assertTrue(main.add(Integer.valueOf(palette.color(i))), "duplicate main colour at order " + i);
		}
		assertEquals(6, main.size());
	}

	@Test
	@DisplayName("palettes are reachable by id and unknown ids do not throw")
	void kindLookup()
	{
		assertEquals(OrderKind.HIGH_CONTRAST, OrderKind.byId("high_contrast"));
		assertEquals(OrderKind.HIGH_CONTRAST, OrderKind.byId("HIGH_CONTRAST"));
		assertEquals(OrderKind.GROUP3, OrderKind.byId("group3"));
		assertEquals(OrderKind.FOURIER, OrderKind.byId("fourier"));

		// Unknown ids have to come back as null: a typo in the config file must
		// not take the game down.
		assertEquals(null, OrderKind.byId("nonsense"));
	}
}