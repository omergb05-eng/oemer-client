package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;

import java.nio.file.Path;
import java.util.List;

/** Look-Modus: eigene Bilder aus .minecraft/config/oemerclient/looks/ als Hintergrund. */
public final class Looks {
	private Looks() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("looks");
	}

	public static List<String> choices() {
		return CapeManager.imageFiles(folder());
	}

	/** Die Textur vom gewählten Bild (auch für die Vorschau, wenn der Modus aus ist). */
	public static Textures.Tex selected() {
		OemerConfig c = OemerConfig.get();
		List<String> files = choices();
		if (files.isEmpty()) return null;
		if (!files.contains(c.lookImage)) {
			c.lookImage = files.get(0);
		}
		return Textures.file("look", folder().resolve(c.lookImage), 1920);
	}

	/** Ist der Modus an und gibt es ein Bild? */
	public static boolean active() {
		return OemerConfig.get().lookMode && selected() != null;
	}

	/** Bild über den ganzen Screen + Abdunkeln (damit Text lesbar bleibt). */
	public static boolean drawFullscreen(GuiGraphics g, int w, int h) {
		if (!OemerConfig.get().lookMode) return false;
		Textures.Tex t = selected();
		if (t == null) return false;
		Textures.drawCover(g, t, 0, 0, w, h);
		int dim = OemerConfig.LOOK_DIM[Math.floorMod(OemerConfig.get().lookDim, OemerConfig.LOOK_DIM.length)];
		g.fill(0, 0, w, h, dim << 24);
		return true;
	}
}
