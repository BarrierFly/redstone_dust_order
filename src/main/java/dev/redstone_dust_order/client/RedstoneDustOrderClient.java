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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;

import dev.redstone_dust_order.command.RsusocCommand;

/**
 * Client entry point.
 *
 * <p>The mod is client-only and purely visual: every client computes the same
 * answer from the same coordinates with the same integer arithmetic, so there is
 * no protocol, no packet and nothing for a server to know about.
 *
 * <p>Startup order matters and is deliberate:
 * <ol>
 *   <li>load config and push it into {@link OrderColors}, so the render path
 *       never sees defaults it was not asked for;</li>
 *   <li><em>ask</em> for the tint. It cannot be installed yet: Fabric runs client
 *       entrypoints from inside the {@code Minecraft} constructor and
 *       {@code BlockColors.createDefault()} has not happened at that point, so
 *       {@code getBlockColors()} is still null. {@link OrderTint} installs itself
 *       on the first tick instead — see that class;</li>
 *   <li>register the key, the command and the world-join hook. All of those are
 *       safe during entrypoints.</li>
 * </ol>
 */
public class RedstoneDustOrderClient implements ClientModInitializer
{
	public static final String MOD_ID = "redstone_dust_order";

	public static final Logger LOGGER = LoggerFactory.getLogger("Redstone Dust Update Order");

	public static String MOD_VERSION = "unknown";

	@Override
	public void onInitializeClient()
	{
		MOD_VERSION = FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");

		Config config = Config.INSTANCE;
		config.load();
		OrderColors.apply(config);

		OrderTint.install();
		KeyBindings.register();

		RsusocCommand.registerCallback();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				WorldWarnings.onJoinWorld(client.level));

		ClientTickEvents.END_CLIENT_TICK.register(RedstoneDustOrderClient::onEndTick);

		LOGGER.info("Redstone Dust Update Order {} ready: {} orders, palette {}, alpha {}",
				MOD_VERSION,
				dev.redstone_dust_order.order.OrderTable.get().orderCount(),
				config.kind().id(),
				config.alpha());
	}

	private static void onEndTick(Minecraft client)
	{
		OrderTint.ensureInstalled(client);

		if (KeyBindings.consumeTogglePress()) {
			Config config = Config.INSTANCE;
			config.setEnabled(!config.enabled());
			config.save();
			OrderColors.apply(config);
			ChunkRebuild.request();
		}

		ChunkRebuild.flush(client);
	}
}