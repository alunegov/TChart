package com.github.alunegov.tchart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Преобразователь json-строки в данные графиков
public class ChartInputDataMapper {
    private static final String CHART_DATA_CHARSET = "UTF8";

    public interface ResourceLoader {
        String[] listResources(@NotNull String path) throws IOException;

        InputStream openResource(String fileName) throws IOException;
    }

    // Абстракция над android.Color
    public interface ColorParser {
        // преобразование строки в код цвета (ref Color.parseColor)
        int parseColor(String color);
    }

    /*public static @Nullable ChartInputData find(String s) {
        return null;
    }*/

    public static @NotNull List<ChartInputData> load(@NotNull ResourceLoader resourceLoader, @NotNull ColorParser colorParser) throws IOException, JSONException {
        final List<ChartInputData> res = new ArrayList<>();

        final String[] resources = resourceLoader.listResources("contest");

        for (String r: resources) {
            final String fileName = "contest" +  File.separator + r + File.separator + "overview.json";
            final String json = ChartUtils.readStreamToString(resourceLoader.openResource(fileName), CHART_DATA_CHARSET);

            final ChartInputData cid = loadChart(json, colorParser);

            res.add(cid);
        }

        return res;
    }

    public static @NotNull List<ChartInputData> load(@NotNull String json, @NotNull ColorParser colorParser) throws JSONException {
        final List<ChartInputData> res = new ArrayList<>();
        final JSONArray ja = new JSONArray(json);
        for (int i = 0; i < ja.length(); i++) {
            final JSONObject jo = ja.getJSONObject(i);
            res.add(parseChart(jo, colorParser));
        }
        return res;
    }

    private static @NotNull ChartInputData loadChart(@NotNull String json, @NotNull ColorParser colorParser) throws JSONException {
        final JSONObject jo = new JSONObject(json);
        return parseChart(jo, colorParser);
    }

    private static @NotNull ChartInputData parseChart(@NotNull JSONObject jo, @NotNull ColorParser colorParser) throws JSONException {
        final JSONArray jColumns = jo.getJSONArray("columns");
        final JSONObject jTypes = jo.getJSONObject("types");
        final JSONObject jNames = jo.getJSONObject("names");
        final JSONObject jColors = jo.getJSONObject("colors");

        if (jColumns.length() <= 1) {
            throw new JSONException("not enough lines (2 or more req.)");
        }

        final int linesCount = jColumns.length() - 1;  // without x-type line
        final int firstLinePointsCount = detectPointsCount(jColumns);
        final ChartInputData.LineType linesType = detectLinesType(jTypes);
        final BitSet flags = new BitSet();
        if (jo.has("percentage") && jo.getBoolean("percentage")) {
            flags.set(ChartInputData.FLAG_PERCENTAGE);
        }
        if (jo.has("stacked") && jo.getBoolean("stacked")) {
            flags.set(ChartInputData.FLAG_STACKED);
        }
        if (jo.has("y_scaled") && jo.getBoolean("y_scaled")) {
            flags.set(ChartInputData.FLAG_Y_SCALED);
        }

        final ChartInputData res = new ChartInputData(linesCount, firstLinePointsCount, linesType, flags);
        int l = 0;
        boolean gotX = false;

        for (int j = 0; j < jColumns.length(); j++) {
            final JSONArray jLine = jColumns.getJSONArray(j);

            final String lineId = jLine.getString(0);
            final String lineTypeAsStr = jTypes.getString(lineId);

            if (lineTypeAsStr.equals("x")) {
                if (gotX) {
                    throw new JSONException("duplicate x-type line");
                }

                gotX = true;

                for (int k = 0; k < firstLinePointsCount; k++) {
                    res.XValues[k] = jLine.getLong(k + 1);
                }
            } else {
                final ChartInputData.LineType lineType = ChartInputData.LineType.valueOf(lineTypeAsStr.toUpperCase());
                if (lineType != linesType) {
                    throw new JSONException("unsupported line type");
                }

                for (int k = 0; k < firstLinePointsCount; k++) {
                    res.LinesValues[l][k] = jLine.getInt(k + 1);
                }
                res.LinesNames[l] = jNames.getString(lineId);
                res.LinesColors[l] = colorParser.parseColor(jColors.getString(lineId));

                l++;
            }
        }

        if (!gotX) {
            throw new JSONException("no x-type line");
        }
        if (l == 0) {
            throw new JSONException("no line-type lines");
        }

        return res;
    }

    private static int detectPointsCount(@NotNull JSONArray jColumns) throws JSONException {
        final JSONArray jFirstLine = jColumns.getJSONArray(0);
        return jFirstLine.length() - 1;  // without lineId at [0]
        //return 90;
    }

    private static ChartInputData.LineType detectLinesType(@NotNull JSONObject jTypes) throws JSONException {
        ChartInputData.LineType linesType = ChartInputData.LineType.LINE;
        final Iterator<String> linesTypesIter = jTypes.keys();
        while (linesTypesIter.hasNext()) {
            final String lineId = linesTypesIter.next();
            final String lineTypeAsStr = jTypes.getString(lineId);
            if (!lineTypeAsStr.equals("x")) {
                linesType = ChartInputData.LineType.valueOf(lineTypeAsStr.toUpperCase());
                break;
            }
        }
        return linesType;
    }
}
