package com.oemer.client.gui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Die Anleitung im Musik-Menü. Der Text steht in assets/oemerclient/help/music.txt. */
public final class MusicHelp {
	private MusicHelp() {
	}

	/** type: '#' Überschrift, '1' Schritt, '!' Hinweis, ' ' Text */
	public record Block(char type, String text) {
	}

	public record Tab(String name, List<Block> blocks) {
	}

	private static List<Tab> tabs;

	public static synchronized List<Tab> tabs() {
		if (tabs != null) return tabs;
		List<Tab> out = new ArrayList<>();
		try (InputStream in = MusicHelp.class.getResourceAsStream("/assets/oemerclient/help/music.txt")) {
			if (in != null) {
				String all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				Tab cur = null;
				for (String raw : all.split("\r?\n")) {
					String line = raw.strip();
					if (line.isEmpty()) continue;
					if (line.startsWith("@@")) {
						cur = new Tab(line.substring(2).strip(), new ArrayList<>());
						out.add(cur);
					} else if (cur != null) {
						if (line.startsWith("# ")) cur.blocks().add(new Block('#', line.substring(2)));
						else if (line.startsWith("! ")) cur.blocks().add(new Block('!', line.substring(2)));
						else if (line.matches("^\\d+\\. .*")) cur.blocks().add(new Block('1', line));
						else cur.blocks().add(new Block(' ', line));
					}
				}
			}
		} catch (Exception ignored) {
		}
		tabs = out;
		return out;
	}

	/** Text auf Zeilen der Breite maxW umbrechen. */
	public static List<String> wrap(String text, int maxW) {
		List<String> lines = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = cur.length() == 0 ? word : cur + " " + word;
			if (cur.length() > 0 && Draw.width(test) > maxW) {
				lines.add(cur.toString());
				cur = new StringBuilder(word);
			} else {
				cur = new StringBuilder(test);
			}
		}
		if (cur.length() > 0) lines.add(cur.toString());
		return lines;
	}
}
