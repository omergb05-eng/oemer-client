package com.oemer.client.feature;

import com.oemer.client.OemerConfig;

/** Gemeinsame Steuerung für YouTube-Musik und Spotify (umschaltbar im Menü unter Musik). */
public final class Music {
	private Music() {
	}

	public static final String[] SERVICES = {"YouTube", "Spotify"};

	/** Was gerade läuft – für HUD und Menü. */
	public record Now(String title, String artist, String imageUrl, long progressMs, long durationMs, boolean playing, String service) {
	}

	public static boolean youtube() {
		return OemerConfig.get().musicService == 0;
	}

	public static void playPause() {
		if (youtube()) YouTubeMusic.playPause();
		else Spotify.playPause();
	}

	public static void next() {
		if (youtube()) YouTubeMusic.next();
		else Spotify.next();
	}

	public static void previous() {
		if (youtube()) YouTubeMusic.previous();
		else Spotify.previous();
	}

	public static void volume(int delta) {
		if (youtube()) YouTubeMusic.volume(delta);
		else Spotify.volume(delta);
	}

	public static void toggleShuffle() {
		if (youtube()) YouTubeMusic.toggleShuffle();
		else Spotify.toggleShuffle();
	}

	/** null = nichts läuft. */
	public static Now now() {
		if (youtube()) {
			YouTubeMusic.Track t = YouTubeMusic.current();
			if (t == null) return null;
			return new Now(t.shownTitle(), t.artist, t.thumb(), YouTubeMusic.position(), YouTubeMusic.duration(),
					YouTubeMusic.playing(), "YouTube");
		}
		if (Spotify.status() != Spotify.Status.CONNECTED) return null;
		Spotify.Track t = Spotify.track();
		if (t == null) return null;
		return new Now(t.title(), t.artist(), t.imageUrl(), t.progressNow(), t.durationMs(), t.playing(), "Spotify");
	}

	public static String time(long ms) {
		return Spotify.time(ms);
	}
}
