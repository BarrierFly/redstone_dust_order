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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the whole space produces exactly the 60 orders, at exactly the
 * frequencies the design notes recorded.
 */
class OrderTableTest
{
	@Test
	@DisplayName("the entire space contains exactly 60 distinct orders")
	void sixtyDistinctOrders()
	{
		OrderTable table = OrderTable.get();
		assertEquals(60, table.orderCount());
		assertEquals(1 << 20, table.sampleCount());
	}

	@Test
	@DisplayName("no two orders share a packed key")
	void orderKeysAreDistinct()
	{
		OrderTable table = OrderTable.get();
		int[] keys = table.orderKeys();

		for (int i = 0; i < keys.length; i++) {
			for (int j = i + 1; j < keys.length; j++) {
				assertTrue(keys[i] != keys[j], "duplicate order key at " + i + " and " + j);
			}
		}
	}

	@Test
	@DisplayName("the six common orders carry ~97% and match the recorded split")
	void frequenciesMatchDesign()
	{
		OrderTable table = OrderTable.get();

		// 24.26682%, 24.26682%, then 12.13341% four times.
		assertEquals(0.2426682, table.frequency(0), 1e-6);
		assertEquals(0.2426682, table.frequency(1), 1e-6);

		for (int i = 2; i <= 5; i++) {
			assertEquals(0.1213341, table.frequency(i), 1e-6);
		}

		double top6 = 0;
		for (int i = 0; i < 6; i++) {
			top6 += table.frequency(i);
		}
		assertEquals(0.9707, top6, 1e-3);

		// ... and the remaining 54 share the other ~3%.
		double rest = 1.0 - top6;
		assertEquals(0.0293, rest, 1e-3);

		for (int i = 6; i < table.orderCount(); i++) {
			assertTrue(table.frequency(i) < 0.002, "order " + i + " should be rare, got " + table.frequency(i));
		}
	}

@Test
	@DisplayName("the origin is one of the rarest orders, matching the reference table")
	void originOrder()
	{
		OrderTable table = OrderTable.get();
		int index = table.orderIndexAt(0, 0, 0);

		assertEquals("O -> -Z -> -X -> +Z -> +X -> -Y -> +Y", table.describe(index));
		assertEquals(57, index);

		// The reference table lists the origin as its last entry, i.e. one of the
		// four orders that only show up roughly once in a million blocks.
		assertTrue(table.frequency(index) < 1e-3, "origin frequency was " + table.frequency(index));
	}

	@Test
	@DisplayName("every reachable table entry is a real order index")
	void tableEntriesAreInRange()
	{
		OrderTable table = OrderTable.get();
		byte[] raw = table.rawTable();

		for (int mode = 0; mode < RedstoneHash.MODE_COUNT; mode++) {
			for (int a = 0; a < 16; a++) {
				for (int b = 0; b < 16; b++) {
					int value = raw[(mode << 8) | (a << 4) | b];

					if (!OrderTableBuilder.isReachable(mode, a)) {
						assertEquals(OrderTableBuilder.UNREACHABLE, (byte) value, "mode " + mode + " A=" + a);
					} else {
						assertTrue(value >= 0 && value < table.orderCount(),
								"mode " + mode + " A=" + a + " B=" + b + " gave " + value);
					}
				}
			}
		}
	}

	@Test
	@DisplayName("the six common orders are exactly the 3-group ones")
	void commonOrdersAreTheGroupedOnes()
	{
		OrderTable table = OrderTable.get();

		for (int i = 0; i < table.orderCount(); i++) {
			if (table.groupPattern(i) >= 0) {
				assertTrue(i < 6, "order " + i + " fits the 3-group structure but is not one of the six");
			}
		}
	}

@Test
	@DisplayName("sampling a spread of cells turns up all six common orders")
	void aSpreadOfCellsShowsTheCommonOrders()
	{
		OrderTable table = OrderTable.get();
		boolean[] seen = new boolean[table.orderCount()];

		// A single 16-block cell is not enough: B is constant inside one, and the
		// order depends on B as well as A, so one cell only ever shows the orders
		// reachable at that one B. Sampling across cells varies B.
		for (int ox = 0; ox < 16 * 40; ox += 16) {
			for (int oy = 0; oy < 16 * 8; oy += 16) {
				for (int oz = 0; oz < 16 * 8; oz += 16) {
					for (int dx = 0; dx < 16; dx++) {
						for (int dy = 0; dy < 16; dy++) {
							for (int dz = 0; dz < 16; dz++) {
								seen[table.orderIndexAt(ox + dx, oy + dy, oz + dz)] = true;
							}
						}
					}
				}
			}
		}

		for (int i = 0; i < 6; i++) {
			assertTrue(seen[i], "common order " + i + " never appeared");
		}
	}
}