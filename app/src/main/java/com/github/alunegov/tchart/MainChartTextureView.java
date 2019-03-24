package com.github.alunegov.tchart;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Set;

import android.view.TextureView;
import org.jetbrains.annotations.NotNull;

public class MainChartTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    ChartInputData inputData;
    ChartDrawData drawData;
    float xLeftValue, xRightValue;
    volatile boolean xChanged;
    RectF area = new RectF();
    volatile boolean areaChanged;

    final Object locker = new Object();

    SurfaceTexture surfaceTexture;

    //float lineWidth;
    // настройки отрисовки линий
    Paint[] linesPaints;

    private DrawThread drawThread;

    public MainChartTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setSurfaceTextureListener(this);

        drawThread = new DrawThread(getSurfaceTexture());
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        this.inputData = inputData;
        drawData = new ChartDrawData(inputData);
        linesPaints = ChartUtils.makeLinesPaints(inputData.LinesColors, 3);
    }

    public void setXRange(float xLeftValue, float xRightValue) {
        Log.v("MCTV", String.format("setXRange to %f - %f", xLeftValue, xRightValue));
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
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.v("MCTV", "onSurfaceTextureAvailable");
        surfaceTexture = surface;
        drawThread.setRunning(true);
        drawThread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.v("MCTV", String.format("onSurfaceTextureSizeChanged w = %d,  h = %d", width, height));
        synchronized (locker) {
            area.set(0, 0, width, height);
            areaChanged = true;
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.v("MCTV", "onSurfaceTextureDestroyed");
        drawThread.setRunning(false);
        //drawThread.join();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        //Log.v("MCTV", "onSurfaceTextureUpdated");
    }

    private class DrawThread extends Thread {

        //SurfaceTexture surfaceTexture;
        volatile boolean running;

        public DrawThread(SurfaceTexture surfaceTexture) {
            //this.surfaceTexture = surfaceTexture;
        }

        void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public void run() {
            Surface surface = null;
            Canvas canvas;

            while (running) {
                if (surface == null) {
                    if (surfaceTexture == null) {
                        continue;
                    }
                    surface = new Surface(surfaceTexture);
                }

                synchronized (locker) {
                    if (xChanged) {
                        Log.v("MCTV", String.format("DrawThread xChanged to %f - %f", xLeftValue, xRightValue));
                        xChanged = false;
                        drawData.setXRange(xLeftValue, xRightValue, true);
                    }
                    if (areaChanged) {
                        Log.v("MCTV", String.format("DrawThread areaChanged w = %f,  h = %f", area.width(), area.height()));
                        areaChanged = false;
                        drawData.setArea(area);
                    }
                }

                canvas = surface.lockCanvas(null);

                canvas.drawColor(Color.WHITE);
                drawLines(canvas);

                surface.unlockCanvasAndPost(canvas);
            }

            surface.release();
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
