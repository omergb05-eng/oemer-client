package com.oemer.client.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clipper: speichert im Hintergrund die letzten Sekunden vom Bild (als kleine JPEGs im Arbeitsspeicher).
 * Taste drücken → die letzten 30 s / 1 min / 2 min landen als Video im gewählten Ordner. Ohne Ton.
 */
public final class Clipper {
	private Clipper() {
	}

	private record Frame(long t, byte[] jpg) {
	}

	/** Ein fertiger Ausschnitt zum Speichern. */
	public record Snapshot(List<Frame> frames, int width, int height) {
	}

	private static final ArrayDeque<Frame> FRAMES = new ArrayDeque<>();
	private static long totalBytes;
	private static int width, height;
	private static long lastCapture;
	private static final AtomicInteger PENDING = new AtomicInteger();
	private static volatile String lastSaved = "";
	private static volatile String status = "";
	private static volatile boolean saving;
	private static final long MAX_BYTES = 420L * 1024 * 1024;

	private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Ömer Clipper Bild");
		t.setDaemon(true);
		return t;
	});
	private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Ömer Clipper Speichern");
		t.setDaemon(true);
		return t;
	});

	public static Path defaultFolder() {
		return FabricLoader.getInstance().getGameDir().resolve("oemer-clips");
	}

	public static Path folder() {
		String f = OemerConfig.get().clipFolder;
		return f == null || f.isBlank() ? defaultFolder() : Path.of(f);
	}

	public static String status() {
		return status;
	}

	public static String lastSaved() {
		return lastSaved;
	}

	public static boolean saving() {
		return saving;
	}

	/** Wie viele Sekunden sind gerade im Puffer? */
	public static int bufferedSeconds() {
		synchronized (FRAMES) {
			if (FRAMES.size() < 2) return 0;
			return (int) ((FRAMES.peekLast().t - FRAMES.peekFirst().t) / 1000);
		}
	}

	public static int bufferedFrames() {
		synchronized (FRAMES) {
			return FRAMES.size();
		}
	}

	public static long bufferedBytes() {
		return totalBytes;
	}

	public static void clear() {
		synchronized (FRAMES) {
			FRAMES.clear();
			totalBytes = 0;
		}
	}

	// ------------------------------------------------------------ Aufnehmen

	/** Jeden Tick: wenn es Zeit für ein neues Bild ist, das aktuelle Bild abgreifen. */
	public static void tick(Minecraft mc) {
		OemerConfig c = OemerConfig.get();
		if (!c.clipperOn) {
			if (bufferedFrames() > 0) clear();
			return;
		}
		if (mc.level == null) return;
		long now = System.currentTimeMillis();
		int fps = OemerConfig.CLIP_FPS[c.clipFps];
		if (now - lastCapture < 1000L / fps - 4) return;
		if (PENDING.get() > 3) return;
		lastCapture = now;
		PENDING.incrementAndGet();
		final int targetW = OemerConfig.CLIP_WIDTHS[c.clipQuality];
		try {
			Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
				int w = image.getWidth(), h = image.getHeight();
				int[] px;
				try {
					px = image.getPixelsABGR();
				} finally {
					image.close();
				}
				ENCODER.execute(() -> {
					try {
						addFrame(px, w, h, now, targetW);
					} catch (Throwable t) {
						OemerClient.LOGGER.debug("Clipper-Bild: {}", t.toString());
					} finally {
						PENDING.decrementAndGet();
					}
				});
			});
		} catch (Throwable t) {
			PENDING.decrementAndGet();
			OemerClient.LOGGER.debug("Clipper konnte kein Bild holen: {}", t.toString());
		}
	}

	private static javax.imageio.ImageWriter jpegWriter;

	/** Verkleinern und als JPEG in den Puffer legen (läuft im Hintergrund). */
	static void addFrame(int[] abgr, int w, int h, long t, int targetW) throws Exception {
		int tw = Math.min(targetW, w);
		tw &= ~1;
		int th = Math.max(2, Math.round((float) tw * h / w)) & ~1;
		BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
		int[] out = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
		for (int y = 0; y < th; y++) {
			int sy0 = y * h / th, sy1 = Math.max(sy0 + 1, (y + 1) * h / th);
			for (int x = 0; x < tw; x++) {
				int sx0 = x * w / tw, sx1 = Math.max(sx0 + 1, (x + 1) * w / tw);
				int r = 0, g = 0, b = 0, n = 0;
				for (int sy = sy0; sy < sy1; sy++) {
					int row = sy * w;
					for (int sx = sx0; sx < sx1; sx++) {
						int p = abgr[row + sx];
						r += p & 0xFF;
						g += (p >> 8) & 0xFF;
						b += (p >> 16) & 0xFF;
						n++;
					}
				}
				out[y * tw + x] = ((r / n) << 16) | ((g / n) << 8) | (b / n);
			}
		}
		byte[] jpg = toJpeg(img, 0.8f);
		synchronized (FRAMES) {
			width = tw;
			height = th;
			FRAMES.addLast(new Frame(t, jpg));
			totalBytes += jpg.length;
			long keep = OemerConfig.CLIP_LENGTHS[OemerConfig.get().clipLen] * 1000L + 1500;
			while (!FRAMES.isEmpty() && (t - FRAMES.peekFirst().t > keep || totalBytes > MAX_BYTES)) {
				totalBytes -= FRAMES.pollFirst().jpg.length;
			}
		}
	}

	static byte[] toJpeg(BufferedImage img, float quality) throws Exception {
		if (jpegWriter == null) jpegWriter = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam p = jpegWriter.getDefaultWriteParam();
		p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		p.setCompressionQuality(quality);
		ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
		try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
			jpegWriter.setOutput(ios);
			jpegWriter.write(null, new IIOImage(img, null, null), p);
		}
		return bos.toByteArray();
	}

	// ------------------------------------------------------------ Speichern

	/** Taste gedrückt. */
	public static void saveRequested(Minecraft mc) {
		OemerConfig c = OemerConfig.get();
		if (!c.clipperOn) {
			say(mc, "§f[Clipper] §cist aus §7– im Menü bei Mods einschalten");
			return;
		}
		if (saving) {
			say(mc, "§f[Clipper] §7Der letzte Clip wird noch gespeichert …");
			return;
		}
		Snapshot snap = snapshot(OemerConfig.CLIP_LENGTHS[c.clipLen]);
		if (snap == null) {
			say(mc, "§f[Clipper] §cNoch nichts aufgenommen §7– kurz im Spiel warten");
			return;
		}
		say(mc, "§f[Clipper] §7Speichere die letzten " + OemerConfig.CLIP_LENGTH_NAMES[c.clipLen] + " …");
		saveAsync(snap, path -> say(mc, path == null ? "§f[Clipper] §cSpeichern ging schief" : "§f[Clipper] §aClip gespeichert: §7" + path.getFileName()));
	}

	/** Kopie der letzten Sekunden aus dem Puffer. null = zu wenig. */
	public static Snapshot snapshot(int seconds) {
		synchronized (FRAMES) {
			if (FRAMES.size() < 3) return null;
			long end = FRAMES.peekLast().t;
			List<Frame> list = new ArrayList<>();
			for (Frame f : FRAMES) if (end - f.t <= seconds * 1000L) list.add(f);
			if (list.size() < 3) return null;
			return new Snapshot(list, width, height);
		}
	}

	public static void saveAsync(Snapshot snap, java.util.function.Consumer<Path> done) {
		saving = true;
		status = "Speichere …";
		SAVER.execute(() -> {
			Path result = null;
			try {
				result = writeClip(snap);
				lastSaved = result.toString();
				status = "Gespeichert: " + result.getFileName();
			} catch (Throwable t) {
				OemerClient.LOGGER.warn("Clip konnte nicht gespeichert werden", t);
				status = "Fehler: " + t.getMessage();
			} finally {
				saving = false;
			}
			done.accept(result);
		});
	}

	/** Für den Test: Clip sofort (in diesem Thread) schreiben. */
	public static Path saveNow(int seconds) throws Exception {
		Snapshot s = snapshot(seconds);
		if (s == null) throw new IllegalStateException("zu wenig Bilder im Puffer");
		return writeClip(s);
	}

	private static Path writeClip(Snapshot snap) throws Exception {
		OemerConfig c = OemerConfig.get();
		Path dir = folder();
		Files.createDirectories(dir);
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		boolean mp4 = c.clipFormat == 0;
		Path file = dir.resolve("Clip_" + stamp + (mp4 ? ".mp4" : ".avi"));
		int fps = OemerConfig.CLIP_FPS[c.clipFps];

		// gleichmäßige Bildrate: für jede Ausgabe-Zeit das jeweils letzte Bild nehmen (gleicht Ruckler aus)
		List<Frame> src = snap.frames;
		long t0 = src.get(0).t, t1 = src.get(src.size() - 1).t;
		int n = Math.max(2, (int) Math.round((t1 - t0) / 1000.0 * fps));
		List<byte[]> seq = new ArrayList<>(n);
		int p = 0;
		for (int i = 0; i < n; i++) {
			long target = t0 + (long) i * 1000L / fps;
			while (p + 1 < src.size() && src.get(p + 1).t <= target) p++;
			seq.add(src.get(p).jpg);
		}
		if (mp4) VideoWriters.writeMp4(file, seq, snap.width, snap.height, fps);
		else VideoWriters.writeAvi(file, seq, snap.width, snap.height, fps);
		return file;
	}

	private static void say(Minecraft mc, String text) {
		mc.execute(() -> {
			if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
		});
	}

	/** Bild aus dem Puffer holen (für Test und Vorschau). */
	public static BufferedImage frameImage(int index) throws Exception {
		Frame f;
		synchronized (FRAMES) {
			if (FRAMES.isEmpty()) return null;
			List<Frame> l = new ArrayList<>(FRAMES);
			f = l.get(Math.max(0, Math.min(l.size() - 1, index < 0 ? l.size() + index : index)));
		}
		return ImageIO.read(new ByteArrayInputStream(f.jpg));
	}
}
