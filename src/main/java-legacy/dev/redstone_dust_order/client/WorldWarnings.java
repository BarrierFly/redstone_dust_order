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

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * World-join warnings, for 1.19.4 - 1.21.x.
 *
 * <p>Nothing to warn about on these versions: the experimental redstone feature
 * that randomises update order does not exist before 26.x. The method is kept,
 * with its signature, so the entry point needs no version branch to call it.
 */
public final class WorldWarnings
{
	private WorldWarnings() {}

	public static void onJoinWorld(ClientLevel level) {}

	/**
	 * Always false here; see the 26.x copy for what this is about.
	 */
	public static boolean isUpdateOrderRandomised(ClientLevel level)
	{
		return false;
	}
}