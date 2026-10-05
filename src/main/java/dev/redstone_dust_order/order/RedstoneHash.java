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
 * Pure-integer reimplementation of the vanilla redstone-dust update-order model.
 *
 * <p>Background (see README for the full write-up):
 *
 * <ul>
 *   <li>vanilla collects the 7 "update cores" of a dust position into a
 *       {@code java.util.HashSet<BlockPos>}: the position itself plus its 6
 *       neighbours, always inserted in the order
 *       {@code self -> -Y -> +Y -> -Z -> +Z -> -X -> +X}.</li>
 *   <li>{@code BlockPos} does not override {@code hashCode}, so the value used is
 *       {@code Vec3i.hashCode() == (y + 31 * z) * 31 + x == x + 31y + 961z}.</li>
 *   <li>{@code HashMap} spreads the hash with {@code h ^ (h >>> 16)} and, for a
 *       table of length 16, the bucket is the low 4 bits of that.</li>
 *   <li>Iteration goes bucket-ascending, insertion-order inside a bucket.</li>
 * </ul>
 *
 * <p>Only three numbers of {@code h} matter, and they are all we ever need:
 * the low 16 bits {@code L}, the low 4 bits {@code A = L & 15} and the first
 * 4 bits of the high half {@code B = (h >>> 16) & 15}. See
 * {@link OrderTableBuilder} for how those collapse into a 7x16x16 table.
 */
public final class RedstoneHash
{
	// The 7 update cores, in the order vanilla inserts them into the HashSet.
	public static final int CORE_SELF = 0;
	public static final int CORE_NEG_Y = 1;
	public static final int CORE_POS_Y = 2;
	public static final int CORE_NEG_Z = 3;
	public static final int CORE_POS_Z = 4;
	public static final int CORE_NEG_X = 5;
	public static final int CORE_POS_X = 6;

	public static final int CORE_COUNT = 7;

	/** {@code hashCode} delta of each core relative to the dust position. */
	public static final int[] CORE_DELTA = {0, -31, 31, -961, 961, -1, 1};

	/** Short names, used by the debug/self-check output. */
	public static final String[] CORE_NAME = {"O", "-Y", "+Y", "-Z", "+Z", "-X", "+X"};

	// Carry/borrow "modes". Naming follows the plan: the cores whose neighbour
	// hashCode leaves the 0..65535 window, which is what perturbs B.
	public static final int MODE_SAFE = 0;
	public static final int MODE_CARRY_Z = 1;
	public static final int MODE_CARRY_ZY = 2;
	public static final int MODE_CARRY_ZYX = 3;
	public static final int MODE_BORROW_Z = 4;
	public static final int MODE_BORROW_ZY = 5;
	public static final int MODE_BORROW_ZYX = 6;
	public static final int MODE_COUNT = 7;

	/**
	 * The one number per core that survives the compression: how the core's
	 * high half moves when its low half is added to. -1 = borrow, 0 = nothing,
	 * +1 = carry.
	 */
	private static final int[][] MODE_CARRY = {
			{0, 0, 0, 0, 0, 0, 0},
			{0, 0, 0, 0, 1, 0, 0},
			{0, 0, 1, 0, 1, 0, 0},
			{0, 0, 1, 0, 1, 0, 1},
			{0, 0, 0, -1, 0, 0, 0},
			{0, -1, 0, -1, 0, 0, 0},
			{0, -1, 0, -1, 0, -1, 0},
	};

	/** {@code low16 -> mode}, precomputed so the hot path never branches. */
	private static final byte[] MODE_OF_LOW16 = new byte[1 << 16];

	static {
		for (int l = 0; l < MODE_OF_LOW16.length; l++) {
			MODE_OF_LOW16[l] = (byte) modeOfLow16(l);
		}
	}

	private RedstoneHash() {}

	/**
	 * {@code Vec3i.hashCode()} as used by {@code BlockPos}. Deliberately allowed
	 * to overflow: only the low 16 and bits 16..19 survive into the bucket, and
	 * the model is defined on the wrapped-around value.
	 */
	public static int hash(int x, int y, int z)
	{
		return x + 31 * y + 961 * z;
	}

	/** Low 16 bits of the hashCode. */
	public static int low16(int hash)
	{
		return hash & 0xFFFF;
	}

	/** {@code A}: the bucket-relevant residue of {@link #low16(int)}. */
	public static int nibbleA(int hash)
	{
		return hash & 0xF;
	}

	/** {@code B}: the first 4 bits of the high half, i.e. bits 16..19. */
	public static int nibbleB(int hash)
	{
		return (hash >>> 16) & 0xF;
	}

	/**
	 * Classifies a low-16 value into one of the 7 modes.
	 *
	 * <p>The thresholds are just {@code 65536 - |CORE_DELTA|} and
	 * {@code |CORE_DELTA|}: a core's neighbour crosses the window boundary
	 * exactly when {@code L + delta} leaves 0..65535.
	 */
	public static int modeOfLow16(int low16)
	{
		if (low16 == 0) return MODE_BORROW_ZYX;
		if (low16 <= 30) return MODE_BORROW_ZY;   // -961 and -31 both borrow
		if (low16 <= 960) return MODE_BORROW_Z;    // -961 borrows
		if (low16 <= 64574) return MODE_SAFE;      // 961..64574: nothing crosses
		if (low16 <= 65504) return MODE_CARRY_Z;    // +961 carries
		if (low16 <= 65534) return MODE_CARRY_ZY;   // +961 and +31 carry
		return MODE_CARRY_ZYX;                     // +961, +31 and +1 all carry
	}

	/** Mode of an arbitrary hashCode. */
	public static int modeOf(int hash)
	{
		return modeOfLow16(low16(hash));
	}

	/** Table lookup form of {@link #modeOf(int)}. */
	public static int modeOfFast(int hash)
	{
		return MODE_OF_LOW16[hash & 0xFFFF];
	}

	/**
	 * Reference implementation of the high-half shift for one core: -1 when the
	 * neighbour's low half borrows, +1 when it carries, 0 otherwise.
	 *
	 * <p>This is the uncompressed ground truth. {@link #carryOfFast(int, int)}
	 * must agree with it for every reachable input; that is what the unit tests
	 * pin down.
	 */
	public static int carryOf(int core, int low16)
	{
		int sum = low16 + CORE_DELTA[core];
		if (sum < 0) return -1;
		if (sum >= 1 << 16) return 1;
		return 0;
	}

	/** Compressed form of {@link #carryOf(int, int)}: one table row per mode. */
	public static int carryOfFast(int mode, int core)
	{
		return MODE_CARRY[mode][core];
	}

	/**
	 * HashMap bucket of one core, from the compressed inputs.
	 *
	 * <p>{@code bucket = (h ^ (h >>> 16)) & 15 = (h & 15) ^ ((h >>> 16) & 15)},
	 * and the two halves move independently, so this only needs {@code A},
	 * {@code B} and the carry.
	 */
	public static int bucket(int nibbleA, int nibbleB, int core, int carry)
	{
		return ((nibbleA + CORE_DELTA[core]) & 0xF) ^ ((nibbleB + carry) & 0xF);
	}

	/**
	 * Packs a traversal order into an int: 3 bits per position, first core in
	 * the low bits. 21 bits total, so it fits in an int and can be used as a
	 * map key / comparison key.
	 */
	public static int packOrder(int[] traversalOrder)
	{
		int key = 0;
		for (int i = 0; i < CORE_COUNT; i++) {
			key |= traversalOrder[i] << (3 * i);
		}
		return key;
	}

	/** Inverse of {@link #packOrder(int[])}. */
	public static int[] unpackOrder(int key)
	{
		int[] out = new int[CORE_COUNT];
		for (int i = 0; i < CORE_COUNT; i++) {
			out[i] = (key >>> (3 * i)) & 0x7;
		}
		return out;
	}

	/**
	 * Traverses the 7 cores for the given compressed inputs and returns the
	 * packed order. Allocation-free: writes into {@code out} and returns it.
	 */
	public static int orderKey(int mode, int nibbleA, int nibbleB, int[] out)
	{
		int[] bucket = new int[CORE_COUNT];

		for (int i = 0; i < CORE_COUNT; i++) {
			out[i] = i;
		}

		for (int core = 0; core < CORE_COUNT; core++) {
			bucket[core] = bucket(nibbleA, nibbleB, core, MODE_CARRY[mode][core]);
		}

		// Stable insertion sort by bucket. Cores are visited in insertion order,
		// so equal buckets keep vanilla's insertion order for free.
		for (int i = 1; i < CORE_COUNT; i++) {
			int core = out[i];
			int key = bucket[core];
			int j = i - 1;
			while (j >= 0 && bucket[out[j]] > key) {
				out[j + 1] = out[j];
				j--;
			}
			out[j + 1] = core;
		}

		return packOrder(out);
	}

	/**
	 * The 7 HashMap buckets at a dust position, from the uncompressed
	 * arithmetic. This is the ground truth that the compressed
	 * {@link #orderKey(int, int, int, int[])} path is checked against.
	 */
	public static void bucketsAt(int hash, int[] out)
	{
		int low16 = hash & 0xFFFF;
		int high4 = (hash >>> 16) & 0xF;

		for (int core = 0; core < CORE_COUNT; core++) {
			int sum = low16 + CORE_DELTA[core];
			int carry = sum < 0 ? -1 : (sum >= 1 << 16 ? 1 : 0);
			int low = sum & 0xFFFF;
			int high = high4 + carry;
			out[core] = (low & 0xF) ^ (high & 0xF);
		}
	}

	/**
	 * The simulator's {@code groupPattern}: which of the 6 "three contiguous
	 * groups" patterns a bucket assignment realises, or -1 if it is not one.
	 *
	 * <p>Requires the buckets, not just the traversal: two cores can sit next to
	 * each other in the traversal and still have different buckets, in which case
	 * the group is not actually a single bucket and the position does not count.
	 */
	public static int groupPattern(int[] buckets)
	{
		int self = buckets[CORE_SELF];
		int plus = buckets[CORE_NEG_Y];
		int minus = buckets[CORE_POS_Y];

		if (buckets[CORE_POS_Z] != plus || buckets[CORE_POS_X] != plus) return -1;
		if (buckets[CORE_NEG_Z] != minus || buckets[CORE_NEG_X] != minus) return -1;
		if (self == plus || self == minus || plus == minus) return -1;

		if (self < plus && self < minus) return plus < minus ? 0 : 1;
		if (plus < self && plus < minus) return self < minus ? 2 : 3;
		return self < plus ? 4 : 5;
	}

	/**
	 * Stable bucket sort of the 7 cores, from the uncompressed arithmetic.
	 * Writes the traversal into {@code out} and returns it.
	 */
	public static int[] traversalAt(int hash, int[] out)
	{
		int[] buckets = new int[CORE_COUNT];
		bucketsAt(hash, buckets);

		for (int i = 0; i < CORE_COUNT; i++) {
			out[i] = i;
		}

		for (int i = 1; i < CORE_COUNT; i++) {
			int core = out[i];
			int key = buckets[core];
			int j = i - 1;
			while (j >= 0 && buckets[out[j]] > key) {
				out[j + 1] = out[j];
				j--;
			}
			out[j + 1] = core;
		}

		return out;
	}

	/** {@code "O -> -Z -> -X -> ..."} for a packed order. */
	public static String describeOrder(int orderKey)
	{
		int[] cores = unpackOrder(orderKey);
		StringBuilder sb = new StringBuilder(CORE_COUNT * 5);
		for (int i = 0; i < CORE_COUNT; i++) {
			if (i > 0) sb.append(" -> ");
			sb.append(CORE_NAME[cores[i]]);
		}
		return sb.toString();
	}
}