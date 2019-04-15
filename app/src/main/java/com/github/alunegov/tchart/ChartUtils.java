package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.text.format.DateFormat;
import android.util.TypedValue;

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

    public static Paint makeLinePaint(int color, float width, boolean liney) {
        final Paint paint = new Paint();

        paint.setColor(color);
        if (liney) {
            paint.setAntiAlias(true);
            paint.setStrokeWidth(width);
            paint.setStyle(Paint.Style.STROKE);
        } else {
            paint.setStyle(Paint.Style.FILL);
        }

        return paint;
    }

    public static Paint[] makeLinesPaints(int[] colors, float width, boolean liney) {
        final Paint[] paints = new Paint[colors.length];

        for (int i = 0; i < colors.length; i++) {
            paints[i] = makeLinePaint(colors[i], width, liney);
        }

        return paints;
    }

    private static final String DEF_AXIS_DATE_FORMAT_TEMPLATE = "MMM dd";

    // формат преобразования дат на отметках оси Х
    public static @NotNull String getAxisDateFormatTemplate(@NotNull Context context) {
        return getDateFormatTemplate(context, false, false);
    }

    // формат преобразования дат на плашке
    public static @NotNull String getMarkerDateFormatTemplate(@NotNull Context context) {
        final String res = getDateFormatTemplate(context, true, false);
        return "EEE, " + res;
    }

    // формат преобразования дат в заголовке графика
    public static @NotNull String getXRangeDateFormatTemplate(@NotNull Context context) {
        return getDateFormatTemplate(context, true, true);
    }

    private static @NotNull String getDateFormatTemplate(@NotNull Context context, boolean includeYear, boolean longMonth) {
        String res;

        try {
            final char[] dfo = DateFormat.getDateFormatOrder(context);

            if (dfo.length == 0) {
                res = DEF_AXIS_DATE_FORMAT_TEMPLATE;
            } else {
                final StringBuilder sb = new StringBuilder();

                for (int i = 0; i < dfo.length; i++) {
                    switch (dfo[i]) {
                        case 'y':
                            if (includeYear) {
                                if (sb.length() > 0) {
                                    sb.append(" ");
                                }
                                sb.append("yyyy");
                            }
                            break;

                        case 'M':
                            if (sb.length() > 0) {
                                sb.append(" ");
                            }
                            sb.append(longMonth ? "MMMM" : "MMM");
                            break;

                        case 'd':
                            if (sb.length() > 0) {
                                sb.append(" ");
                            }
                            sb.append("dd");
                            break;
                    }
                }

                res = sb.toString();
            }
        } catch (Exception e) {
            res = DEF_AXIS_DATE_FORMAT_TEMPLATE;
        }

        return res;
    }

    public static int getThemedColor(@NotNull Context context, int resId, int defColor) {
        int res;
        final TypedValue ta = new TypedValue();
        final boolean isResolved = context.getTheme().resolveAttribute(resId, ta, true);
        if (isResolved) {
            if (ta.resourceId != 0) {
                res = ContextCompat.getColor(context, ta.resourceId);
            } else {
                res = ta.data;
            }
        } else {
            res = defColor;
        }
        return res;
    }
}
