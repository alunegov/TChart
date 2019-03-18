package com.github.alunegov.tchart;

import android.graphics.Paint;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.annotations.NotNull;

public class ChartUtils {
    public static @NotNull String readFileToString(@NotNull File file, @NotNull String charsetName) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream(file);

            return readStreamToString(is, charsetName);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    public static @NotNull String readStreamToString(@NotNull InputStream stream, @NotNull String charsetName) throws IOException {
        int availBytes = stream.available();
        byte[] buffer = new byte[availBytes];
        int bytesRead = stream.read(buffer);
        if (bytesRead != availBytes) {
            throw new IOException("not all bytes read");
        }
        return new String(buffer, charsetName);
    }

    public static Paint makeLinePaint(int color, float width) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        paint.setStyle(Paint.Style.STROKE);

        return paint;
    }

    public static Paint[] makeLinesPaints(int[] colors, float width) {
        Paint[] paints = new Paint[colors.length];
        for (int i = 0; i < colors.length; i++) {
            paints[i] = makeLinePaint(colors[i], width);
        }

        return paints;
    }
}
