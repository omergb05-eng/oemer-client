package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/** Löschen von eigenen Dateien (Hintergründe, Capes, Totems, Cosmetics …) – und die Auswahl wird sauber zurückgesetzt. */
public final class OwnFiles {
	private OwnFiles() {
	}

	/** Löscht name im Ordner dir (nur direkt darin, nie außerhalb). */
	public static boolean remove(Path dir, String name) {
		if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) return false;
		try {
			Path f = dir.resolve(name).normalize();
			if (!f.startsWith(dir.normalize()) || !Files.isRegularFile(f)) return false;
			Files.delete(f);
			return true;
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Konnte {} nicht löschen", name, e);
			return false;
		}
	}

	public static boolean look(String name) {
		boolean ok = remove(Looks.folder(), name);
		OemerConfig c = OemerConfig.get();
		if (c.lookImage.equals(name)) c.lookImage = Looks.WEB + "0";
		OemerConfig.save();
		return ok;
	}

	public static boolean cape(String name) {
		boolean ok = remove(CapeManager.folder(), name);
		OemerConfig c = OemerConfig.get();
		if (c.capeSelection.equals(name)) c.capeSelection = "design:0";
		CapeManager.invalidate();
		OemerConfig.save();
		return ok;
	}

	/** true = das gelöschte Totem war aktiv (Ressourcen müssen neu geladen werden). */
	public static boolean totem(String name) {
		remove(LooksPack.totemFolder(), name);
		OemerConfig c = OemerConfig.get();
		boolean wasActive = c.totemMode == 0 && c.totemSelection.equals(name);
		if (c.totemSelection.equals(name)) {
			c.totemSelection = "";
			if (c.totemMode == 0) {
				c.totemMode = 2;
				c.totemPreset = 0;
			}
		}
		OemerConfig.save();
		return wasActive;
	}

	public static boolean cosmetic(String name) {
		boolean ok = remove(CustomCosmetics.folder(), name);
		OemerConfig c = OemerConfig.get();
		c.equipped.values().removeIf(v -> v.equals("custom:" + name));
		CustomCosmetics.invalidate();
		OemerConfig.save();
		return ok;
	}

	public static boolean crosshair(String name) {
		boolean ok = remove(Crosshair.folder(), name);
		OemerConfig c = OemerConfig.get();
		if (c.crosshairSelection.equals(name)) c.crosshairSelection = "design:0";
		OemerConfig.save();
		return ok;
	}

	public static boolean schematic(String name) {
		boolean ok = remove(MaterialList.folder(), name);
		OemerConfig c = OemerConfig.get();
		if (c.schematic.equals(name)) {
			c.schematic = "";
			c.materialDone.clear();
		}
		MaterialList.reload();
		OemerConfig.save();
		return ok;
	}
}
