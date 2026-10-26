package com.oemer.client.online;

import com.mojang.blaze3d.platform.NativeImage;
import com.oemer.client.OemerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Capes und Totems, die Freunde mit dir teilen. Capes erscheinen auf den Freunden in der Welt. */
public final class FriendSkins {
	private FriendSkins() {
	}

	private static final class Holder {
		final byte[] png;
		/** Bilder untereinander im PNG (1 = normale Cape) und wie lange jedes zu sehen ist. */
		final int frames, ms;
		volatile ClientAsset.Texture tex;
		volatile Identifier id;
		volatile java.util.List<ClientAsset.Texture> anim;
		volatile java.util.List<Identifier> animIds = java.util.List.of();
		volatile boolean broken;
		java.util.List<Identifier> old = java.util.List.of();

		Holder(byte[] png, int frames, int ms) {
			this.png = png;
			this.frames = Math.max(1, Math.min(16, frames));
			this.ms = Math.max(20, Math.min(5000, ms));
		}

		java.util.List<Identifier> ids() {
			if (anim != null) return animIds;
			return id == null ? java.util.List.of() : java.util.List.of(id);
		}
	}

	private static final Map<UUID, Holder> CAPES = new ConcurrentHashMap<>();
	private static final Map<String, byte[]> TOTEMS = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> TOTEM_IMAGES = new ConcurrentHashMap<>();
	private static int counter;

	static void update(String uid, String mc, byte[] cape, int capeFrames, int capeMs, byte[] totem) {
		UUID id = parse(mc);
		if (id != null) {
			Holder old = CAPES.get(id);
			if (cape == null) {
				if (old != null) {
					CAPES.remove(id);
					release(old.ids());
				}
			} else if (old == null || !java.util.Arrays.equals(old.png, cape) || old.frames != Math.max(1, Math.min(16, capeFrames))) {
				Holder h = new Holder(cape, capeFrames, capeMs);
				if (old != null) h.old = old.ids();
				CAPES.put(id, h);
			}
		}
		if (totem == null) {
			TOTEMS.remove(uid);
			TOTEM_IMAGES.remove(uid);
		} else if (!java.util.Arrays.equals(TOTEMS.get(uid), totem)) {
			TOTEMS.put(uid, totem);
			TOTEM_IMAGES.remove(uid);
		}
	}

	static void clear() {
		for (Holder h : CAPES.values()) release(h.ids());
		CAPES.clear();
		TOTEMS.clear();
		TOTEM_IMAGES.clear();
	}

	private static void release(java.util.List<Identifier> ids) {
		if (ids.isEmpty()) return;
		Minecraft.getInstance().execute(() -> {
			for (Identifier id : ids) Minecraft.getInstance().getTextureManager().release(id);
		});
	}

	private static UUID parse(String s) {
		try {
			return s == null || s.isEmpty() ? null : UUID.fromString(s);
		} catch (Exception e) {
			return null;
		}
	}

	/** Cape eines Freundes (nach Minecraft-UUID) oder null. Nur auf dem Render-Thread aufrufen. */
	public static ClientAsset.Texture capeFor(UUID player) {
		Holder h = CAPES.get(player);
		if (h == null || h.broken) return null;
		if (h.anim != null) return h.anim.get(com.oemer.client.feature.CapeManager.frameAt(frameDelays(h), h.frames * h.ms, System.currentTimeMillis()));
		if (h.tex != null) return h.tex;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (h.frames > 1) {
				BufferedImage strip = ImageIO.read(new ByteArrayInputStream(h.png));
				int fh = strip.getHeight() / h.frames;
				java.util.List<ClientAsset.Texture> texs = new java.util.ArrayList<>();
				java.util.List<Identifier> ids = new java.util.ArrayList<>();
				for (int i = 0; i < h.frames; i++) {
					NativeImage ni = NativeImage.read(com.oemer.client.feature.ImageTools.toPng(strip.getSubimage(0, i * fh, strip.getWidth(), fh)));
					Identifier id = Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "friendcape/" + (counter++));
					mc.getTextureManager().register(id, new DynamicTexture(() -> "Freund-Cape", ni));
					ids.add(id);
					texs.add(new ClientAsset.ResourceTexture(id, id));
				}
				release(h.old);
				h.old = java.util.List.of();
				h.animIds = ids;
				h.anim = texs;
				return texs.get(0);
			}
			NativeImage image = NativeImage.read(new ByteArrayInputStream(h.png));
			Identifier id = Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "friendcape/" + (counter++));
			mc.getTextureManager().register(id, new DynamicTexture(() -> "Freund-Cape", image));
			release(h.old);
			h.old = java.util.List.of();
			h.id = id;
			h.tex = new ClientAsset.ResourceTexture(id, id);
			return h.tex;
		} catch (Exception e) {
			h.broken = true;
			OemerClient.LOGGER.warn("Freund-Cape konnte nicht geladen werden", e);
			return null;
		}
	}

	private static int[] frameDelays(Holder h) {
		int[] d = new int[h.frames];
		java.util.Arrays.fill(d, h.ms);
		return d;
	}

	/** Anzahl Bilder der Cape dieses Freundes (0 = keine Cape, 1 = normal, mehr = animiert). */
	public static int capeFrames(String mc) {
		UUID id = parse(mc);
		Holder h = id == null ? null : CAPES.get(id);
		return h == null ? 0 : h.frames;
	}

	public static boolean hasCape(String mc) {
		UUID id = parse(mc);
		return id != null && CAPES.containsKey(id);
	}

	public static boolean hasTotem(String uid) {
		return TOTEMS.containsKey(uid);
	}

	/** Totem-Bild eines Freundes (für die Anzeige im Menü) oder null. */
	public static BufferedImage totemImage(String uid) {
		BufferedImage img = TOTEM_IMAGES.get(uid);
		if (img != null) return img;
		byte[] png = TOTEMS.get(uid);
		if (png == null) return null;
		try {
			img = ImageIO.read(new ByteArrayInputStream(png));
			if (img != null) TOTEM_IMAGES.put(uid, img);
			return img;
		} catch (Exception e) {
			return null;
		}
	}

	public static int totemVersion(String uid) {
		byte[] b = TOTEMS.get(uid);
		return b == null ? 0 : java.util.Arrays.hashCode(b);
	}
}
