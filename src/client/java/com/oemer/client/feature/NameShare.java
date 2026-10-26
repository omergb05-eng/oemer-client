package com.oemer.client.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dein Namensstil (Farbe/Verlauf/Rainbow) und dein kleines Logo neben dem Namen – für dich und für Freunde,
 * die den Ömer Client haben. Freunde bekommen beides über den Server (skins/&lt;uid&gt;).
 */
public final class NameShare {
	private NameShare() {
	}

	/** Ein Namensstil. text leer = der echte Name des Spielers. */
	public record Style(String text, int mode, int c1, int c2, boolean bold) {
	}

	private static final class Holder {
		final byte[] png;
		Identifier id;
		boolean broken;

		Holder(byte[] png) {
			this.png = png;
		}
	}

	private static final Map<String, Style> STYLES = new ConcurrentHashMap<>();
	private static final Map<String, Holder> LOGOS = new ConcurrentHashMap<>();
	private static int counter;
	private static Holder own;
	private static String ownStamp = "";
	/** Wie oft ein Logo über einem Namen gemalt wurde (für Tests). */
	public static volatile int drawn;

	private static String norm(String s) {
		return s == null ? "" : s.replace("-", "").toLowerCase(Locale.ROOT);
	}

	// ---------------------------------------------------------------- Logo-Datei

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("namelogo");
	}

	/** Das eigene Logo als Bild (höchstens 32x32) oder null. */
	public static BufferedImage ownImage() {
		OemerConfig c = OemerConfig.get();
		if (c.nameLogo.isEmpty()) return null;
		try {
			Path f = folder().resolve(c.nameLogo);
			if (!Files.isRegularFile(f)) return null;
			BufferedImage img = ImageTools.read(f);
			if (img == null) return null;
			if (img.getWidth() > 32 || img.getHeight() > 32 || img.getWidth() != img.getHeight()) {
				img = ImageTools.squareContain(img, Math.min(32, Math.max(img.getWidth(), img.getHeight())));
			}
			return img;
		} catch (Exception e) {
			return null;
		}
	}

	/** Das eigene Logo als PNG für den Server (klein halten) oder null. */
	public static byte[] ownPng() {
		OemerConfig c = OemerConfig.get();
		if (!c.nameLogoOn || !c.customName) return null;
		BufferedImage img = ownImage();
		if (img == null) return null;
		try {
			byte[] png = ImageTools.toPng(img);
			if (png.length > 5500) png = ImageTools.toPng(ImageTools.squareContain(img, 16));
			return png.length > 5500 ? null : png;
		} catch (Exception e) {
			return null;
		}
	}

	/** Ändert sich, wenn das Logo ein anderes wird (für Vorschau-Zwischenspeicher). */
	public static String stamp() {
		String n = OemerConfig.get().nameLogo;
		return n + "_" + stampOf(n);
	}

	public static void invalidate() {
		ownStamp = "";
	}

	// ---------------------------------------------------------------- Stil

	/** Dein Stil als JSON für den Server oder null (nicht teilen). */
	public static String ownJson() {
		OemerConfig c = OemerConfig.get();
		if (!c.customName || !c.shareName) return null;
		JsonObject o = new JsonObject();
		o.addProperty("t", c.customNameText.trim());
		o.addProperty("m", Math.floorMod(c.nameMode, 3));
		o.addProperty("a", c.nameColor1);
		o.addProperty("b", c.nameColor2);
		o.addProperty("f", c.nameBold);
		return o.toString();
	}

	private static Style parse(String json) {
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			String t = o.has("t") ? o.get("t").getAsString() : "";
			if (t.length() > 32) t = t.substring(0, 32);
			int n = NameStyle.COLORS.length;
			return new Style(t, Math.floorMod(o.get("m").getAsInt(), 3), Math.floorMod(o.get("a").getAsInt(), n),
					Math.floorMod(o.get("b").getAsInt(), n), o.has("f") && o.get("f").getAsBoolean());
		} catch (Exception e) {
			return null;
		}
	}

	/** Vom Server abgeholt: Stil und Logo eines Freundes. */
	public static void setFriend(String mcUuid, String json, byte[] logoPng) {
		String key = norm(mcUuid);
		if (key.isEmpty()) return;
		Style st = json == null ? null : parse(json);
		if (st == null) STYLES.remove(key);
		else STYLES.put(key, st);
		Holder old = LOGOS.get(key);
		if (logoPng == null) {
			if (old != null) {
				LOGOS.remove(key);
				release(old);
			}
		} else if (old == null || !java.util.Arrays.equals(old.png, logoPng)) {
			LOGOS.put(key, new Holder(logoPng));
			if (old != null) release(old);
		}
	}

	public static void clear() {
		for (Holder h : LOGOS.values()) release(h);
		LOGOS.clear();
		STYLES.clear();
	}

	private static void release(Holder h) {
		if (h == null || h.id == null) return;
		Identifier id = h.id;
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(id));
	}

	/** Der Stil dieses Spielers (du selbst oder ein Freund) oder null = normaler Name. */
	public static Style styleOf(UUID player) {
		if (player == null) return null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getUser() != null && player.equals(mc.getUser().getProfileId())) {
			OemerConfig c = OemerConfig.get();
			return c.customName ? new Style(c.customNameText.trim(), c.nameMode, c.nameColor1, c.nameColor2, c.nameBold) : null;
		}
		return STYLES.get(norm(player.toString()));
	}

	/** Name im Stil des Spielers (realName wird genommen, wenn der Stil keinen eigenen Text hat). */
	public static net.minecraft.network.chat.MutableComponent styled(Style st, String realName) {
		String text = st.text().isEmpty() ? realName : st.text();
		return NameStyle.styled(text, st.mode(), st.c1(), st.c2(), st.bold());
	}

	// ---------------------------------------------------------------- Logo zeichnen

	/** Textur des Logos dieses Spielers oder null. Nur auf dem Render-Thread. */
	public static Identifier logoOf(UUID player) {
		if (player == null) return null;
		Minecraft mc = Minecraft.getInstance();
		Holder h;
		if (mc.getUser() != null && player.equals(mc.getUser().getProfileId())) {
			OemerConfig c = OemerConfig.get();
			String stamp = c.nameLogo + "|" + c.nameLogoOn + "|" + c.customName + "|" + stampOf(c.nameLogo);
			if (!stamp.equals(ownStamp)) {
				ownStamp = stamp;
				release(own);
				byte[] png = ownPng();
				own = png == null ? null : new Holder(png);
			}
			h = own;
		} else {
			h = LOGOS.get(norm(player.toString()));
		}
		if (h == null || h.broken) return null;
		if (h.id != null) return h.id;
		try {
			NativeImage image = NativeImage.read(new ByteArrayInputStream(h.png));
			Identifier id = Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "namelogo/" + (counter++));
			mc.getTextureManager().register(id, new DynamicTexture(() -> "Ömer Logo", image));
			h.id = id;
			return id;
		} catch (Exception e) {
			h.broken = true;
			OemerClient.LOGGER.warn("Namens-Logo konnte nicht geladen werden", e);
			return null;
		}
	}

	private static long stampOf(String name) {
		try {
			return name.isEmpty() ? 0 : Files.getLastModifiedTime(folder().resolve(name)).toMillis();
		} catch (Exception e) {
			return 0;
		}
	}

	/** Das Logo links neben das Namensschild setzen. attachment/camera kommen aus dem Render-Zustand. */
	public static void drawLogo(PoseStack pose, SubmitNodeCollector collector, Vec3 attachment, CameraRenderState camera, int nameWidth,
								boolean seeThrough, Identifier tex) {
		float x1 = -nameWidth / 2f - 3f, x0 = x1 - 10f, y0 = -1f, y1 = 9f;
		pose.pushPose();
		pose.translate(attachment.x, attachment.y + 0.5, attachment.z); // wie das Namensschild selbst
		pose.rotate(camera.orientation);
		pose.scale(0.025f, -0.025f, 0.025f);
		try {
			if (seeThrough) {
				collector.submitCustomGeometry(pose, RenderTypes.textSeeThrough(tex), (p, vc) -> quad(p, vc, x0, y0, x1, y1, 0x50FFFFFF));
			}
			collector.submitCustomGeometry(pose, RenderTypes.text(tex), (p, vc) -> quad(p, vc, x0, y0, x1, y1, 0xFFFFFFFF));
			drawn++;
		} finally {
			pose.popPose();
		}
	}

	private static void quad(PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer vc, float x0, float y0, float x1, float y1, int color) {
		int light = 0xF000F0;
		vc.addVertex(p, x0, y0, 0f).setColor(color).setUv(0f, 0f).setLight(light);
		vc.addVertex(p, x0, y1, 0f).setColor(color).setUv(0f, 1f).setLight(light);
		vc.addVertex(p, x1, y1, 0f).setColor(color).setUv(1f, 1f).setLight(light);
		vc.addVertex(p, x1, y0, 0f).setColor(color).setUv(1f, 0f).setLight(light);
	}
}
