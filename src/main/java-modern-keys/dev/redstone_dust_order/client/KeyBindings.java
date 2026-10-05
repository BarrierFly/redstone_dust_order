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

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;

/**
 * The toggle key, on 26.x.
 *
 * <p>Same behaviour as the other eras — registered with no default binding, and
 * left off until the player both binds a key and turns the mod on. Three things
 * are spelled differently on 26.x, which is why this is a separate file rather
 * than a conditional:
 *
 * <ul>
 *   <li>the helper moved from {@code client.keybinding.v1.KeyBindingHelper} to
 *       {@code client.keymapping.v1.KeyMappingHelper}, and gained a second
 *       method;</li>
 *   <li>{@code InputConstants.Type.KEYSYM} was renamed to {@code KEYBOARD};</li>
 *   <li>the unbound key is spelled {@link InputConstants#UNKNOWN} rather than
 *       {@code GLFW_KEY_UNKNOWN}, because the GLFW dependency is no longer
 *       directly on the compile classpath.</li>
 * </ul>
 */
public final class KeyBindings
{
	static final String TOGGLE_TRANSLATION_KEY = "redstone_dust_order.key.toggle";

	private static KeyMapping toggle;

	private KeyBindings() {}

	public static void register()
	{
		toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				TOGGLE_TRANSLATION_KEY,
				InputConstants.Type.KEYBOARD,
				InputConstants.UNKNOWN.getValue(),
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