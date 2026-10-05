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

package dev.redstone_dust_order.tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import dev.redstone_dust_order.order.OrderKind;
import dev.redstone_dust_order.order.OrderTable;
import dev.redstone_dust_order.order.OrderTableBuilder;
import dev.redstone_dust_order.order.Palette;
import dev.redstone_dust_order.order.RedstoneHash;

/**
 * Dumps the whole {@code (mode, A, B)} space to a PNG, one pixel per entry, so
 * a palette can be judged on paper before anyone builds a circuit to look at.
 *
 * <p>Deliberately a test-scope tool rather than shipped code: it exists to answer
 * "is this palette readable", and that answer does not belong in the jar.
 *
 * <p>Layout: 16 columns for {@code B}, one row per {@code A}, one 16x16 block per
 * mode, left to right. Modes 3 and 6 come out half black on purpose — those are
 * the {@code (mode, A)} pairs no real position can reach.
 *
 * <pre>
 * java -cp &lt;test-classpath&gt; dev.redstone_dust_order.tools.TablePngDump out.png [palette]
 * </pre>
 */
public final class TablePngDump
{
	private static final int TILE = 16;
	private static final int MARGIN = 4;
	private static final int UNREACHABLE_RGB = 0x101010;

	private TablePngDump() {}

	public static void main(String[] args) throws IOException
	{
		File out = new File(args.length > 0 ? args[0] : "order-table.png");

		OrderKind kind = args.length > 1 ? OrderKind.byId(args[1]) : OrderKind.HIGH_CONTRAST;
		if (kind == null) kind = OrderKind.HIGH_CONTRAST;

		OrderTable table = OrderTable.get();
		Palette palette = kind.palette();

		int width = MARGIN + RedstoneHash.MODE_COUNT * (TILE + MARGIN);
		int height = MARGIN + TILE + MARGIN;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		for (int mode = 0; mode < RedstoneHash.MODE_COUNT; mode++) {
			int originX = MARGIN + mode * (TILE + MARGIN);

			for (int a = 0; a < TILE; a++) {
				for (int b = 0; b < TILE; b++) {
					int index = table.orderIndex(mode, a, b);
					int rgb = index < 0 ? UNREACHABLE_RGB : palette.color(index) & 0xFFFFFF;

					image.setRGB(originX + b, MARGIN + a, rgb);
				}
			}
		}

		ImageIO.write(image, "png", out);

		System.out.println("wrote " + out.getAbsolutePath()
				+ " (" + width + "x" + height + ", " + RedstoneHash.MODE_COUNT
				+ " modes, palette " + kind.id() + ", " + table.orderCount() + " orders)");
	}
}