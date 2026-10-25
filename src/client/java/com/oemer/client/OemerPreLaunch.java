package com.oemer.client;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Läuft ganz am Anfang: schaltet den Auto-Updater vom eingebauten Distant Horizons aus,
 * damit er nie selbst Dateien im mods-Ordner austauscht (die Version kommt mit dem Ömer Client).
 */
public class OemerPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("DistantHorizons.toml");
			if (Files.isRegularFile(file)) {
				String s = Files.readString(file, StandardCharsets.UTF_8);
				String t = s.replaceAll("(enableAutoUpdater\\s*=\\s*)true", "$1false")
						.replaceAll("(enableSilentUpdates\\s*=\\s*)true", "$1false");
				if (!t.equals(s)) Files.writeString(file, t, StandardCharsets.UTF_8);
			}
		} catch (Exception ignored) {
		}
	}
}
