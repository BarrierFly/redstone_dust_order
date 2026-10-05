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

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;

/**
 * World-join warnings, for 26.x.
 *
 * <p>26.x ships a vanilla experimental feature pack that flips the redstone
 * update order to a random orientation, gated on
 * {@link FeatureFlags#REDSTONE_EXPERIMENTS}. No positional model can predict
 * that, so rather than quietly disabling the mod — a silent change to the
 * player's state — we say so in chat when they join a world that has it on.
 *
 * <p>{@code /rsusoc check} reports the same thing, for players who join with the
 * warning already scrolled off.
 */
public final class WorldWarnings
{
	private WorldWarnings() {}

	public static void onJoinWorld(ClientLevel level)
	{
		if (!isUpdateOrderRandomised(level)) return;

		// 26.x dropped Gui#getChat() in favour of Gui#chatListener(); the second
		// argument is "overlay", and false means it goes to the chat log only.
		Minecraft.getInstance().gui.chatListener().handleSystemMessage(
				Component.translatable("redstone_dust_order.warn.randomized"), false);
	}

	/** True when this world randomises redstone update order, so colours lie. */
	public static boolean isUpdateOrderRandomised(ClientLevel level)
	{
		return level != null && level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS);
	}
}