package com.steadypower.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Collections;
import java.util.List;

public class PowerHistogramView extends View {
    private static final double BIN_W = 0.05;
    private List<PowerRepository.Sample> data = Collections.emptyList();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PowerHistogramView(Context context) { super(context); init(); }
    public PowerHistogramView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        barPaint.setColor(Color.rgb(63, 133, 79));
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(dp(11));
        gridPaint.setColor(Color.rgb(225, 225, 225));
        gridPaint.setStrokeWidth(dp(1));
        setBackgroundColor(Color.WHITE);
    }

    public void setData(List<PowerRepository.Sample> samples) {
        data = samples == null ? Collections.emptyList() : samples;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float left = dp(46), right = dp(12), top = dp(30), bottom = dp(36);
        float cw = Math.max(1, w - left - right);
        float ch = Math.max(1, h - top - bottom);

        if (data.isEmpty()) {
            textPaint.setTextSize(dp(14));
            canvas.drawText("停止测试后可直接看主要功耗分布", left, h / 2f, textPaint);
            return;
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (PowerRepository.Sample s : data) {
            min = Math.min(min, s.powerW);
            max = Math.max(max, s.powerW);
        }
        double start = Math.floor(min / BIN_W) * BIN_W;
        double end = Math.ceil(max / BIN_W) * BIN_W;
        if (end <= start) end = start + BIN_W;
        int bins = Math.max(1, (int) Math.ceil((end - start) / BIN_W));
        int[] counts = new int[bins];
        for (PowerRepository.Sample s : data) {
            int idx = (int) Math.floor((s.powerW - start) / BIN_W);
            if (idx < 0) idx = 0;
            if (idx >= bins) idx = bins - 1;
            counts[idx]++;
        }

        int maxCount = 1;
        int peak = 0;
        for (int i = 0; i < bins; i++) {
            if (counts[i] > maxCount) { maxCount = counts[i]; peak = i; }
        }

        for (int i = 0; i <= 4; i++) {
            float y = top + ch * i / 4f;
            canvas.drawLine(left, y, left + cw, y, gridPaint);
            int value = Math.round(maxCount * (1f - i / 4f));
            canvas.drawText(String.valueOf(value), dp(8), y + dp(4), textPaint);
        }
        canvas.drawText("次数", dp(6), top - dp(7), textPaint);

        float gap = bins <= 50 ? dp(1) : 0f;
        float bw = cw / bins;
        for (int i = 0; i < bins; i++) {
            float x1 = left + i * bw + gap / 2f;
            float x2 = left + (i + 1) * bw - gap / 2f;
            float barH = ch * counts[i] / (float) maxCount;
            canvas.drawRect(x1, top + ch - barH, Math.max(x1 + 1, x2), top + ch, barPaint);
        }

        int labelStep = Math.max(1, (int) Math.ceil(bins / 5.0));
        for (int i = 0; i < bins; i += labelStep) {
            String label = String.format(java.util.Locale.US, "%.2f", start + i * BIN_W);
            canvas.drawText(label, left + i * bw, h - dp(10), textPaint);
        }
        canvas.drawText("W", left + cw - dp(10), h - dp(10), textPaint);

        double peakStart = start + peak * BIN_W;
        String peakText = String.format(java.util.Locale.US,
                "最高柱：%.2f–%.2f W（%d 次）", peakStart, peakStart + BIN_W, counts[peak]);
        canvas.drawText(peakText, left, dp(19), textPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
