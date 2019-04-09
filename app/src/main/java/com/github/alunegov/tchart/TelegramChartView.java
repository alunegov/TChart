package com.github.alunegov.tchart;

import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import simplify.Simplify;

public class TelegramChartView extends LinearLayout {
    private static final int DEF_TEXT_SIZE_SP = 17;
    private static final int DEF_AXIS_TEXT_SIZE_SP = 12;

    private TextView titleView;
    private TextView xRangeView;
    private MainChartView mainChartView;
    private PreviewChartView previewChartView;
    private LineNameListView lineNamesView;

    private MainChartView.XAxisConverter xRangeTextConverter;
    private final @NotNull long[] xRange = new long[2];

    private final @NotNull ValueAnimator zoneChangeAnimator = new ValueAnimator();
    private boolean isPendingZoneChangeAnimation = false;
    private float zoneLeftValuePending, zoneRightValuePending;

    private final @NotNull ValueAnimator lineVisibilityAnimator = new ValueAnimator();
    private int lineVisibilityAnimation_lineIndex;
    private boolean lineVisibilityAnimation_exceptLine;

    private final Handler h = new Handler();

    private int ymin_main1, ymax_main1, ymin_main2, ymax_main2, ymin_main3, ymax_main3;

    public TelegramChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOrientation(VERTICAL);

        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) throw new AssertionError();

        inflater.inflate(R.layout.view_telegram_chart, this, true);

        titleView = (TextView) findViewById(R.id.title);
        xRangeView = (TextView) findViewById(R.id.x_range);
        mainChartView = (MainChartView) findViewById(R.id.main_chart);
        final View cursorPopupView = findViewById(R.id.cursor_popup);
        previewChartView = (PreviewChartView) findViewById(R.id.preview_chart);
        lineNamesView = (LineNameListView) findViewById(R.id.line_names);

        final String xRangeDateFormatTemplate = ChartUtils.getXRangeDateFormatTemplate(context);
        xRangeTextConverter = new MainChartView.XAxisConverter(xRangeDateFormatTemplate);

        mainChartView.setCursorPopupView(cursorPopupView);
        previewChartView.setOnChangeListener(previewChartChangeListener);
        lineNamesView.setOnChangeListener(lineNamesChangeListener);

        //zoneChangeAnimator.setInterpolator(new FastOutSlowInInterpolator());
        zoneChangeAnimator.setDuration(200);
        zoneChangeAnimator.addUpdateListener(zoneChangeAnimatorUpdateListener);

        //lineVisibilityAnimator.setInterpolator(new FastOutSlowInInterpolator());
        //lineVisibilityAnimator.setDuration(200);
        lineVisibilityAnimator.addUpdateListener(lineVisibilityAnimatorUpdateListener);

        final DisplayMetrics dm = context.getResources().getDisplayMetrics();
        final float defTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEF_TEXT_SIZE_SP, dm);
        final float defAxisTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEF_AXIS_TEXT_SIZE_SP, dm);

        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.TelegramChartView, 0, 0);
        try {
            CharSequence title = ta.getText(R.styleable.TelegramChartView_title);
            titleView.setText(title.toString());

            float px = ta.getDimension(R.styleable.TelegramChartView_title_text_size, defTextSize);
            setTitleTextSize(px);

            px = ta.getDimension(R.styleable.TelegramChartView_axis_text_size, defAxisTextSize);
            setAxisTextSize(px);

            px = ta.getDimension(R.styleable.TelegramChartView_line_name_text_size, defTextSize);
            setLineNameTextSize(px);
        } finally {
            ta.recycle();
        }
    }

    private final PreviewChartView.OnChangeListener previewChartChangeListener = new PreviewChartView.OnChangeListener() {
        @Override
        public void onZoneChanged(float zoneLeftValue, float zoneRightValue) {
//            mainChartView.setXRange(zoneLeftValue, zoneRightValue);

            //zoneChangeAnimator.cancel();

            //Log.v("TCV", String.format("left = %f, right = %f, swing = %f onZoneChanged", zoneLeftValue, zoneRightValue, zoneRightValue - zoneLeftValue));

            if (zoneChangeAnimator.isStarted()) {
                //Log.v("TCV", "pending");
                zoneLeftValuePending = zoneLeftValue;
                zoneRightValuePending = zoneRightValue;
                isPendingZoneChangeAnimation = true;
                return;
            }

            //mainChartView.setXRange(zoneLeftValue, zoneRightValue);
            //invalidate();

            startZoneChangeAnimation(zoneLeftValue, zoneRightValue, true);

            // AnimatorListener.onAnimationEnd not working right - sometimes it not fires.
            // https://stackoverflow.com/a/18683419/2968990
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //Log.v("TCV", String.format("run gotPending = %s", isPendingZoneChangeAnimation));
                    if (isPendingZoneChangeAnimation) {
                        isPendingZoneChangeAnimation = false;
                        startZoneChangeAnimation(zoneLeftValuePending, zoneRightValuePending, true);

                        h.postDelayed(this, zoneChangeAnimator.getDuration());
                    }
                }
            }, zoneChangeAnimator.getDuration());
        }
    };

    private void startZoneChangeAnimation(float zoneLeftValue, float zoneRightValue, boolean b) {
        final PropertyValuesHolder xl, xr;
        if (b) {
            final float[] startXRange = new float[2];
            mainChartView.getXRange(startXRange);
            xl = PropertyValuesHolder.ofFloat("xl", startXRange[0], zoneLeftValue);
            xr = PropertyValuesHolder.ofFloat("xr", startXRange[1], zoneRightValue);
        } else {
            xl = PropertyValuesHolder.ofFloat("xl", zoneLeftValue, zoneLeftValue);
            xr = PropertyValuesHolder.ofFloat("xr", zoneRightValue, zoneRightValue);
        }
        //Log.v("TCV", String.format("left = %f, right = %f, swing = %f startZoneChangeAnimation", zoneLeftValue, zoneRightValue, zoneRightValue - zoneLeftValue));

        final int[] startYRange = new int[2];
        final int[] stopYRange = new int[2];

        mainChartView.calcAnimationRanges(zoneLeftValue, zoneRightValue, startYRange, stopYRange);
        final PropertyValuesHolder ymin = PropertyValuesHolder.ofInt("ymin", startYRange[0], stopYRange[0]);
        final PropertyValuesHolder ymax = PropertyValuesHolder.ofInt("ymax", startYRange[1], stopYRange[1]);

        zoneChangeAnimator.setValues(xl, xr, ymin, ymax);
        zoneChangeAnimator.start();
    }

    private final @NotNull ValueAnimator.AnimatorUpdateListener zoneChangeAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            final float xl = (float) animation.getAnimatedValue("xl");
            final float xr = (float) animation.getAnimatedValue("xr");
            final int ymin = (int) animation.getAnimatedValue("ymin");
            final int ymax = (int) animation.getAnimatedValue("ymax");
            //Log.v("TCV", String.format("left = %f, right = %f, swing = %f setXYRange at %d", xl, xr, xr - xl, animation.getCurrentPlayTime()));

            mainChartView.setXYRange(xl, xr, ymin, ymax);

            mainChartView.getXRange(xRange);
            updateXRangeText(xRange[0], xRange[1]);
        }
    };

    private final LineNameListView.OnChangeListener lineNamesChangeListener = new LineNameListView.OnChangeListener() {
        @Override
        public void onCheckedChange(int lineIndex, boolean isChecked) {
            startLineVisibilityAnimation(lineIndex, false, isChecked);
        }

        @Override
        public void onLongClick(int lineIndex) {
            startLineVisibilityAnimation(lineIndex, true, true);
        }
    };

    private void startLineVisibilityAnimation(int lineIndex, boolean exceptLine, boolean isChecked) {
        //mainChartView.updateLineVisibility(lineIndex, isChecked, true);
        //previewChartView.updateLineVisibility(lineIndex, isChecked, true);

        lineVisibilityAnimator.cancel();

        final int[] startYRange = new int[2];
        final int[] stopYRange = new int[2];

        int startLineVisibilityState;
        if (exceptLine) {
            startLineVisibilityState = mainChartView.getLineVisibilityState(lineIndex);
        } else {
            startLineVisibilityState = isChecked ? ChartDrawData.VISIBILITY_STATE_OFF : ChartDrawData.VISIBILITY_STATE_ON;
        }
        final int stopLineVisibilityState = isChecked ? ChartDrawData.VISIBILITY_STATE_ON : ChartDrawData.VISIBILITY_STATE_OFF;

        mainChartView.calcAnimationRanges(lineIndex, exceptLine, stopLineVisibilityState, startYRange, stopYRange);
        final PropertyValuesHolder ymin_main = PropertyValuesHolder.ofInt("ymin_main", startYRange[0], stopYRange[0]);
        final PropertyValuesHolder ymax_main = PropertyValuesHolder.ofInt("ymax_main", startYRange[1], stopYRange[1]);

        previewChartView.calcAnimationRanges(lineIndex, exceptLine, stopLineVisibilityState, startYRange, stopYRange);
        final PropertyValuesHolder ymin_preview = PropertyValuesHolder.ofInt("ymin_preview", startYRange[0], stopYRange[0]);
        final PropertyValuesHolder ymax_preview = PropertyValuesHolder.ofInt("ymax_preview", startYRange[1], stopYRange[1]);

        final PropertyValuesHolder lineVisibilityState = PropertyValuesHolder.ofInt("lineVisibilityState", startLineVisibilityState, stopLineVisibilityState);

        // TODO: переход из/в состояние без графиков
        // TODO: если нет изменения по Y, не нужно вызывать setYRange
        //if ((startYRange[0] == stopYRange[0]) && (startYRange[1] == stopYRange[1])) {
        lineVisibilityAnimation_lineIndex = lineIndex;
        lineVisibilityAnimation_exceptLine = exceptLine;

//            t = SystemClock.elapsedRealtime();

        lineVisibilityAnimator.setValues(ymin_main, ymax_main, ymin_preview, ymax_preview, lineVisibilityState);
        lineVisibilityAnimator.start();

/*                ymin_main1 = startYRange[0];
                ymax_main1 = startYRange[1];
                ymin_main2 = stopYRange[0];
                ymax_main2 = stopYRange[1];
                ymin_main3 = (stopYRange[0] - startYRange[0]) / 10;
                ymax_main3 = (stopYRange[1] - startYRange[1]) / 10;

                prevFrameTimeNanos = 0;
                bb = 0;

                Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 0);*/

/*                postOnAnimationDelayed(new Runnable() {
                    @Override
                    public void run() {
                        boolean cond;
                        if (ymax_main3 > 0) {
                            cond = ymin_main1 < ymin_main2 || ymax_main1 < ymax_main2;
                        } else {
                            cond = ymin_main1 > ymin_main2 || ymax_main1 > ymax_main2;
                        }
                        if (cond) {
                            ymin_main1 += ymin_main3;
                            ymax_main1 += ymax_main3;

                            mainChartView.setYRange(ymin_main1, ymax_main1, true);
                            previewChartView.setYRange(ymin_main1, ymax_main1, true);

                            removeCallbacks(this);
                            postOnAnimationDelayed(this, 16);
                        } else {
                            previewChartView.useCachedLines();
                        }
                    }
                }, 16);*/
    }

/*    private static final long NanoSecPerMSec = 1000000L;
    private long prevFrameTimeNanos;
    private int bb = 0;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            //Trace.beginSection("Choreographer.doFrame");
            //Log.d("TCV", String.format("Choreographer.doFrame at %d", frameTimeNanos));
            if (bb++ < 1) {
                Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 0);
                return;
            } else {
                bb = 0;
            }

            if (prevFrameTimeNanos != 0) {
                long elapsed = (frameTimeNanos - prevFrameTimeNanos) / NanoSecPerMSec;
                if (elapsed > 17) {
                    Log.d("TCV", String.format("elapsed ms %d", elapsed));
                }
                if (elapsed < 16) {
                    //Log.d("TCV", "Choreographer.doFrame in 16 ms");
                    Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 0);
                    return;
                }
            }
            prevFrameTimeNanos = frameTimeNanos;

            boolean cond;
            if (ymax_main3 > 0) {
                cond = ymin_main1 < ymin_main2 || ymax_main1 < ymax_main2;
            } else {
                cond = ymin_main1 > ymin_main2 || ymax_main1 > ymax_main2;
            }
            if (cond) {
                ymin_main1 += ymin_main3;
                ymax_main1 += ymax_main3;

                mainChartView.setYRange(ymin_main1, ymax_main1, true);
                previewChartView.setYRange(ymin_main1, ymax_main1, true);

                Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 0);
            } else {
                previewChartView.useCachedLines();
            }

            //Trace.endSection();
        }
    };*/

    private final @NotNull ValueAnimator.AnimatorUpdateListener lineVisibilityAnimatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            //Trace.beginSection("lineVisibility onAnimationUpdate");

/*            if ((t + 15) > SystemClock.elapsedRealtime()) {
                Log.d("TCV", String.format("skipped at %d < %d", SystemClock.elapsedRealtime(), t + 15));
                return;
            }
            t = SystemClock.elapsedRealtime();*/

            final int ymin_main = (int) animation.getAnimatedValue("ymin_main");
            final int ymax_main = (int) animation.getAnimatedValue("ymax_main");
            final int ymin_preview = (int) animation.getAnimatedValue("ymin_preview");
            final int ymax_preview = (int) animation.getAnimatedValue("ymax_preview");
            final int lineVisibilityState = (int) animation.getAnimatedValue("lineVisibilityState");
            //Log.d("TCV", String.format("ymin_main = %d, ymax_main = %d, lineVisibilityState = %d", ymin_main, ymax_main, lineVisibilityState));

            // порядок методов setYRange и updateLineVisibility важен, п.ч. setYRange сбрасывает кэш-картинку, а
            // previewChartView восстанавливает её по завершению анимации (lineVisibilityState) - сейчас это выключено.
            // НО при обновлении ChartDrawData.updateLineVisibility пересчитываются y (updateYRange), а нам это не
            // нужно - мы сами анимируем изменение.

            mainChartView.updateLineVisibility(lineVisibilityAnimation_lineIndex, lineVisibilityAnimation_exceptLine,
                    lineVisibilityState, false, false);
            previewChartView.updateLineVisibility(lineVisibilityAnimation_lineIndex, lineVisibilityAnimation_exceptLine,
                    lineVisibilityState, false, false);

            mainChartView.setYRange(ymin_main, ymax_main, true);
            previewChartView.setYRange(ymin_preview, ymax_preview, true);

            //Trace.endSection();
        }
    };

    public void setTitle(@NotNull String title) {
        titleView.setText(title);
    }

    public void setTitleTextSize(float px) {
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
    }

    public void setAxisTextSize(float px) {
        mainChartView.setAxisTextSize(px);
    }

    public void setLineNameTextSize(float px) {
        lineNamesView.setTextSize(px);
    }

    public void setInputData(@NotNull ChartInputData inputData) {
        final LineName[] linesNames = new LineName[inputData.LinesNames.length];
        for (int i = 0; i < inputData.LinesNames.length; i++) {
            linesNames[i] = new LineName(inputData.LinesNames[i], inputData.LinesColors[i]);
        }

//        optInputData(inputData);

        mainChartView.setInputData(inputData);
        previewChartView.setInputData(inputData);
        lineNamesView.setLineNames(linesNames);

        final float[] zone = new float[2];
        previewChartView.getZone(zone);

        mainChartView.setXRange(zone[0], zone[1]);

        mainChartView.getXRange(xRange);
        updateXRangeText(xRange[0], xRange[1]);
    }

    private void optInputData(@NotNull ChartInputData inputData) {
        final long[] originalXValues = new long[inputData.XValues.length];
        System.arraycopy(inputData.XValues, 0, originalXValues, 0, inputData.XValues.length);

        float tol;
        //tol = (inputData.XValues[inputData.XValues.length - 1] - inputData.XValues[0]) / 10f;
        tol = 20f;

        for (int j = 0; j < inputData.LinesValues.length; j++) {
            final float[][] f = new float[inputData.LinesValues[j].length][3];
            int l = 0;
            for (int i = 0; i < inputData.LinesValues[j].length; i++) {
                f[l][0] = (float) originalXValues[i];
                f[l][1] = inputData.LinesValues[j][i];
                f[l][2] = 0;
                l++;
            }

            final float[][] res = Simplify.simplify(f, tol, true);

            Log.d("TCV", String.format("points %d -> %d", inputData.LinesValues[j].length, res.length));

            inputData.XValues = new long[res.length];
            inputData.LinesValues[j] = new int[res.length];

            for (int i = 0; i < res.length; i++) {
                inputData.XValues[i] = (long) (res[i][0]);
                inputData.LinesValues[j][i] = (int) res[i][1];
            }
        }
    }

    private void updateXRangeText(long xLeft, long xRight) {
        final String text = String.format("%s - %s", xRangeTextConverter.toText(xLeft), xRangeTextConverter.toText(xRight));
        xRangeView.setText(text);
    }
}
