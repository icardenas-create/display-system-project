package com.displaysystem.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Draws one image as large as possible without cropping it. */
public class SlideView extends View {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dest = new RectF();
    private Bitmap bitmap;

    public SlideView(Context context) {
        super(context);
    }

    public void show(Bitmap b) {
        bitmap = b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null) return;
        float vw = getWidth(), vh = getHeight();
        float scale = Math.min(vw / bitmap.getWidth(), vh / bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        dest.set((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f);
        canvas.drawBitmap(bitmap, null, dest, paint);
    }
}
