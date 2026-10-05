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
 * The toggle key, on 1.21.10 and 1.21.11.
 *
 * <p>Identical to the 1.19.4 - 1.21.1 copy apart from the category: 1.21.10
 * turned {@code KeyMapping}'s category from a translation key into a
 * {@code KeyMapping.Category} record. {@code Category.MISC} is used rather than a
 * mod-specific category because 26.x also renamed {@code ResourceLocation} to
 * {@code Identifier}, which a custom category would have had to branch around.
 *
 * <p>Registered with no default binding, and bound to
 * {@link GLFW#GLFW_KEY_UNKNOWN} ({@code -1}), which is what vanilla's
 * {@code KeyMapping#isUnbound()} compares against.
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
				KeyMapping.Category.MISC));
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