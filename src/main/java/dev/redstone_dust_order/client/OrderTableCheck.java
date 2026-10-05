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

import dev.redstone_dust_order.order.OrderTable;

/**
 * Runtime check that the 7x16x16 lookup table still agrees with the arithmetic
 * it was generated from.
 *
 * <p>Cheap enough to run on demand from {@code /rsusoc check} (a million
 * comparisons, well under a frame) and it catches the only failure mode that
 * would be genuinely dangerous: someone editing the compressed table by hand and
 * silently colouring the world wrong.
 */
public final class OrderTableCheck
{
	private OrderTableCheck() {}

	public static boolean tableMatchesReferenceArithmetic()
	{
		OrderTable table = OrderTable.get();
		return dev.redstone_dust_order.order.OrderTableBuilder
				.verifyTable(table.rawTable(), table.orderKeys()) == 0;
	}
}