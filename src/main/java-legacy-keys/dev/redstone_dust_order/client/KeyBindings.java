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

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

/**
 * The toggle key, on 1.19.4 - 1.21.1.
 *
 * <p>Registered with <em>no default binding</em>, on purpose. The mod is off by
 * default, and a player who has not picked a key should not have one — a mod that
 * claims a key on first launch is a bad neighbour. The player binds it in the
 * normal vanilla controls screen.
 *
 * <p>{@link GLFW#GLFW_KEY_UNKNOWN} is {@code -1}, which is exactly what vanilla's
 * own {@code KeyMapping#isUnbound()} compares against.
 *
 * <p>It sits in the vanilla "Miscellaneous" category rather than one of its own:
 * 1.21.10 replaced the category string with a {@code KeyMapping.Category}
 * record, and 26.x then renamed {@code ResourceLocation} to {@code Identifier} on
 * top of that. Two more version branches to save for one cosmetic label is a bad
 * trade.
 */
public final class KeyBindings
{
	static final String TOGGLE_TRANSLATION_KEY = "redstone_dust_order.key.toggle";

	private static KeyMapping toggle;

	private KeyBindings() {}

	public static void register()
	{
		toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				TOGGLE_TRANSLATION_KEY,
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				"key.categories.misc"));
	}

	public static KeyMapping toggle()
	{
		return toggle;
	}

	/** Consumes a pending press, if any. Called once per client tick. */
	public static boolean consumeTogglePress()
	{
		return toggle != null && toggle.consumeClick();
	}
}