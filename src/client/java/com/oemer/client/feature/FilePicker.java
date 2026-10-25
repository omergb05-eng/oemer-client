package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** "Datei wählen": öffnet das normale Datei-Fenster von Windows/Mac/Linux und kopiert das Bild in den Ömer-Ordner. */
public final class FilePicker {
	private FilePicker() {
	}

	private static volatile boolean open;

	public static boolean isOpen() {
		return open;
	}

	/** Bild auswählen → in folder kopieren → onDone(Dateiname) im Spiel-Thread. */
	public static void pickImage(String title, Path folder, Consumer<String> onDone) {
		if (open) return;
		open = true;
		Thread t = new Thread(() -> {
			String chosen = null;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				String[] patterns = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};
				PointerBuffer filters = stack.mallocPointer(patterns.length);
				for (String p : patterns) filters.put(stack.UTF8(p));
				filters.flip();
				chosen = TinyFileDialogs.tinyfd_openFileDialog(title, System.getProperty("user.home") + "/", filters, "Bilder", false);
			} catch (Throwable e) {
				OemerClient.LOGGER.warn("Datei-Fenster ging nicht auf", e);
			}
			String name = null;
			if (chosen != null && !chosen.isBlank()) {
				try {
					Path src = Path.of(chosen);
					Files.createDirectories(folder);
					String base = src.getFileName().toString().replaceAll("[^A-Za-z0-9._ -]", "_");
					Path target = folder.resolve(base);
					int n = 2;
					while (Files.exists(target) && !Files.isSameFile(src, target)) {
						int dot = base.lastIndexOf('.');
						target = folder.resolve((dot > 0 ? base.substring(0, dot) : base) + "_" + n++ + (dot > 0 ? base.substring(dot) : ""));
					}
					if (!Files.exists(target)) Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
					name = target.getFileName().toString();
				} catch (Exception e) {
					OemerClient.LOGGER.warn("Bild konnte nicht kopiert werden", e);
				}
			}
			open = false;
			final String result = name;
			if (result != null) Minecraft.getInstance().execute(() -> onDone.accept(result));
		}, "Ömer Datei wählen");
		t.setDaemon(true);
		t.start();
	}
}
