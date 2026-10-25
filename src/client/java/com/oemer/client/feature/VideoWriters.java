package com.oemer.client.feature;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Schreibt die JPEG-Bilder als Video: AVI (Motion-JPEG, sofort fertig) oder MP4 (H.264 über JCodec). */
final class VideoWriters {
	private VideoWriters() {
	}

	private static void fourcc(ByteBuffer b, String s) {
		for (int i = 0; i < 4; i++) b.put((byte) s.charAt(i));
	}

	/** AVI mit Motion-JPEG – spielt in VLC, Windows-Player (je nach Codec) und fast allen Schnittprogrammen. */
	static void writeAvi(Path file, List<byte[]> frames, int w, int h, int fps) throws IOException {
		int n = frames.size();
		int maxFrame = 0;
		long moviData = 4;
		for (byte[] f : frames) {
			maxFrame = Math.max(maxFrame, f.length);
			moviData += 8 + f.length + (f.length & 1);
		}
		int hdrlSize = 4 + (8 + 56) + (8 + (4 + (8 + 56) + (8 + 40)));
		long idxSize = 16L * n;
		long riffSize = 4 + (8 + hdrlSize) + (8 + moviData) + (8 + idxSize);

		ByteBuffer head = ByteBuffer.allocate(12 + 8 + hdrlSize + 12).order(ByteOrder.LITTLE_ENDIAN);
		fourcc(head, "RIFF");
		head.putInt((int) riffSize);
		fourcc(head, "AVI ");
		fourcc(head, "LIST");
		head.putInt(hdrlSize);
		fourcc(head, "hdrl");
		fourcc(head, "avih");
		head.putInt(56);
		head.putInt(1_000_000 / fps);
		head.putInt(maxFrame * fps);
		head.putInt(0);
		head.putInt(0x10); // hat Index
		head.putInt(n);
		head.putInt(0);
		head.putInt(1);
		head.putInt(maxFrame);
		head.putInt(w);
		head.putInt(h);
		head.putInt(0).putInt(0).putInt(0).putInt(0);
		fourcc(head, "LIST");
		head.putInt(4 + (8 + 56) + (8 + 40));
		fourcc(head, "strl");
		fourcc(head, "strh");
		head.putInt(56);
		fourcc(head, "vids");
		fourcc(head, "MJPG");
		head.putInt(0);
		head.putShort((short) 0).putShort((short) 0);
		head.putInt(0);
		head.putInt(1);
		head.putInt(fps);
		head.putInt(0);
		head.putInt(n);
		head.putInt(maxFrame);
		head.putInt(-1);
		head.putInt(0);
		head.putShort((short) 0).putShort((short) 0).putShort((short) w).putShort((short) h);
		fourcc(head, "strf");
		head.putInt(40);
		head.putInt(40);
		head.putInt(w);
		head.putInt(h);
		head.putShort((short) 1);
		head.putShort((short) 24);
		fourcc(head, "MJPG");
		head.putInt(w * h * 3);
		head.putInt(0).putInt(0).putInt(0).putInt(0);
		fourcc(head, "LIST");
		head.putInt((int) moviData);
		fourcc(head, "movi");
		head.flip();

		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
			out.write(head.array(), 0, head.limit());
			ByteBuffer idx = ByteBuffer.allocate((int) idxSize).order(ByteOrder.LITTLE_ENDIAN);
			int offset = 4;
			ByteBuffer ch = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
			for (byte[] f : frames) {
				ch.clear();
				fourcc(ch, "00dc");
				ch.putInt(f.length);
				out.write(ch.array(), 0, 8);
				out.write(f);
				if ((f.length & 1) != 0) out.write(0);
				fourcc(idx, "00dc");
				idx.putInt(0x10);
				idx.putInt(offset);
				idx.putInt(f.length);
				offset += 8 + f.length + (f.length & 1);
			}
			ByteBuffer ih = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
			fourcc(ih, "idx1");
			ih.putInt((int) idxSize);
			out.write(ih.array(), 0, 8);
			out.write(idx.array(), 0, (int) idxSize);
		}
	}

	/** MP4 (H.264) – läuft überall, auch auf Discord. Das Verschlüsseln dauert ein paar Sekunden bis Minuten. */
	static void writeMp4(Path file, List<byte[]> frames, int w, int h, int fps) throws IOException {
		org.jcodec.api.awt.AWTSequenceEncoder enc = org.jcodec.api.awt.AWTSequenceEncoder.createSequenceEncoder(file.toFile(), fps);
		try {
			byte[] last = null;
			BufferedImage img = null;
			for (byte[] f : frames) {
				if (f != last) {
					img = ImageIO.read(new ByteArrayInputStream(f));
					last = f;
				}
				enc.encodeImage(img);
			}
		} finally {
			enc.finish();
		}
	}
}
