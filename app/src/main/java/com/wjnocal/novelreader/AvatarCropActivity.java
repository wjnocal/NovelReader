package com.wjnocal.novelreader;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class AvatarCropActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE_URI = "sourceUri";
    public static final String EXTRA_CROPPED_URI = "croppedUri";
    private static final int OUTPUT_SIZE = 512;

    private CropAvatarView cropView;

    public static Intent createIntent(Context context, Uri sourceUri) {
        Intent intent = new Intent(context, AvatarCropActivity.class);
        intent.putExtra(EXTRA_SOURCE_URI, sourceUri);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Uri sourceUri = getIntent().getParcelableExtra(EXTRA_SOURCE_URI);
        Bitmap bitmap = loadBitmap(sourceUri);
        if (bitmap == null) {
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF111111);
        cropView = new CropAvatarView(this);
        cropView.setBitmap(bitmap);
        root.addView(cropView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView hint = new TextView(this);
        hint.setText("拖动调整位置，双指缩放头像");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(16f);
        hint.setGravity(android.view.Gravity.CENTER);
        hint.setPadding(0, dp(28), 0, dp(12));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP
        );
        root.addView(hint, hintParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.CENTER);
        actions.setPadding(dp(24), dp(16), dp(24), dp(28));
        actions.setBackgroundColor(0xCC111111);
        TextView cancel = createAction("取消");
        TextView confirm = createAction("使用头像");
        cancel.setOnClickListener(v -> finish());
        confirm.setOnClickListener(v -> saveCroppedAvatar());
        actions.addView(cancel);
        actions.addView(confirm);
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM
        );
        root.addView(actions, actionParams);

        setContentView(root);
    }

    private Bitmap loadBitmap(Uri uri) {
        if (uri == null) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(stream, null, bounds);
            }
            int sampleSize = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / sampleSize > 2048) {
                sampleSize *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(stream, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private TextView createAction(String text) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextColor(Color.WHITE);
        action.setTextSize(17f);
        action.setGravity(android.view.Gravity.CENTER);
        action.setPadding(dp(24), dp(12), dp(24), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(8), 0, dp(8), 0);
        action.setLayoutParams(params);
        return action;
    }

    private void saveCroppedAvatar() {
        Bitmap cropped = cropView.exportCroppedBitmap(OUTPUT_SIZE);
        if (cropped == null) {
            Toast.makeText(this, "头像裁剪失败", Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(getFilesDir(), "avatars");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "头像保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        File output = new File(dir, "current_avatar.png");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            cropped.compress(Bitmap.CompressFormat.PNG, 100, stream);
            Intent data = new Intent();
            data.putExtra(EXTRA_CROPPED_URI, Uri.fromFile(output));
            setResult(RESULT_OK, data);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "头像保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class CropAvatarView extends View {
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cropRect = new RectF();
        private Bitmap bitmap;
        private float scale = 1f;
        private float minScale = 1f;
        private float offsetX;
        private float offsetY;
        private float lastX;
        private float lastY;
        private float lastDistance;
        private boolean dragging;
        private boolean pinching;

        CropAvatarView(Context context) {
            super(context);
            shadePaint.setColor(0x99000000);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(context, 2));
            strokePaint.setColor(Color.WHITE);
        }

        void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            resetImage();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            float size = Math.min(w, h) * 0.72f;
            float left = (w - size) / 2f;
            float top = (h - size) / 2f;
            cropRect.set(left, top, left + size, top + size);
            resetImage();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xFF111111);
            if (bitmap != null) {
                canvas.save();
                canvas.translate(offsetX, offsetY);
                canvas.scale(scale, scale);
                canvas.drawBitmap(bitmap, 0, 0, imagePaint);
                canvas.restore();
            }

            Path overlay = new Path();
            overlay.setFillType(Path.FillType.EVEN_ODD);
            overlay.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            overlay.addCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width() / 2f, Path.Direction.CW);
            canvas.drawPath(overlay, shadePaint);
            canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width() / 2f, strokePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    pinching = false;
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2) {
                        pinching = true;
                        dragging = false;
                        lastDistance = pointerDistance(event);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (pinching && event.getPointerCount() >= 2) {
                        float distance = pointerDistance(event);
                        if (lastDistance > 0f) {
                            float focusX = (event.getX(0) + event.getX(1)) / 2f;
                            float focusY = (event.getY(0) + event.getY(1)) / 2f;
                            float newScale = clamp(scale * distance / lastDistance, minScale, minScale * 4f);
                            float factor = newScale / scale;
                            offsetX = focusX - (focusX - offsetX) * factor;
                            offsetY = focusY - (focusY - offsetY) * factor;
                            scale = newScale;
                            clampImage();
                            invalidate();
                        }
                        lastDistance = distance;
                    } else if (dragging) {
                        offsetX += event.getX() - lastX;
                        offsetY += event.getY() - lastY;
                        lastX = event.getX();
                        lastY = event.getY();
                        clampImage();
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    pinching = false;
                    dragging = true;
                    int index = event.getActionIndex() == 0 ? 1 : 0;
                    if (index < event.getPointerCount()) {
                        lastX = event.getX(index);
                        lastY = event.getY(index);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    pinching = false;
                    return true;
                default:
                    return true;
            }
        }

        Bitmap exportCroppedBitmap(int outputSize) {
            if (bitmap == null || cropRect.isEmpty()) {
                return null;
            }
            Bitmap output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Path clip = new Path();
            float radius = outputSize / 2f;
            clip.addCircle(radius, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            float outputScale = outputSize / cropRect.width();
            canvas.translate((offsetX - cropRect.left) * outputScale, (offsetY - cropRect.top) * outputScale);
            canvas.scale(scale * outputScale, scale * outputScale);
            canvas.drawBitmap(bitmap, 0, 0, imagePaint);
            return output;
        }

        private void resetImage() {
            if (bitmap == null || cropRect.isEmpty()) {
                return;
            }
            minScale = Math.max(cropRect.width() / bitmap.getWidth(), cropRect.height() / bitmap.getHeight());
            scale = minScale;
            offsetX = cropRect.centerX() - bitmap.getWidth() * scale / 2f;
            offsetY = cropRect.centerY() - bitmap.getHeight() * scale / 2f;
            clampImage();
        }

        private void clampImage() {
            if (bitmap == null || cropRect.isEmpty()) {
                return;
            }
            float imageWidth = bitmap.getWidth() * scale;
            float imageHeight = bitmap.getHeight() * scale;
            if (imageWidth <= cropRect.width()) {
                offsetX = cropRect.centerX() - imageWidth / 2f;
            } else {
                offsetX = Math.min(cropRect.left, Math.max(cropRect.right - imageWidth, offsetX));
            }
            if (imageHeight <= cropRect.height()) {
                offsetY = cropRect.centerY() - imageHeight / 2f;
            } else {
                offsetY = Math.min(cropRect.top, Math.max(cropRect.bottom - imageHeight, offsetY));
            }
        }

        private float pointerDistance(MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private int dp(Context context, int value) {
            return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
