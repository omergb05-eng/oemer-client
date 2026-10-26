package com.oemer.client.feature;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Gefüllter, durchsichtiger Kasten aus 6 Vierecken (für RenderTypes.debugQuads()). */
final class BoxDraw {
	private BoxDraw() {
	}

	private static void quad(VertexConsumer b, Matrix4f m, int c,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		b.addVertex(m, ax, ay, az).setColor(c);
		b.addVertex(m, bx, by, bz).setColor(c);
		b.addVertex(m, cx, cy, cz).setColor(c);
		b.addVertex(m, dx, dy, dz).setColor(c);
	}

	static void box(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
		quad(b, m, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // unten
		quad(b, m, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // oben
		quad(b, m, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // Nord
		quad(b, m, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // Süd
		quad(b, m, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // West
		quad(b, m, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // Ost
	}
}
