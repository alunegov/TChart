package com.github.alunegov.tchart;

import android.graphics.Paint;

public class ChartUtils {
    public static Paint makeLinePaint(int color, int width) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        paint.setStyle(Paint.Style.STROKE);

        return paint;
    }

    public static Paint[] makeLinesPaints(int[] colors, int width) {
        Paint[] paints = new Paint[colors.length];
        for (int i = 0; i < colors.length; i++) {
            paints[i] = makeLinePaint(colors[i], width);
        }

        return paints;
    }
}
