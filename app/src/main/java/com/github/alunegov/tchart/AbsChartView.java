package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

public abstract class AbsChartView extends View {
    protected ChartInputData inputData;

    protected ChartDrawData drawData;

    // настройки отрисовки линий
    protected Paint[] linesPaints;

    public AbsChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void updateLineVisibility(int lineIndex, boolean visible) {
        if (drawData == null) {
            return;
        }

        drawData.updateLineVisibility(lineIndex, visible);

        invalidate();
    }

    protected void drawLines(@NotNull Canvas canvas) {
        assert drawData != null;
        assert linesPaints != null;

        final Path[] paths = drawData.getLinesPaths();
        final Set<Integer> invisibleLinesIndexes = drawData.getInvisibleLinesIndexes();

        assert paths.length == linesPaints.length;
        for (int i = 0; i < paths.length; i++) {
            if (!invisibleLinesIndexes.contains(i)) {
                canvas.drawPath(paths[i], linesPaints[i]);
            }
        }
    }
}
