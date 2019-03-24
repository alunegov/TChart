package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class MainChartSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    ChartInputData inputData;
    ChartDrawData drawData;
    float xLeftValue, xRightValue;
    volatile boolean xChanged;
    RectF area = new RectF();
    volatile boolean areaChanged;

    final Object locker = new Object();

    //float lineWidth;
    // настройки отрисовки линий
    Paint[] linesPaints;

    private DrawThread drawThread;

    public MainChartSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        getHolder().addCallback(this);

        drawThread = new DrawThread(getHolder());
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;
        drawData = new ChartDrawData(inputData);
        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, 3);
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        Log.v("MCSV", String.format("setXRange to %f - %f", xLeftValue, xRightValue));
        synchronized (locker) {
            this.xLeftValue = xLeftValue;
            this.xRightValue = xRightValue;
            xChanged = true;
        }
    }

    public void updateLineVisibility(int lineIndex, boolean visible, boolean doUpdate) {
        // nop
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v("MCSV", "surfaceCreated format");
        drawThread.setRunning(true);
        drawThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v("MCSV", String.format("surfaceChanged format = %d, w = %d,  h = %d", format, width, height));
        synchronized (locker) {
            area.set(0, 0, width, height);
            areaChanged = true;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v("MCSV", "surfaceDestroyed format");
        drawThread.setRunning(false);
        //drawThread.join();
    }

    private class DrawThread extends Thread {

        SurfaceHolder surfaceHolder;
        volatile boolean running;

        public DrawThread(SurfaceHolder surfaceHolder) {
            this.surfaceHolder = surfaceHolder;
        }

        void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public void run() {
            Canvas canvas;

            while (running) {
                synchronized (locker) {
                    if (xChanged) {
                        Log.v("MCSV", String.format("DrawThread xChanged to %f - %f", xLeftValue, xRightValue));
                        xChanged = false;
                        drawData.setXRange(xLeftValue, xRightValue, true);
                    }
                    if (areaChanged) {
                        Log.v("MCSV", String.format("DrawThread areaChanged w = %f,  h = %f", area.width(), area.height()));
                        areaChanged = false;
                        drawData.setArea(area);
                    }
                }

                canvas = surfaceHolder.lockCanvas();

                canvas.drawColor(Color.WHITE);
                drawLines(canvas);

                surfaceHolder.unlockCanvasAndPost(canvas);
            }
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
}
