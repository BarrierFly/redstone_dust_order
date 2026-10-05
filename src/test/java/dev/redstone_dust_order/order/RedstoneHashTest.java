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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the arithmetic down to the exact values the design notes were derived
 * from. These are the tests that would catch someone "tidying" the model into
 * something that still looks plausible but is no longer what vanilla does.
 */
class RedstoneHashTest
{
	@Test
	@DisplayName("hashCode matches Vec3i.hashCode")
	void hashMatchesVec3i()
	{
		assertEquals(0, RedstoneHash.hash(0, 0, 0));
		assertEquals(1, RedstoneHash.hash(1, 0, 0));
		assertEquals(31, RedstoneHash.hash(0, 1, 0));
		assertEquals(961, RedstoneHash.hash(0, 0, 1));

		// x + 31 * (y + 31 * z), the other way of writing the same thing.
		for (int x = -3; x <= 3; x++) {
			for (int y = -3; y <= 3; y++) {
				for (int z = -3; z <= 3; z++) {
					assertEquals(x + 31 * (y + 31 * z), RedstoneHash.hash(x, y, z));
				}
			}
		}
	}

@Test
	@DisplayName("L is masked to 16 bits even when the hash has wrapped")
	void nibbleExtraction()
	{
		// (0,0,0) -> h == 0, so everything is zero.
		assertEquals(0, RedstoneHash.low16(0));
		assertEquals(0, RedstoneHash.nibbleA(0));
		assertEquals(0, RedstoneHash.nibbleB(0));

		// L is the low 16 bits of the hash, so it must be in range even when the
		// hash itself has wrapped round to something negative.
		assertEquals(0xFFFF, RedstoneHash.low16(-1));
		assertEquals(0xF, RedstoneHash.nibbleA(-1));
		assertEquals(0xF, RedstoneHash.nibbleB(-1));

		assertEquals(0, RedstoneHash.low16(1 << 16));
		assertEquals(0, RedstoneHash.nibbleA(1 << 16));
		assertEquals(1, RedstoneHash.nibbleB(1 << 16));
	}

	@Test
	@DisplayName("the origin reproduces the reference bucket vector and order")
	void originMatchesReference()
	{
		int hash = RedstoneHash.hash(0, 0, 0);
		assertEquals(RedstoneHash.MODE_BORROW_ZYX, RedstoneHash.modeOfLow16(0));

		int[] buckets = new int[RedstoneHash.CORE_COUNT];
		RedstoneHash.bucketsAt(hash, buckets);

		// In the order the cores are inserted: O, -Y, +Y, -Z, +Z, -X, +X.
		assertArrayEquals(new int[] {0, 14, 15, 0, 1, 0, 1}, buckets);

		assertEquals("O -> -Z -> -X -> +Z -> +X -> -Y -> +Y",
				RedstoneHash.describeOrder(
						RedstoneHash.packOrder(RedstoneHash.traversalAt(hash, new int[RedstoneHash.CORE_COUNT]))));
	}

	@Test
	@DisplayName("mode boundaries land exactly where the deltas say they should")
	void modeBoundaries()
	{
		// 0: -Z, -Y and -X all borrow
		assertEquals(RedstoneHash.MODE_BORROW_ZYX, RedstoneHash.modeOfLow16(0));
		assertEquals(RedstoneHash.MODE_BORROW_ZY, RedstoneHash.modeOfLow16(1));
		assertEquals(RedstoneHash.MODE_BORROW_ZY, RedstoneHash.modeOfLow16(30));
		// 31: -Y stops borrowing, only -Z does
		assertEquals(RedstoneHash.MODE_BORROW_Z, RedstoneHash.modeOfLow16(31));
		assertEquals(RedstoneHash.MODE_BORROW_Z, RedstoneHash.modeOfLow16(960));
		// 961: nothing borrows any more
		assertEquals(RedstoneHash.MODE_SAFE, RedstoneHash.modeOfLow16(961));
		assertEquals(RedstoneHash.MODE_SAFE, RedstoneHash.modeOfLow16(64574));
		// 65536 - 961: +Z starts carrying
		assertEquals(RedstoneHash.MODE_CARRY_Z, RedstoneHash.modeOfLow16(64575));
		assertEquals(RedstoneHash.MODE_CARRY_Z, RedstoneHash.modeOfLow16(65504));
		// 65536 - 31: +Y joins
		assertEquals(RedstoneHash.MODE_CARRY_ZY, RedstoneHash.modeOfLow16(65505));
		assertEquals(RedstoneHash.MODE_CARRY_ZY, RedstoneHash.modeOfLow16(65534));
		// 65536 - 1: +X joins and only L == 65535 is left
		assertEquals(RedstoneHash.MODE_CARRY_ZYX, RedstoneHash.modeOfLow16(65535));
	}

	@Test
	@DisplayName("the compressed carry table reproduces the arithmetic everywhere")
	void compressedCarryTableIsExact()
	{
		assertEquals(0, OrderTableBuilder.verifyCompressedModel());
	}

	@Test
	@DisplayName("the two extreme modes only exist at one L, so they pin A")
	void extremeModesPinNibbleA()
	{
		for (int l = 0; l < 1 << 16; l++) {
			int mode = RedstoneHash.modeOfLow16(l);

			assertEquals(mode != RedstoneHash.MODE_BORROW_ZYX || (l & 0xF) == 0,
					OrderTableBuilder.isReachable(mode, l & 0xF));
			assertEquals(mode != RedstoneHash.MODE_CARRY_ZYX || (l & 0xF) == 15,
					OrderTableBuilder.isReachable(mode, l & 0xF));
		}
	}

@Test
	@DisplayName("shifting 16 along an axis always leaves A alone")
	void sixteenBlockShiftKeepsNibbleA()
	{
		// 1, 31 and 961 are each +/-1 mod 16, so a 16-block shift adds 16, 496 or
		// 15376 to the hash: a multiple of 16 in every case. This part is exact
		// everywhere, no caveats.
		for (int x = -200; x <= 200; x += 13) {
			for (int y = -200; y <= 200; y += 13) {
				for (int z = -200; z <= 200; z += 13) {
					int hash = RedstoneHash.hash(x, y, z);
					assertEquals(RedstoneHash.nibbleA(hash), RedstoneHash.nibbleA(hash + 16));
					assertEquals(RedstoneHash.nibbleA(hash), RedstoneHash.nibbleA(hash + 496));
					assertEquals(RedstoneHash.nibbleA(hash), RedstoneHash.nibbleA(hash + 15376));
				}
			}
		}
	}

	@Test
	@DisplayName("a 16-block shift keeps the buckets exactly when mode and B are unchanged")
	void sixteenBlockShiftKeepsBucketsWhenNothingCrosses()
	{
		// The design notes call the pattern "strictly 16-periodic along every
		// axis". A is, but the buckets also depend on B and on which cores cross
		// the 16-bit window, and those *do* move under a 16-block shift along Y or
		// Z (which shift the hash by 496 and 15376, not by 16). So the honest
		// statement is the conditional one, and this test is it.
		for (int x = -300; x <= 300; x += 11) {
			for (int y = -300; y <= 300; y += 11) {
				for (int z = -300; z <= 300; z += 11) {
					int hash = RedstoneHash.hash(x, y, z);

					for (int shift : new int[] {16, 496, 15376}) {
						int moved = hash + shift;

						boolean modeAndNibbleBMatch =
								RedstoneHash.modeOf(hash) == RedstoneHash.modeOf(moved)
										&& RedstoneHash.nibbleB(hash) == RedstoneHash.nibbleB(moved);

						if (!modeAndNibbleBMatch) continue;

						assertArrayEquals(bucketsOf(hash), bucketsOf(moved),
								"buckets differ at hash " + hash + " shifted by " + shift);
					}
				}
			}
		}
	}

@Test
	@DisplayName("away from the window boundary the order index really is 16-periodic")
	void sixteenBlockPeriodInTheSafeInterior()
	{
		OrderTable table = OrderTable.get();

// Bounds chosen so that neither hash can leave the safe window. A shift of
		// +15376 along Z has to stay at or below 64574, the point where +Z starts
		// carrying, so the binding constraint on the starting L is 49198.
		for (int x = -600; x <= 600; x += 7) {
			for (int y = -600; y <= 600; y += 7) {
				for (int z = -600; z <= 600; z += 7) {
					int low = RedstoneHash.low16(RedstoneHash.hash(x, y, z));
					if (low < 3000 || low > 49000) continue;

					int base = table.orderIndexAt(x, y, z);

					assertEquals(base, table.orderIndexAt(x + 16, y, z), "X+16 at " + x + "," + y + "," + z);
					assertEquals(base, table.orderIndexAt(x, y + 16, z), "Y+16 at " + x + "," + y + "," + z);
					assertEquals(base, table.orderIndexAt(x, y, z + 16), "Z+16 at " + x + "," + y + "," + z);
				}
			}
		}
	}

	@Test
	@DisplayName("a 16-block shift really does break the period near the top of the L range")
	void sixteenBlockShiftBreaksNearTheBoundary()
	{
// Not a defect, the reason the previous test needs a margin: translating
		// along Z by 16 adds 15376 to the hash, so for large L it pushes the hash
		// past the point where +Z starts carrying, and the order changes. Worth
		// pinning so nobody "simplifies" the model on the assumption that 16
		// always divides evenly.
		OrderTable table = OrderTable.get();
		int found = 0;

		for (int z = 0; z < 90 && found == 0; z++) {
			for (int x = 0; x < 16 && found == 0; x++) {
				for (int y = 0; y < 16 && found == 0; y++) {
					int low = RedstoneHash.low16(RedstoneHash.hash(x, y, z));
					if (low < 50160) continue;

					if (table.orderIndexAt(x, y, z) != table.orderIndexAt(x, y, z + 16)) found++;
				}
			}
		}

		assertTrue(found > 0, "expected at least one position where a Z+16 shift changes the order");
	}

	private static int[] bucketsOf(int hash)
	{
		int[] buckets = new int[RedstoneHash.CORE_COUNT];
		RedstoneHash.bucketsAt(hash, buckets);
		return buckets;
	}

	@Test
	@DisplayName("the lookup table matches the arithmetic for all 2^20 classes")
	void tableMatchesArithmetic()
	{
		OrderTable table = OrderTable.get();
		assertEquals(0, OrderTableBuilder.verifyTable(table.rawTable(), table.orderKeys()));
	}

	@Test
	@DisplayName("overflowing coordinates still give a valid answer")
	void overflowIsIntended()
	{
		OrderTable table = OrderTable.get();

		// Far outside any real world, but the model is defined on the wrapped
		// value rather than on mathematical integers.
		int[] traversal = new int[RedstoneHash.CORE_COUNT];
		int hash = RedstoneHash.hash(Integer.MAX_VALUE, Integer.MIN_VALUE, 123456789);

		int expected = RedstoneHash.packOrder(RedstoneHash.traversalAt(hash, traversal));
		assertEquals(expected, table.orderKey(table.orderIndexAt(Integer.MAX_VALUE, Integer.MIN_VALUE, 123456789)));
	}

	@Test
	@DisplayName("pack and unpack round-trip")
	void packUnpackRoundTrip()
	{
		int[] cores = {6, 0, 4, 1, 5, 2, 3};
		assertArrayEquals(cores, RedstoneHash.unpackOrder(RedstoneHash.packOrder(cores)));
	}

	@Test
	@DisplayName("stable sort keeps insertion order inside a bucket")
	void stableSortWithinBucket()
	{
		// At the origin, O and -Z/-X land in bucket 0 and 14; the traversal must
		// keep them in insertion order rather than arbitrary order.
		int[] traversal = RedstoneHash.traversalAt(0, new int[RedstoneHash.CORE_COUNT]);
		assertEquals(RedstoneHash.CORE_SELF, traversal[0]);
		assertTrue(traversal[1] == RedstoneHash.CORE_NEG_Z);
		assertEquals(RedstoneHash.CORE_NEG_X, traversal[2]);
	}
}