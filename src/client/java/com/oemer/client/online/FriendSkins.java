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
		volatile ClientAsset.Texture tex;
		volatile Identifier id;
		volatile boolean broken;
		Identifier old;

		Holder(byte[] png) {
			this.png = png;
		}
	}

	private static final Map<UUID, Holder> CAPES = new ConcurrentHashMap<>();
	private static final Map<String, byte[]> TOTEMS = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> TOTEM_IMAGES = new ConcurrentHashMap<>();
	private static int counter;

	static void update(String uid, String mc, byte[] cape, byte[] totem) {
		UUID id = parse(mc);
		if (id != null) {
			Holder old = CAPES.get(id);
			if (cape == null) {
				if (old != null) {
					CAPES.remove(id);
					release(old.id);
				}
			} else if (old == null || !java.util.Arrays.equals(old.png, cape)) {
				Holder h = new Holder(cape);
				if (old != null) h.old = old.id;
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
		for (Holder h : CAPES.values()) release(h.id);
		CAPES.clear();
		TOTEMS.clear();
		TOTEM_IMAGES.clear();
	}

	private static void release(Identifier id) {
		if (id == null) return;
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(id));
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
		if (h.tex != null) return h.tex;
		try {
			Minecraft mc = Minecraft.getInstance();
			NativeImage image = NativeImage.read(new ByteArrayInputStream(h.png));
			Identifier id = Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "friendcape/" + (counter++));
			mc.getTextureManager().register(id, new DynamicTexture(() -> "Freund-Cape", image));
			if (h.old != null) {
				mc.getTextureManager().release(h.old);
				h.old = null;
			}
			h.id = id;
			h.tex = new ClientAsset.ResourceTexture(id, id);
			return h.tex;
		} catch (Exception e) {
			h.broken = true;
			OemerClient.LOGGER.warn("Freund-Cape konnte nicht geladen werden", e);
			return null;
		}
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
