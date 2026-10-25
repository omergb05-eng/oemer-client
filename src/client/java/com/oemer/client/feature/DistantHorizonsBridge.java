package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Distant Horizons (eingebaut, Original-Mod, LGPL-3.0) an/aus schalten.
 * Wir sprechen die offizielle DH-API per Reflection an, damit der Client auch ohne DH läuft.
 */
public final class DistantHorizonsBridge {
	private DistantHorizonsBridge() {
	}

	private static Boolean applied;
	private static int tickCounter;
	private static String status = "";

	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("distanthorizons");
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer("distanthorizons")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	public static String status() {
		return status;
	}

	/** Jeden Tick: Einstellung an DH weitergeben, sobald DH bereit ist. */
	public static void tick() {
		if (!installed()) return;
		boolean want = OemerConfig.get().distantHorizons;
		if (applied != null && applied == want) return;
		if (++tickCounter % 40 != 0) return; // alle 2 Sekunden versuchen
		if (setRendering(want)) applied = want;
	}

	private static boolean setRendering(boolean on) {
		try {
			Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
			Field f = delayed.getField("configs");
			Object configs = f.get(null);
			if (configs == null) {
				status = "Distant Horizons startet noch …";
				return false;
			}
			Object graphics = call(configs, "graphics");
			Object value = call(graphics, "renderingEnabled");
			Method set = findSetter(value.getClass());
			set.setAccessible(true);
			set.invoke(value, on);
			status = on ? "Distant Horizons " + version() + " ist an" : "Distant Horizons ist aus";
			OemerClient.LOGGER.info("Distant Horizons Rendering: {}", on);
			return true;
		} catch (Throwable t) {
			status = "Distant Horizons konnte nicht gesteuert werden";
			OemerClient.LOGGER.warn("Distant Horizons API nicht erreichbar: {}", t.toString());
			applied = on; // nicht endlos versuchen
			return false;
		}
	}

	private static Object call(Object target, String name) throws Exception {
		Method m = target.getClass().getMethod(name);
		m.setAccessible(true);
		return m.invoke(target);
	}

	private static Method findSetter(Class<?> c) throws NoSuchMethodException {
		for (Method m : c.getMethods()) {
			if (m.getName().equals("setValue") && m.getParameterCount() == 1) return m;
		}
		throw new NoSuchMethodException("setValue");
	}

	/** Auto-Updater von DH ausschalten (die Version kommt mit dem Ömer Client). */
	public static void disableUpdater() {
		if (!installed()) return;
		String[] classes = {"com.seibel.distanthorizons.core.config.Config$Client$Advanced$AutoUpdater",
				"com.seibel.distanthorizons.core.config.Config$Client$AutoUpdater"};
		for (String cls : classes) {
			try {
				Class<?> c = Class.forName(cls);
				for (String field : new String[]{"enableAutoUpdater", "enableSilentUpdates"}) {
					try {
						Object entry = c.getField(field).get(null);
						Method set = null;
						for (Method m : entry.getClass().getMethods()) {
							if ((m.getName().equals("set") || m.getName().equals("setValue")) && m.getParameterCount() == 1) set = m;
						}
						if (set != null) {
							set.setAccessible(true);
							set.invoke(entry, false);
							OemerClient.LOGGER.info("Distant Horizons: {} aus", field);
						}
					} catch (NoSuchFieldException ignored) {
					}
				}
				return;
			} catch (Throwable t) {
				OemerClient.LOGGER.info("Distant Horizons Updater ({}) nicht gefunden: {}", cls, t.toString());
			}
		}
	}

	public static void changed() {
		applied = null;
		tickCounter = 39;
	}

	/** Das Einstellungs-Menü von Distant Horizons (null, wenn es keins gibt). */
	public static Screen configScreen(Screen parent) {
		String[] candidates = {"com.seibel.distanthorizons.common.wrappers.gui.GetConfigScreen"};
		for (String cls : candidates) {
			try {
				Method m = Class.forName(cls).getMethod("getScreen", Screen.class);
				Object s = m.invoke(null, parent);
				if (s instanceof Screen screen) return screen;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}
}
