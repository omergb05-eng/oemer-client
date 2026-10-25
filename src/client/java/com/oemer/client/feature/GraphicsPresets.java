package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/** Grafik-Stufen Low / Mid / High / Super High (setzt die normalen Minecraft-Videoeinstellungen). */
public final class GraphicsPresets {
	private GraphicsPresets() {
	}

	public static final String[] NAMES = {"Low", "Mid", "High", "Super High"};
	public static final String[] HINTS = {"Max. FPS für PvP", "Ausgewogen", "Schön", "Alles an"};

	public static void apply(int level) {
		Options o = Minecraft.getInstance().options;
		int[] render = {6, 10, 16, 24};
		int[] sim = {5, 8, 12, 16};
		int[] blend = {0, 2, 5, 7};
		double[] entity = {0.5, 0.75, 1.0, 1.5};
		set(() -> o.renderDistance().set(render[level]));
		set(() -> o.simulationDistance().set(sim[level]));
		set(() -> o.particles().set(level == 0 ? ParticleStatus.MINIMAL : level == 1 ? ParticleStatus.DECREASED : ParticleStatus.ALL));
		set(() -> o.entityShadows().set(level >= 1));
		set(() -> o.cloudStatus().set(level == 0 ? CloudStatus.OFF : level == 1 ? CloudStatus.FAST : CloudStatus.FANCY));
		set(() -> o.biomeBlendRadius().set(blend[level]));
		set(() -> o.ambientOcclusion().set(level >= 1));
		set(() -> o.cutoutLeaves().set(level >= 1));
		set(() -> o.improvedTransparency().set(level >= 3));
		set(() -> o.entityDistanceScaling().set(entity[level]));
		o.save();
		OemerConfig.get().graphicsLevel = level;
		OemerConfig.save();
	}

	private static void set(Runnable r) {
		try {
			r.run();
		} catch (Exception e) {
			com.oemer.client.OemerClient.LOGGER.debug("Grafik-Einstellung übersprungen", e);
		}
	}
}
