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
 * The browser simulator's "update order" mode, reproduced exactly: a Fourier
 * embedding of the permutation itself.
 *
 * <p>Each of the 60 orders gets its own colour, so this mode shows you exactly
 * which order you are looking at — the one the design notes call an
 * <em>exploration</em> mode, and not the default, because some of those 60
 * colours land only 9 apart in RGB. On a thin, shaded dust line you will not
 * tell them apart; use {@link HighContrastPalette} for that.
 *
 * <p>The trigonometry here only runs while the palette is being built, never on
 * the render path. See {@code README} for the derivation.
 */
public final class FourierPalette implements Palette
{
	/** Matches the simulator's {@code scale} constant. */
	private static final double SCALE = 6.0;

	private final int[] colors;

	public FourierPalette()
	{
		OrderTable table = OrderTable.get();
		this.colors = new int[table.orderCount()];

		for (int i = 0; i < this.colors.length; i++) {
			this.colors[i] = HighContrastPalette.opaque(
					colorOfTraversal(RedstoneHash.unpackOrder(table.orderKey(i))));
		}
	}

	/**
	 * The simulator's {@code permToRgb}, operating on a traversal order (the
	 * core index at each position in the traversal).
	 *
	 * <p>The idea: standardise the order, read it out as a sum over the first
	 * three DFT components of length 7, and map each of those onto one colour
	 * channel. Adjacent permutations therefore get adjacent colours.
	 */
	static int colorOfTraversal(int[] seq)
	{
		int n = RedstoneHash.CORE_COUNT;

		double sum = 0;
		for (int i = 0; i < n; i++) sum += seq[i];
		double mean = sum / n;

		double sqSum = 0;
		for (int i = 0; i < n; i++) {
			double d = seq[i] - mean;
			sqSum += d * d;
		}
		double std = Math.sqrt(sqSum / n);
		if (std < 1e-12) return 0x808080;

		double r = 0;
		double g = 0;
		double b = 0;

		for (int i = 0; i < n; i++) {
			double x = (seq[i] - mean) / std;
			double angle = 2 * Math.PI * i / n;
			r += x * Math.cos(angle);
			g += x * Math.sin(angle);
			b += x * Math.cos(2 * angle);
		}

		return (clip255(r / SCALE * 127 + 128) << 16)
				| (clip255(g / SCALE * 127 + 128) << 8)
				| clip255(b / SCALE * 127 + 128);
	}

	private static int clip255(double v)
	{
		return (int) Math.round(Math.max(0, Math.min(255, v)));
	}

	@Override
	public int color(int orderIndex)
	{
		return this.colors[orderIndex];
	}

	@Override
	public String translationKey()
	{
		return "redstone_dust_order.palette.fourier";
	}

	/**
	 * Smallest pairwise RGB distance over all 60 colours, minimised across the
	 * uniform gains vanilla applies to the dust texture. This is the number that
	 * motivates not making this palette the default.
	 */
	public static double worstCaseSeparation()
	{
		double[] gains = {1.000, 0.996, 0.920, 0.851};
		double worst = Double.MAX_VALUE;
		FourierPalette palette = new FourierPalette();

		for (int i = 0; i < palette.colors.length; i++) {
			for (int j = i + 1; j < palette.colors.length; j++) {
				for (double gain : gains) {
					int ci = palette.colors[i];
					int cj = palette.colors[j];
					double dr = (((ci >> 16) & 0xFF) - ((cj >> 16) & 0xFF)) * gain;
					double dg = (((ci >> 8) & 0xFF) - ((cj >> 8) & 0xFF)) * gain;
					double db = ((ci & 0xFF) - (cj & 0xFF)) * gain;
					worst = Math.min(worst, Math.sqrt(dr * dr + dg * dg + db * db));
				}
			}
		}

		return worst;
	}
}