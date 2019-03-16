package com.github.alunegov.tchart;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Преобразователь json-строки в данные графиков
public class ChartInputDataMapper {
    // Абстракция над android.Color
    public interface ColorParser {
        // преобразование строки в код цвета (ref Color.parseColor)
        int parseColor(String color);
    }

    public static @NotNull List<ChartInputData> load(@NotNull String json, @NotNull ColorParser colorParser) throws JSONException {
        List<ChartInputData> res = new ArrayList<>();

        JSONArray ja = new JSONArray(json);
        for (int i = 0; i < ja.length(); i++) {
            JSONObject jo = ja.getJSONObject(i);

            JSONArray jColumns = jo.getJSONArray("columns");
            JSONObject jTypes = jo.getJSONObject("types");
            JSONObject jNames = jo.getJSONObject("names");
            JSONObject jColors = jo.getJSONObject("colors");

            if (jColumns.length() <= 1) {
                throw new JSONException("not enough lines (2 or more req.)");
            }

            int linesCount = jColumns.length() - 1;  // without x-type line

            JSONArray jFirstLine = jColumns.getJSONArray(0);
            int firstLinePointsCount = jFirstLine.length() - 1;  // without line label

            ChartInputData data = new ChartInputData(linesCount, firstLinePointsCount);
            int l = 0;
            boolean gotX = false;

            for (int j = 0; j < jColumns.length(); j++) {
                JSONArray jLine = jColumns.getJSONArray(j);

                String lineId = jLine.getString(0);
                String lineType = jTypes.getString(lineId);

                if (lineType.equals("x")) {
                    if (gotX) {
                        throw new JSONException("duplicate x-type line");
                    }

                    gotX = true;

                    for (int k = 1; k < jLine.length(); k++) {
                        data.XValues[k - 1] = jLine.getLong(k);
                    }
                } else if (lineType.equals("line")) {
                    for (int k = 1; k < jLine.length(); k++) {
                        data.LinesValues[l][k - 1] = jLine.getInt(k);
                    }
                    data.LinesNames[l] = jNames.getString(lineId);
                    data.LinesColors[l] = colorParser.parseColor(jColors.getString(lineId));

                    l++;
                } else {
                    throw new JSONException("unsupported line type");
                }
            }

            if (!gotX) {
                throw new JSONException("no x-type line");
            }
            if (l == 0) {
                throw new JSONException("no line-type lines");
            }

            res.add(data);
        }

        return res;
    }
}
