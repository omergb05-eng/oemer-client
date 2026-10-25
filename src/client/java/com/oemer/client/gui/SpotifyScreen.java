package com.oemer.client.gui;

import com.oemer.client.feature.Spotify;
import com.oemer.client.feature.Textures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.image.BufferedImage;
import java.util.List;

/** Spotify im Spiel: was läuft, Steuerung und deine Playlists. */
public class SpotifyScreen extends Screen {
	private final Screen parent;
	private double scroll;
	private int listX, listY, listW, listH;
	private static final int ROW = 30;

	public SpotifyScreen(Screen parent) {
		super(Component.literal("Spotify"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(new OmerWidgets.Flat(14, 12, 64, 20, "‹ Zurück", this::onClose).style(OmerWidgets.Style.GHOST));

		int cardY = 44, cardH = 74;
		int bx = 20 + 70, by = cardY + cardH - 24;
		int bw = 34;
		addControls(this, bx, by, bw, 18);
		this.addRenderableWidget(new OmerWidgets.Flat(this.width - 20 - 90, cardY + 8, 90, 16, "Neu laden", () -> {
			Spotify.loadPlaylists();
			try {
				Spotify.refreshPlayback();
			} catch (Exception ignored) {
			}
		}));

		listX = 20;
		listY = cardY + cardH + 22;
		listW = this.width - 40;
		listH = this.height - listY - 28;
		if (Spotify.playlists().isEmpty()) Spotify.loadPlaylists();
	}

	/** Steuer-Buttons (auch im Ömer-Menü benutzt). Gibt die Breite zurück. */
	static int addControls(Screen screen, int x, int y, int w, int h) {
		String[][] buttons = {{"◀◀", "Vorheriger Song"}, {"▶ ❚❚", "Play / Pause"}, {"▶▶", "Nächster Song"},
				{"⇄", "Zufall an/aus"}, {"−", "Leiser"}, {"+", "Lauter"}};
		Runnable[] actions = {Spotify::previous, Spotify::playPause, Spotify::next, Spotify::toggleShuffle,
				() -> Spotify.volume(-10), () -> Spotify.volume(10)};
		int cx = x;
		for (int i = 0; i < buttons.length; i++) {
			int bw = i == 1 ? w + 10 : w;
			var b = new OmerWidgets.Flat(cx, y, bw, h, buttons[i][0], actions[i]);
			if (i == 1) b.style(OmerWidgets.Style.PRIMARY);
			b.tip(buttons[i][1]);
			if (screen instanceof SpotifyScreen s) s.addRenderableWidget(b);
			else if (screen instanceof OmerScreen o) o.addExtra(b);
			cx += bw + 4;
		}
		return cx - x;
	}

	/** Karte "Läuft gerade" mit Cover, Titel, Fortschritt. */
	static void drawNowPlaying(GuiGraphics g, int x, int y, int w, int h, boolean withButtonsSpace) {
		Draw.round(g, x, y, w, h, 6, 0x50000000);
		Draw.roundBorder(g, x, y, w, h, 6, Theme.white(0x22));
		Spotify.Track t = Spotify.track();
		int cs = Math.min(h - 12, 56);
		int cx = x + 8, cy = y + 6;
		Draw.round(g, cx, cy, cs, cs, 4, Theme.white(0x14));
		if (t != null) {
			BufferedImage cover = Spotify.cover(t.imageUrl());
			if (cover != null) {
				Textures.Tex tex = Textures.image("spotify_cover", t.imageUrl(), cover);
				if (tex != null) Textures.drawCover(g, tex, cx, cy, cs, cs);
			}
		} else {
			Draw.centered(g, "♪", cx + cs / 2, cy + cs / 2 - 4, Theme.TEXT_DIM);
		}

		int tx = cx + cs + 10;
		int tw = x + w - tx - 10;
		if (t == null) {
			String m = Spotify.message().isEmpty() ? statusText() : Spotify.message();
			Draw.text(g, Draw.fit("Gerade läuft nichts", tw), tx, cy + 2, Theme.TEXT);
			Draw.text(g, Draw.fit(m, tw), tx, cy + 14, Theme.TEXT_DIM);
			return;
		}
		Draw.text(g, Draw.fit("§l" + t.title(), tw), tx, cy + 1, Theme.TEXT);
		Draw.text(g, Draw.fit(t.artist(), tw), tx, cy + 12, Theme.TEXT_SOFT);
		// Fortschritt
		int barY = cy + 25;
		long now = t.progressNow();
		float f = t.durationMs() <= 0 ? 0 : (float) now / t.durationMs();
		Draw.round(g, tx, barY, tw, 3, 1, Theme.white(0x22));
		Draw.round(g, tx, barY, Math.max(3, Math.round(tw * f)), 3, 1, Theme.accent());
		String time = Spotify.time(now) + " / " + Spotify.time(t.durationMs());
		String info = (t.playing() ? "▶ läuft" : "❚❚ Pause") + (t.shuffle() ? " · ⇄" : "") + (t.volume() >= 0 ? " · " + t.volume() + "%" : "")
				+ (t.device().isEmpty() ? "" : " · " + t.device());
		Draw.text(g, time, tx, barY + 6, Theme.TEXT_DIM);
		if (!withButtonsSpace) {
			Draw.text(g, Draw.fit(info, tw - Draw.width(time) - 8), tx + Draw.width(time) + 8, barY + 6, Theme.TEXT_FAINT);
		}
		if (!Spotify.message().isEmpty()) {
			Draw.text(g, Draw.fit(Spotify.message(), tw), tx, barY + 17, Theme.accent());
		}
	}

	static String statusText() {
		return switch (Spotify.status()) {
			case NO_CLIENT_ID -> "Trag zuerst deine Client-ID ein";
			case NOT_CONNECTED -> "Noch nicht verbunden";
			case WAITING_FOR_BROWSER -> "Warte auf Anmeldung im Browser …";
			case CONNECTED -> "Verbunden";
			case ERROR -> "Fehler";
		};
	}

	// ------------------------------------------------------------- Liste

	private int maxScroll() {
		return Math.max(0, Spotify.playlists().size() * ROW - listH);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - v * ROW));
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();
		if (mx < listX || mx > listX + listW || my < listY || my > listY + listH) return false;
		int idx = (int) ((my - listY + scroll) / ROW);
		List<Spotify.Playlist> pls = Spotify.playlists();
		if (idx >= 0 && idx < pls.size()) {
			Spotify.playPlaylist(pls.get(idx));
			return true;
		}
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Backdrop.render(g, this.width, this.height, this.minecraft.level != null);
		g.pose().pushMatrix();
		g.pose().translate(90, 14);
		g.pose().scale(1.35f);
		Draw.text(g, "Spotify", 0, 0, Theme.TEXT);
		g.pose().popMatrix();
		Draw.text(g, statusText(), 90 + Math.round(Draw.width("Spotify") * 1.35f) + 10, 17, Theme.TEXT_DIM);
		drawNowPlaying(g, 20, 44, this.width - 40, 74, true);
		Draw.glass(g, listX - 6, listY - 16, listW + 12, listH + 22, 8, Theme.panel(), Theme.white(0x2A));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		Draw.text(g, "Deine Playlists · Klick = abspielen", listX, listY - 11, Theme.TEXT_SOFT);

		List<Spotify.Playlist> pls = Spotify.playlists();
		scroll = Math.min(scroll, maxScroll());
		if (pls.isEmpty()) {
			String m = Spotify.status() != Spotify.Status.CONNECTED ? "Erst im Ömer-Menü unter Musik verbinden"
					: (Spotify.loadingPlaylists() ? "Lädt …" : "Keine Playlists gefunden");
			Draw.centered(g, m, this.width / 2, listY + 20, Theme.TEXT_DIM);
			return;
		}
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		for (int i = 0; i < pls.size(); i++) {
			int y = listY + i * ROW - (int) scroll;
			if (y + ROW < listY || y > listY + listH) continue;
			Spotify.Playlist p = pls.get(i);
			boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + ROW - 2
					&& mouseY >= listY && mouseY <= listY + listH;
			Draw.round(g, listX, y, listW, ROW - 3, 5, Theme.white(hover ? 0x1E : 0x0A));
			int cs = ROW - 9;
			BufferedImage img = Spotify.cover(p.imageUrl());
			Draw.round(g, listX + 4, y + 3, cs, cs, 3, Theme.white(0x14));
			if (img != null) {
				Textures.Tex tex = Textures.image("pl_" + Integer.toHexString(p.uri().hashCode()), p.imageUrl(), img);
				if (tex != null) Textures.drawCover(g, tex, listX + 4, y + 3, cs, cs);
			}
			Draw.text(g, Draw.fit(p.name(), listW - cs - 80), listX + cs + 12, y + 5, Theme.TEXT);
			Draw.text(g, p.tracks() + " Songs", listX + cs + 12, y + 15, Theme.TEXT_DIM);
			if (hover) Draw.text(g, "▶ abspielen", listX + listW - 70, y + 10, Theme.accent());
		}
		g.disableScissor();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
