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
 * The runtime lookup table: {@code position -> index into the 60 distinct update
 * orders}.
 *
 * <p>Built exactly once, then immutable, because it is read from the chunk
 * meshing threads. There is no shared mutable state anywhere in this package,
 * which is what makes it safe to call {@link #orderIndexAt(int, int, int)}
 * concurrently.
 *
 * <p>The hot path is four integer operations plus two array loads and no
 * branches:
 *
 * <pre>
 * int h    = x + 31 * y + 961 * z;
 * int mode = MODE_OF_LOW16[h &amp; 0xFFFF];
 * return table[(mode &lt;&lt; 8) | ((h &amp; 15) &lt;&lt; 4) | ((h &gt;&gt;&gt; 16) &amp; 15)];
 * </pre>
 *
 * <p>Note what is <em>not</em> stored here: colours. The table maps a position
 * to an <em>order index</em>, and the palette turns that into a colour, so
 * switching palette or alpha costs nothing and needs no rebuild of this table.
 * Storing colours directly (a {@code int[7][16][16]}) would have made every
 * palette change a full re-derivation for no benefit.
 */
public final class OrderTable
{
	private static final OrderTable INSTANCE = new OrderTable();

	private final byte[] orderByModeAB;
	private final int[] orderKeys;
	private final long[] orderCounts;
	private final byte[] groupPatterns;
	private final long sampleCount;

	private OrderTable()
	{
		OrderTableBuilder.Built built = OrderTableBuilder.build();
		this.orderByModeAB = built.orderByModeAB;
		this.orderKeys = built.orderKeys;
		this.orderCounts = built.orderCounts;
		this.groupPatterns = built.groupPatterns;
		this.sampleCount = built.sampleCount;
	}

	/** The single shared instance, built on first access. */
	public static OrderTable get()
	{
		return INSTANCE;
	}

	/** Number of distinct update orders in the whole space; 60. */
	public int orderCount()
	{
		return orderKeys.length;
	}

	/** Number of {@code (B, L)} classes swept while building; 1048576. */
	public long sampleCount()
	{
		return sampleCount;
	}

	/**
	 * Order index at a block position. The whole point of the mod: no branches,
	 * no floats, no allocation.
	 */
	public int orderIndexAt(int x, int y, int z)
	{
		int hash = x + 31 * y + 961 * z;
		int mode = RedstoneHash.modeOfFast(hash);
		return this.orderByModeAB[(mode << 8) | ((hash & 0xF) << 4) | ((hash >>> 16) & 0xF)];
	}

	/** Order index straight from the compressed inputs. */
	public int orderIndex(int mode, int nibbleA, int nibbleB)
	{
		return this.orderByModeAB[(mode << 8) | (nibbleA << 4) | nibbleB];
	}

	/** The packed 7-core traversal order for an order index. */
	public int orderKey(int orderIndex)
	{
		return orderKeys[orderIndex];
	}

	/** {@code "O -> -Z -> -X -> ..."} for an order index. */
	public String describe(int orderIndex)
	{
		return RedstoneHash.describeOrder(orderKeys[orderIndex]);
	}

	/** How many of the {@link #sampleCount()} classes produce this order. */
	public long count(int orderIndex)
	{
		return orderCounts[orderIndex];
	}

	/** Fraction of all {@code (B, L)} classes producing this order, in [0, 1]. */
	public double frequency(int orderIndex)
	{
		return (double) orderCounts[orderIndex] / (double) sampleCount;
	}

	/** Raw 7x16x16 table, for tests and for the offline table dump. */
	public byte[] rawTable()
	{
		return orderByModeAB.clone();
	}

	/** The packed orders, frequency order. */
	public int[] orderKeys()
	{
		return orderKeys.clone();
	}

	/**
	 * The "3 group" pattern index for an order (0..5), or -1 when the position
	 * does not fit the neat 3-group structure. Precomputed during the sweep
	 * because it needs the actual buckets, not just the traversal.
	 */
	public int groupPattern(int orderIndex)
	{
		return groupPatterns[orderIndex];
	}
}