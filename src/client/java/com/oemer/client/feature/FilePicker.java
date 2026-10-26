package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** "Datei wählen": öffnet das normale Datei-Fenster von Windows/Mac/Linux und kopiert das Bild in den Ömer-Ordner. */
public final class FilePicker {
	private FilePicker() {
	}

	private static volatile boolean open;

	private static String os() {
		return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
	}

	/** Startet ein Programm und liest die erste Ausgabezeile (null = abgebrochen / nicht vorhanden). */
	private static String run(String... cmd) {
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(false).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			String out;
			try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
				out = in.readLine();
			}
			int code = p.waitFor();
			return code == 0 && out != null && !out.isBlank() ? out.trim() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean has(String bin) {
		String path = System.getenv("PATH");
		if (path == null) return false;
		for (String d : path.split(java.io.File.pathSeparator)) if (new java.io.File(d, bin).canExecute()) return true;
		return false;
	}

	/** Nativer Dialog des Systems (Windows: PowerShell, Mac: osascript, Linux: zenity/kdialog). */
	private static String dialog(boolean folder, String title, String start, String[] patterns, String description) {
		String os = os();
		String home = start == null || start.isBlank() ? System.getProperty("user.home") : start;
		if (os.contains("win")) {
			String filter = folder ? "" : String.join(";", patterns);
			String script = folder
					? "Add-Type -AssemblyName System.Windows.Forms; $d = New-Object System.Windows.Forms.FolderBrowserDialog; $d.Description = '" + title.replace("'", "") + "'; if ($d.ShowDialog() -eq 'OK') { $d.SelectedPath }"
					: "Add-Type -AssemblyName System.Windows.Forms; $d = New-Object System.Windows.Forms.OpenFileDialog; $d.Title = '" + title.replace("'", "") + "'; $d.Filter = '" + description.replace("'", "") + "|" + filter + "'; if ($d.ShowDialog() -eq 'OK') { $d.FileName }";
			return run("powershell", "-NoProfile", "-STA", "-Command", script);
		}
		if (os.contains("mac")) {
			String script = folder
					? "POSIX path of (choose folder with prompt \"" + title.replace("\"", "") + "\")"
					: "POSIX path of (choose file with prompt \"" + title.replace("\"", "") + "\")";
			return run("osascript", "-e", script);
		}
		if (has("zenity")) {
			java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of("zenity", "--file-selection", "--title=" + title, "--filename=" + home + "/"));
			if (folder) cmd.add("--directory");
			else cmd.add("--file-filter=" + description + " | " + String.join(" ", patterns));
			return run(cmd.toArray(new String[0]));
		}
		if (has("kdialog")) {
			return folder ? run("kdialog", "--getexistingdirectory", home, "--title", title)
					: run("kdialog", "--getopenfilename", home, String.join(" ", patterns) + "|" + description, "--title", title);
		}
		OemerClient.LOGGER.warn("Kein Datei-Dialog gefunden (zenity/kdialog fehlt)");
		return null;
	}

	public static boolean isOpen() {
		return open;
	}

	/** Bild auswählen → in folder kopieren → onDone(Dateiname) im Spiel-Thread. */
	public static void pickImage(String title, Path folder, Consumer<String> onDone) {
		pickFile(title, folder, new String[]{"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"}, "Bilder", onDone);
	}

	/** Schematic wählen (.litematic / .schem) → in den schematics-Ordner kopieren. */
	public static void pickSchematic(Path folder, Consumer<String> onDone) {
		pickFile("Schematic wählen", folder, new String[]{"*.litematic", "*.schem"}, "Schematics", onDone);
	}

	/** Ordner wählen → onDone(Pfad) im Spiel-Thread. */
	public static void pickFolder(String title, String start, Consumer<String> onDone) {
		if (open) return;
		open = true;
		Thread t = new Thread(() -> {
			String chosen = null;
			try {
				String def = start == null || start.isBlank() ? System.getProperty("user.home") + "/" : start;
				chosen = dialog(true, title, def, new String[0], "");
			} catch (Throwable e) {
				OemerClient.LOGGER.warn("Ordner-Fenster ging nicht auf", e);
			}
			open = false;
			final String result = chosen;
			if (result != null && !result.isBlank()) Minecraft.getInstance().execute(() -> onDone.accept(result));
		}, "Ömer Ordner wählen");
		t.setDaemon(true);
		t.start();
	}

	/** Beliebige Datei auswählen → in folder kopieren → onDone(Dateiname) im Spiel-Thread. */
	public static void pickFile(String title, Path folder, String[] patterns, String description, Consumer<String> onDone) {
		if (open) return;
		open = true;
		Thread t = new Thread(() -> {
			String chosen = null;
			try {
				chosen = dialog(false, title, null, patterns, description);
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
					boolean copied = !Files.exists(target);
					if (copied) Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
					// Sehr große Bilder gleich beim Hochladen verlustfrei verkleinern (nur unsere Kopie, nie das Original)
					if (copied && patterns.length > 0 && patterns[0].equals("*.png")) target = ImageTools.shrinkFile(target, 2048);
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
