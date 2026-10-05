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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Build-time expansion of {@link RedstoneHash} into the 7x16x16 lookup table
 * that the client actually queries, plus the frequency statistics that rank the
 * 60 distinct orders.
 *
 * <p>Why the table is small enough: the bucket of a core is
 * {@code ((A + delta) & 15) ^ ((B + carry) & 15)}. The {@code delta} half is
 * fully determined by {@code A} (7 constants), and the {@code carry} half is
 * fully determined by which cores cross the 16-bit window, which is exactly the
 * 7-way mode classification. So {@code (mode, A, B)} — 7 * 16 * 16 = 1792
 * combinations — pins down all 7 buckets, and therefore the whole traversal.
 *
 * <p>The sweep deliberately goes through {@link RedstoneHash#bucketsAt} and
 * {@link RedstoneHash#traversalAt}, i.e. the uncompressed arithmetic, so the
 * table is derived from the model rather than from the compressed
 * {@code MODE_CARRY} table it is meant to validate. {@link #verifyCompressedModel()}
 * and {@link #verifyTable(byte[])} then check the two agree.
 */
public final class OrderTableBuilder
{
	/** Number of distinct {@code (B, L)} classes; the whole search space. */
	public static final int SAMPLE_COUNT = 1 << 20;

	/**
	 * Table marker for {@code (mode, A)} pairs no real position can produce.
	 * Negative so an accidental read is loud rather than a plausible colour; the
	 * tint source treats it as "fall back to the vanilla colour".
	 */
	public static final byte UNREACHABLE = -1;

	private OrderTableBuilder() {}

	/** Everything {@link OrderTable} needs, in one immutable bundle. */
	public static final class Built
	{
		public final byte[] orderByModeAB;
		public final int[] orderKeys;
		public final long[] orderCounts;
		public final byte[] groupPatterns;
		public final long sampleCount;

		Built(byte[] orderByModeAB, int[] orderKeys, long[] orderCounts,
				byte[] groupPatterns, long sampleCount)
		{
			this.orderByModeAB = orderByModeAB;
			this.orderKeys = orderKeys;
			this.orderCounts = orderCounts;
			this.groupPatterns = groupPatterns;
			this.sampleCount = sampleCount;
		}
	}

	/** Sweeps the space, ranks the orders by frequency, fills the table. */
	public static Built build()
	{
		Map<Integer, long[]> counts = new HashMap<Integer, long[]>();
		Map<Integer, Byte> patterns = new HashMap<Integer, Byte>();
		int[] traversal = new int[RedstoneHash.CORE_COUNT];
		int[] buckets = new int[RedstoneHash.CORE_COUNT];

		for (int b = 0; b < 16; b++) {
			for (int l = 0; l < 1 << 16; l++) {
				int hash = (b << 16) | l;

				RedstoneHash.bucketsAt(hash, buckets);
				int key = RedstoneHash.packOrder(RedstoneHash.traversalAt(hash, traversal));

				long[] entry = counts.get(key);
				if (entry == null) {
					counts.put(key, new long[] {1L});
					patterns.put(key, Byte.valueOf((byte) RedstoneHash.groupPattern(buckets)));
				} else {
					entry[0]++;
				}
			}
		}

		List<Map.Entry<Integer, long[]>> ranked =
				new ArrayList<Map.Entry<Integer, long[]>>(counts.entrySet());

		// Frequency descending, packed order ascending as a deterministic
		// tie-break, so the table is bit-identical on every JVM.
		Collections.sort(ranked, new Comparator<Map.Entry<Integer, long[]>>()
		{
			@Override
			public int compare(Map.Entry<Integer, long[]> a, Map.Entry<Integer, long[]> b)
			{
				int byCount = Long.compare(b.getValue()[0], a.getValue()[0]);
				if (byCount != 0) return byCount;
				return Integer.compare(a.getKey().intValue(), b.getKey().intValue());
			}
		});

		int orderCount = ranked.size();
		int[] orderKeys = new int[orderCount];
		long[] orderCounts = new long[orderCount];
		byte[] groupPatterns = new byte[orderCount];
		Map<Integer, Integer> indexOfKey = new HashMap<Integer, Integer>(orderCount * 2);

		for (int i = 0; i < orderCount; i++) {
			Integer key = ranked.get(i).getKey();
			orderKeys[i] = key.intValue();
			orderCounts[i] = ranked.get(i).getValue()[0];
			groupPatterns[i] = patterns.get(key).byteValue();
			indexOfKey.put(key, Integer.valueOf(i));
		}

		byte[] orderByModeAB = new byte[RedstoneHash.MODE_COUNT << 8];

		for (int mode = 0; mode < RedstoneHash.MODE_COUNT; mode++) {
			for (int a = 0; a < 16; a++) {
				for (int b = 0; b < 16; b++) {
					int offset = (mode << 8) | (a << 4) | b;

					if (!isReachable(mode, a)) {
						orderByModeAB[offset] = UNREACHABLE;
						continue;
					}

					int key = RedstoneHash.orderKey(mode, a, b, traversal);
					Integer index = indexOfKey.get(Integer.valueOf(key));

					if (index == null) {
						throw new IllegalStateException(
								"order " + RedstoneHash.describeOrder(key)
										+ " (mode=" + mode + " A=" + a + " B=" + b
										+ ") never occurred during the sweep");
					}

					orderByModeAB[offset] = index.byteValue();
				}
			}
		}

		return new Built(orderByModeAB, orderKeys, orderCounts, groupPatterns, SAMPLE_COUNT);
	}

	/**
	 * Which {@code (mode, A)} pairs a real position can actually produce.
	 *
	 * <p>{@code A} is the low 4 bits of {@code L}, so the two extreme modes —
	 * the ones that only trigger at a single {@code L} — pin {@code A} too:
	 * {@code MODE_BORROW_ZYX} only exists at {@code L == 0} ({@code A == 0}) and
	 * {@code MODE_CARRY_ZYX} only at {@code L == 65535} ({@code A == 15}). Every
	 * other mode covers a whole range and admits all 16 values of {@code A}.
	 */
	public static boolean isReachable(int mode, int nibbleA)
	{
		switch (mode) {
			case RedstoneHash.MODE_BORROW_ZYX:
				return nibbleA == 0;
			case RedstoneHash.MODE_CARRY_ZYX:
				return nibbleA == 15;
			default:
				return true;
		}
	}

	/**
	 * Checks that {@link RedstoneHash#carryOfFast(int, int)} reproduces
	 * {@link RedstoneHash#carryOf(int, int)} for every core and every one of the
	 * 65536 low-16 values. Returns the number of mismatches (0 = good).
	 */
	public static int verifyCompressedModel()
	{
		int mismatches = 0;

		for (int l = 0; l < 1 << 16; l++) {
			int mode = RedstoneHash.modeOfLow16(l);

			for (int core = 0; core < RedstoneHash.CORE_COUNT; core++) {
				if (RedstoneHash.carryOf(core, l) != RedstoneHash.carryOfFast(mode, core)) {
					mismatches++;
				}
			}
		}

		return mismatches;
	}

	/**
	 * Checks that the 7x16x16 table agrees with the reference arithmetic for
	 * every {@code (B, L)} class. Returns the number of mismatches (0 = good).
	 */
	public static int verifyTable(byte[] orderByModeAB, int[] orderKeys)
	{
		Map<Integer, Integer> indexOfKey = new HashMap<Integer, Integer>();
		for (int i = 0; i < orderKeys.length; i++) {
			indexOfKey.put(Integer.valueOf(orderKeys[i]), Integer.valueOf(i));
		}

		int mismatches = 0;
		int[] traversal = new int[RedstoneHash.CORE_COUNT];

		for (int b = 0; b < 16; b++) {
			for (int l = 0; l < 1 << 16; l++) {
				int hash = (b << 16) | l;
				int mode = RedstoneHash.modeOfLow16(l);
				int a = l & 0xF;

				int expected = RedstoneHash.packOrder(RedstoneHash.traversalAt(hash, traversal));
				Integer index = indexOfKey.get(Integer.valueOf(expected));
				int got = orderByModeAB[(mode << 8) | (a << 4) | b];

				if (index == null || index.intValue() != got) mismatches++;
			}
		}

		return mismatches;
	}

	/** Convenience for tooling and tests: the packed orders, frequency order. */
	public static int[] allOrderKeys(Built built)
	{
		return Arrays.copyOf(built.orderKeys, built.orderKeys.length);
	}
}