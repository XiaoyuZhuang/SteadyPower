package com.steadypower.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.Collections;
import java.util.List;

public class PowerCurveView extends View {
    private List<PowerRepository.Sample> data = Collections.emptyList();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PowerCurveView(Context context) { super(context); init(); }
    public PowerCurveView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        gridPaint.setColor(Color.rgb(225, 225, 225));
        gridPaint.setStrokeWidth(dp(1));
        linePaint.setColor(Color.rgb(38, 112, 214));
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(dp(11));
        axisPaint.setColor(Color.GRAY);
        axisPaint.setStrokeWidth(dp(1));
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
        float left = dp(46), right = dp(12), top = dp(16), bottom = dp(28);
        float cw = Math.max(1, w - left - right);
        float ch = Math.max(1, h - top - bottom);

        if (data.isEmpty()) {
            textPaint.setTextSize(dp(14));
            canvas.drawText("开始测试后显示原始功耗曲线", left, h / 2f, textPaint);
            return;
        }

        double max = 0.0;
        for (PowerRepository.Sample s : data) max = Math.max(max, s.powerW);
        max = Math.max(0.5, Math.ceil(max * 4.0) / 4.0);

        for (int i = 0; i <= 4; i++) {
            float y = top + ch * i / 4f;
            canvas.drawLine(left, y, left + cw, y, gridPaint);
            double value = max * (1.0 - i / 4.0);
            canvas.drawText(String.format(java.util.Locale.US, "%.2f", value), dp(5), y + dp(4), textPaint);
        }
        canvas.drawText("W", dp(7), top - dp(3), textPaint);

        canvas.drawLine(left, top, left, top + ch, axisPaint);
        canvas.drawLine(left, top + ch, left + cw, top + ch, axisPaint);

        long maxElapsed = Math.max(1L, data.get(data.size() - 1).elapsedMs);
        Path path = new Path();
        for (int i = 0; i < data.size(); i++) {
            PowerRepository.Sample s = data.get(i);
            float x = left + cw * (s.elapsedMs / (float) maxElapsed);
            float y = top + ch * (float) (1.0 - s.powerW / max);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        canvas.drawPath(path, linePaint);

        String end = formatTime(maxElapsed / 1000L);
        canvas.drawText("0:00", left, h - dp(7), textPaint);
        float endWidth = textPaint.measureText(end);
        canvas.drawText(end, left + cw - endWidth, h - dp(7), textPaint);
    }

    private String formatTime(long sec) {
        return String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
