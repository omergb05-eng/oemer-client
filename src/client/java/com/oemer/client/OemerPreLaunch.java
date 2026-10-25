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
			if (!Files.exists(file) && FabricLoader.getInstance().isModLoaded("distanthorizons")) {
				// erster Start: nur die Werte, die wir ändern – den Rest ergänzt Distant Horizons selbst
				Files.writeString(file, """
						[common.logging.warning]
							showGarbageCollectorWarning = false
							showLowMemoryWarningOnStartup = false
							showModCompatibilityWarningsOnStartup = false
							showHighVanillaRenderDistanceWarning = false
							showSlowWorldGenSettingWarnings = false
							showPoolInsufficientMemoryWarning = false
							showReplayWarningOnStartup = false

						[client.advanced.autoUpdater]
							enableAutoUpdater = false
							enableSilentUpdates = false
						""", StandardCharsets.UTF_8);
				return;
			}
			if (Files.isRegularFile(file)) {
				String s = Files.readString(file, StandardCharsets.UTF_8);
				String t = s.replaceAll("(enableAutoUpdater\\s*=\\s*)true", "$1false")
						.replaceAll("(enableSilentUpdates\\s*=\\s*)true", "$1false")
						.replaceAll("(show(GarbageCollector|LowMemory|ModCompatibility|HighVanillaRenderDistance|SlowWorldGen|PoolInsufficientMemory|Replay)\\w*\\s*=\\s*)true", "$1false");
				if (!t.equals(s)) Files.writeString(file, t, StandardCharsets.UTF_8);
			}
		} catch (Exception ignored) {
		}
	}
}
