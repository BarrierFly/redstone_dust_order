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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.redstone_dust_order.order.OrderTable;

/**
 * The blend and the disabled path. Both are pure functions, so they are worth
 * pinning even though the interesting logic lives further down.
 */
class OrderColorsTest
{
	private static final int VANILLA_POWER_7 = 0xFFB02000;

@Test
	@DisplayName("blend is linear and stays opaque")
	void blendIsLinear()
	{
		assertEquals(0xFF000000, OrderColors.blend(0xFF000000, 0xFFFFFFFF, 0.0f) & 0xFF000000);
		assertEquals(0xFF000000, OrderColors.blend(0xFF000000, 0xFFFFFFFF, 1.0f) & 0xFF000000);

		assertEquals(0xFF000000, OrderColors.blend(0xFF000000, 0xFFFFFFFF, 0.0f));
		assertEquals(0xFFFFFFFF, OrderColors.blend(0xFF000000, 0xFFFFFFFF, 1.0f));

		// Channels truncate rather than round, so the midpoint is 127 not 128.
		assertEquals(0xFF7F7F7F, OrderColors.blend(0xFF000000, 0xFFFFFFFF, 0.5f));
	}

	@Test
	@DisplayName("alpha 0 leaves the vanilla colour completely alone")
	void alphaZeroIsVanilla()
	{
		Config config = Config.INSTANCE;
		float saved = config.alpha();

		try {
			config.setEnabled(true);
			config.setAlpha(0.0f);
			OrderColors.apply(config);

			assertEquals(VANILLA_POWER_7, OrderColors.colorAt(0, 0, 0, VANILLA_POWER_7));
		} finally {
			config.setAlpha(saved);
			config.setEnabled(false);
			OrderColors.apply(config);
		}
	}

	@Test
	@DisplayName("alpha 1 replaces the vanilla colour completely")
	void alphaOneIsOrderColour()
	{
		Config config = Config.INSTANCE;
		float saved = config.alpha();

		try {
			config.setEnabled(true);
			config.setAlpha(1.0f);
			OrderColors.apply(config);

			int colour = OrderColors.colorAt(0, 0, 0, VANILLA_POWER_7);
			assertEquals(0xFF000000, colour & 0xFF000000);
			assertTrue(colour != VANILLA_POWER_7);
		} finally {
			config.setAlpha(saved);
			config.setEnabled(false);
			OrderColors.apply(config);
		}
	}

	@Test
	@DisplayName("disabled means vanilla, whatever the position")
	void disabledIsVanilla()
	{
		Config config = Config.INSTANCE;

		try {
			config.setEnabled(false);
			config.setAlpha(1.0f);
			OrderColors.apply(config);

			for (int x = -32; x < 32; x += 3) {
				assertEquals(VANILLA_POWER_7, OrderColors.colorAt(x, 64, 12, VANILLA_POWER_7));
			}
		} finally {
			config.setEnabled(false);
			OrderColors.apply(config);
		}
	}

	@Test
	@DisplayName("every position resolves to an opaque colour and never the sentinel")
	void everyPositionResolves()
	{
		Config config = Config.INSTANCE;

		try {
			config.setEnabled(true);
			config.setAlpha(1.0f);
			OrderColors.apply(config);

			OrderTable table = OrderTable.get();

			for (int x = -40; x < 40; x++) {
				for (int z = -40; z < 40; z++) {
					for (int y = -40; y < 40; y += 7) {
						int colour = OrderColors.colorAt(x, y, z, VANILLA_POWER_7);
						assertEquals(0xFF000000, colour & 0xFF000000, "not opaque at " + x + "," + y + "," + z);
						assertTrue(colour != VANILLA_POWER_7,
								"fell back to vanilla at " + x + "," + y + "," + z
										+ " — order index " + table.orderIndexAt(x, y, z));
					}
				}
			}
		} finally {
			config.setEnabled(false);
			OrderColors.apply(config);
		}
	}

	@Test
	@DisplayName("alpha is rejected outside 0..1")
	void alphaIsRangeChecked()
	{
		Config config = Config.INSTANCE;
		float saved = config.alpha();

		try {
			config.setAlpha(0.5f);
			assertTrue(!config.setAlpha(-0.1f));
			assertTrue(!config.setAlpha(1.1f));
			assertTrue(!config.setAlpha(Float.NaN));
			assertEquals(0.5f, config.alpha(), 1e-6);

			assertTrue(config.setAlpha(1.0f));
		} finally {
			config.setAlpha(saved);
		}
	}
}