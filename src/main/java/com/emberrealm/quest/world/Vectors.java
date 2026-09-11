package com.emberrealm.quest.world;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** float[] helpers: Redis vector fields expect little-endian FLOAT32 blobs. */
public final class Vectors {

    private Vectors() {
    }

    public static byte[] toBlob(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : vector) buffer.putFloat(f);
        return buffer.array();
    }

    public static float[] fromBlob(byte[] blob) {
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[blob.length / 4];
        for (int i = 0; i < out.length; i++) out[i] = buffer.getFloat();
        return out;
    }

    public static Double[] toDoubles(float[] vector) {
        Double[] out = new Double[vector.length];
        for (int i = 0; i < vector.length; i++) out[i] = (double) vector[i];
        return out;
    }

    public static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
