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

package dev.redstone_dust_order.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import dev.redstone_dust_order.client.ChunkRebuild;
import dev.redstone_dust_order.client.Config;
import dev.redstone_dust_order.client.OrderColors;
import dev.redstone_dust_order.client.OrderSelfCheck;
import dev.redstone_dust_order.order.OrderKind;
import dev.redstone_dust_order.order.OrderTable;

/**
 * {@code /rsusoc} — the mod's only in-game surface.
 *
 * <p>There is deliberately no debug HUD. This command plus the README carry the
 * same information with none of the per-frame cost. With no arguments it prints
 * the current settings; {@code check} re-derives the model for the position the
 * player is standing on, which is the fastest way to line it up against the
 * browser simulator at the same coordinates.
 *
 * <h2>Why Brigadier's builders and not {@code net.minecraft.commands.Commands}</h2>
 *
 * This dispatcher is a {@code CommandDispatcher<FabricClientCommandSource>}, but
 * {@code Commands#literal} and {@code Commands#argument} are declared to return
 * builders over {@code CommandSourceStack}, not over a type parameter, so they
 * cannot produce a tree this dispatcher accepts. Brigadier's own
 * {@code LiteralArgumentBuilder#literal} is properly generic, and the source type
 * has to be written down explicitly because nothing downstream infers it.
 */
public final class RsusocCommand
{
	private RsusocCommand() {}

	public static void registerCallback()
	{
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				LiteralArgumentBuilder.<FabricClientCommandSource>literal("rsusoc")
						.executes(ctx -> status(ctx.getSource()))
						.then(literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
						.then(literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
						.then(literal("check").executes(ctx -> check(ctx.getSource())))
						.then(literal("palette")
								.then(RequiredArgumentBuilder
										.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
										.executes(ctx -> setPalette(ctx.getSource(),
												StringArgumentType.getString(ctx, "name")))))
						.then(literal("alpha")
								.then(RequiredArgumentBuilder
										.<FabricClientCommandSource, Float>argument("value",
												FloatArgumentType.floatArg(0.0f, 1.0f))
										.executes(ctx -> setAlpha(ctx.getSource(),
												FloatArgumentType.getFloat(ctx, "value")))))));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name)
	{
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal(name);
	}

	private static int status(FabricClientCommandSource source)
	{
		Config config = Config.INSTANCE;

		source.sendFeedback(Component.translatable("redstone_dust_order.command.status",
				onOff(config.enabled()),
				Component.translatable(config.kind().palette().translationKey()),
				String.format("%.2f", config.alpha())).withStyle(ChatFormatting.GRAY));

		source.sendFeedback(Component.translatable("redstone_dust_order.command.status_extra",
				Component.literal(String.valueOf(OrderTable.get().orderCount())),
				Component.literal(config.file().toString())).withStyle(ChatFormatting.DARK_GRAY));

		return 1;
	}

	private static int setEnabled(FabricClientCommandSource source, boolean value)
	{
		apply(() -> Config.INSTANCE.setEnabled(value));

		source.sendFeedback(Component.translatable(value
				? "redstone_dust_order.command.enabled"
				: "redstone_dust_order.command.disabled"));

		return 1;
	}

	private static int setPalette(FabricClientCommandSource source, String name)
	{
		OrderKind kind = OrderKind.byId(name);

		if (kind == null) {
			source.sendFeedback(Component.translatable("redstone_dust_order.command.unknown_palette", name)
					.withStyle(ChatFormatting.RED));
			return 0;
		}

		apply(() -> Config.INSTANCE.setKind(kind));

		source.sendFeedback(Component.translatable("redstone_dust_order.command.palette_set",
				Component.translatable(kind.palette().translationKey())));

		return 1;
	}

	private static int setAlpha(FabricClientCommandSource source, float value)
	{
		apply(() -> Config.INSTANCE.setAlpha(value));

		source.sendFeedback(Component.translatable("redstone_dust_order.command.alpha_set",
				String.format("%.2f", Config.INSTANCE.alpha())));

		return 1;
	}

	private static int check(FabricClientCommandSource source)
	{
		OrderSelfCheck.report(source);
		return 1;
	}

	/** Persist, re-publish to the render path, and ask for one mesh rebuild. */
	private static void apply(Runnable change)
	{
		change.run();
		Config.INSTANCE.save();
		OrderColors.apply(Config.INSTANCE);
		ChunkRebuild.request();
	}

	private static Component onOff(boolean value)
	{
		return Component.translatable(value
				? "redstone_dust_order.command.on"
				: "redstone_dust_order.command.off");
	}
}