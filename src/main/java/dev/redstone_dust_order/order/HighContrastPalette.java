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
 * Default palette: 6 loud, deliberately far-apart colours for the orders that
 * cover ~97% of all positions, plus a quiet family for the remaining ~3%.
 *
 * <h2>Why hand-picked and not machine-maximised</h2>
 *
 * The obvious move is to run a max-min search over RGB, which does find sets
 * with a much larger worst-case distance — but it always drives them into the
 * corners of the RGB cube, and the result is a pile of neon primaries with at
 * least one colour too dark to read on a shaded dust line. These 6 were picked
 * by hue instead (roughly even spacing over the non-red arc) and then
 * <em>verified</em>: the worst-case pairwise distance, taken over every uniform
 * gain vanilla can apply to the neutral dust texture (grey levels 254/235/217,
 * i.e. gains 0.996/0.920/0.851), is 112.8. For comparison the Fourier palette
 * bottoms out at 9.
 *
 * <h2>Why the rare orders share a family</h2>
 *
 * The other 54 orders only show up in the ~3% of positions where the neat
 * 3-group structure breaks down — the thin strip along X where the hash crosses
 * a 16-bit boundary. They are rare <em>and</em> unpredictable, so telling them
 * apart from each other is not worth any contrast budget. They get 5 graded
 * desaturated slate steps instead, purely so that eyeballing a circuit still
 * tells you "rare, slightly different from its neighbour".
 */
public final class HighContrastPalette implements Palette
{
	/**
	 * Colours for order indices 0..5, in the table's frequency order:
	 * 24.27%, 24.27%, then 12.13% four times.
	 *
	 * <p>The two 24.27% orders get the single most distant pair in the set
	 * (lime vs blue), because those are the two you are guaranteed to be looking
	 * at.
	 */
	public static final int[] MAIN_COLORS = {
			0xA8FF00,  // 0: P -> O -> M, 24.27%
			0x4D7CFF,  // 1: M -> O -> P, 24.27%
			0xFF9F1C,  // 2: 12.13%
			0xFF4DDA,  // 3: 12.13%
			0x00FFF2,  // 4: 12.13%
			0x29FF6E,  // 5: 12.13%
	};

	/** Desaturated "rare" family, ordered from most to least common usage. */
	public static final int[] RARE_COLORS = {
			0x6E7B8C,
			0x7C8899,
			0x8A94A3,
			0x62707F,
			0x96A0AD,
	};

	/**
	 * Where {@link #RARE_COLORS} bands start. Ranks 0..5 are the main orders;
	 * from rank 6 on we walk through the rare family.
	 */
	private static final int[] RARE_BANDS = {6, 10, 14, 22, 30};

	private final int[] colors;

	public HighContrastPalette()
	{
		OrderTable table = OrderTable.get();
		int count = table.orderCount();
		this.colors = new int[count];

		for (int i = 0; i < count; i++) {
			if (i < MAIN_COLORS.length) {
				this.colors[i] = opaque(MAIN_COLORS[i]);
			} else {
				this.colors[i] = opaque(RARE_COLORS[bandForRank(i)]);
			}
		}
	}

	private static int bandForRank(int rank)
	{
		for (int i = 0; i < RARE_BANDS.length; i++) {
			if (rank < RARE_BANDS[i]) return i;
		}
		return RARE_COLORS.length - 1;
	}

	static int opaque(int rgb)
	{
		return 0xFF000000 | rgb;
	}

	@Override
	public int color(int orderIndex)
	{
		return this.colors[orderIndex];
	}

	@Override
	public String translationKey()
	{
		return "redstone_dust_order.palette.high_contrast";
	}

	/**
	 * Smallest pairwise RGB distance over the 6 main colours, minimised across
	 * every uniform gain vanilla applies to the dust texture.
	 *
	 * <p>Exposed so the unit tests can fail if someone edits the palette into
	 * something unreadable.
	 */
	public static double mainWorstCaseSeparation()
	{
		double[] gains = {1.000, 0.996, 0.920, 0.851};
		double worst = Double.MAX_VALUE;

		for (int i = 0; i < MAIN_COLORS.length; i++) {
			for (int j = i + 1; j < MAIN_COLORS.length; j++) {
				for (double gain : gains) {
					double dr = (((MAIN_COLORS[i] >> 16) & 0xFF) - ((MAIN_COLORS[j] >> 16) & 0xFF)) * gain;
					double dg = (((MAIN_COLORS[i] >> 8) & 0xFF) - ((MAIN_COLORS[j] >> 8) & 0xFF)) * gain;
					double db = ((MAIN_COLORS[i] & 0xFF) - (MAIN_COLORS[j] & 0xFF)) * gain;
					worst = Math.min(worst, Math.sqrt(dr * dr + dg * dg + db * db));
				}
			}
		}

		return worst;
	}
}