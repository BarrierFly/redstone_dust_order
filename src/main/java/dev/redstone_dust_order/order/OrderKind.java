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
 * The three built-in ways to turn an update-order index into a colour.
 *
 * <p>All three are pure functions of the order index, so switching between them
 * never touches the lookup table and never needs the table to be rebuilt.
 */
public enum OrderKind
{
	/**
	 * Default. Six hand-picked, deliberately far-apart colours for the orders
	 * that cover ~97% of all positions, plus a calm low-saturation family for the
	 * ~3% of positions where the neat 3-group structure breaks down.
	 */
	HIGH_CONTRAST("high_contrast"),

	/**
	 * The browser simulator's "3 group" mode: 3 hues x 2 shades for the six main
	 * patterns, one grey for everything else.
	 */
	GROUP3("group3"),

	/**
	 * The browser simulator's "update order" mode: a Fourier embedding of the
	 * permutation itself, so every one of the 60 orders gets its own colour.
	 *
	 * <p>Exploration only. It is faithful to the original, but as the design
	 * notes point out some of those 60 colours are only ~9 apart in RGB, so do
	 * not expect to tell them apart on a thin dust line.
	 */
	FOURIER("fourier");

	private final String id;

	OrderKind(String id)
	{
		this.id = id;
	}

	/** Stable identifier used in the config file and in {@code /rsusoc}. */
	public String id()
	{
		return id;
	}

	/** Case-insensitive lookup by {@link #id()}; {@code null} if unknown. */
	public static OrderKind byId(String id)
	{
		for (OrderKind kind : values()) {
			if (kind.id.equalsIgnoreCase(id)) return kind;
		}
		return null;
	}

	/** The palette implementing this kind. */
	public Palette palette()
	{
		switch (this) {
			case HIGH_CONTRAST:
				return new HighContrastPalette();
			case GROUP3:
				return new GroupPalette();
			case FOURIER:
				return new FourierPalette();
			default:
				throw new IllegalStateException("unhandled kind " + this);
		}
	}
}