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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.redstone_dust_order.order.OrderKind;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The handful of settings the mod has, persisted next to the game.
 *
 * <p>Deliberately a plain properties file: 4 keys, no nesting, and no JSON
 * library in the way — which also means the same file parses identically on all
 * six supported Minecraft versions.
 *
 * <p>Defaults follow the design: off until the player binds the key and turns it
 * on, the readable high-contrast palette, and full replacement of the vanilla
 * tint ({@code alpha = 1}).
 */
public final class Config
{
	private static final String FILE_NAME = "redstone_dust_order.properties";

	public static final boolean DEFAULT_ENABLED = false;
	public static final OrderKind DEFAULT_KIND = OrderKind.HIGH_CONTRAST;
	public static final float DEFAULT_ALPHA = 1.0f;

	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_KIND = "palette";
	private static final String KEY_ALPHA = "alpha";

	private boolean enabled = DEFAULT_ENABLED;
	private OrderKind kind = DEFAULT_KIND;
	private float alpha = DEFAULT_ALPHA;

	public static final Config INSTANCE = new Config();

	private Config() {}

	public boolean enabled()
	{
		return enabled;
	}

	public OrderKind kind()
	{
		return kind;
	}

	public float alpha()
	{
		return alpha;
	}

	public boolean setEnabled(boolean value)
	{
		if (this.enabled == value) return false;
		this.enabled = value;
		return true;
	}

	public boolean setKind(OrderKind value)
	{
		if (this.kind == value) return false;
		this.kind = value;
		return true;
	}

	/** Returns false (and changes nothing) when {@code value} is out of range. */
	public boolean setAlpha(float value)
	{
		if (value < 0.0f || value > 1.0f || Float.isNaN(value)) return false;
		if (this.alpha == value) return false;
		this.alpha = value;
		return true;
	}

	public Path file()
	{
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Reads the file if it exists. A malformed file falls back to defaults. */
	public void load()
	{
		Path path = file();

		if (!Files.exists(path)) {
			// Missing config is the normal first-run state, not an error.
			save();
			return;
		}

		java.util.Properties properties = new java.util.Properties();

		try (InputStream in = Files.newInputStream(path)) {
			properties.load(in);
		} catch (IOException e) {
			RedstoneDustOrderClient.LOGGER.warn("Could not read {}, keeping defaults", path, e);
			return;
		}

		this.enabled = Boolean.parseBoolean(properties.getProperty(KEY_ENABLED, Boolean.toString(DEFAULT_ENABLED)));

		OrderKind parsed = OrderKind.byId(properties.getProperty(KEY_KIND, DEFAULT_KIND.id()));
		if (parsed == null) {
			RedstoneDustOrderClient.LOGGER.warn(
					"Unknown palette '{}', falling back to {}", properties.getProperty(KEY_KIND), DEFAULT_KIND.id());
			parsed = DEFAULT_KIND;
		}
		this.kind = parsed;

		try {
			this.alpha = clamp(Float.parseFloat(properties.getProperty(KEY_ALPHA, Float.toString(DEFAULT_ALPHA))));
		} catch (NumberFormatException e) {
			RedstoneDustOrderClient.LOGGER.warn("Bad alpha value, falling back to {}", DEFAULT_ALPHA);
			this.alpha = DEFAULT_ALPHA;
		}
	}

	public void save()
	{
		java.util.Properties properties = new java.util.Properties();
		properties.setProperty(KEY_ENABLED, Boolean.toString(enabled));
		properties.setProperty(KEY_KIND, kind.id());
		properties.setProperty(KEY_ALPHA, Float.toString(alpha));

		Path path = file();

		try {
			Files.createDirectories(path.getParent());

			try (OutputStream out = Files.newOutputStream(path)) {
				properties.store(out, "Redstone Dust Update Order — delete this file to reset");
			}
		} catch (IOException e) {
			RedstoneDustOrderClient.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private static float clamp(float value)
	{
		if (Float.isNaN(value)) return DEFAULT_ALPHA;
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}