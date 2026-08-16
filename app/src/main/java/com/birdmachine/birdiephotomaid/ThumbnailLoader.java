package com.birdmachine.birdiephotomaid;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ThumbnailLoader {
    private final ContentResolver resolver;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    public ThumbnailLoader(Context context) {
        resolver = context.getContentResolver();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024L);
        cache = new LruCache<String, Bitmap>(Math.max(8 * 1024, maxKb / 12)) {
            @Override protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getAllocationByteCount() / 1024;
            }
        };
    }

    public void load(PhotoRecord photo, ImageView target, int px) {
        String key = photo.uri.toString() + ":" + px;
        target.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap bitmap = decode(photo, px);
            if (bitmap == null) return;
            cache.put(key, bitmap);
            main.post(() -> {
                Object tag = target.getTag();
                if (key.equals(tag)) target.setImageBitmap(bitmap);
            });
        });
    }

    private Bitmap decode(PhotoRecord photo, int px) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return resolver.loadThumbnail(photo.uri, new Size(px, px), null);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(photo.uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, options);
            }
            int sample = 1;
            while (Math.max(options.outWidth, options.outHeight) / sample > px * 2) sample *= 2;
            options.inJustDecodeBounds = false;
            options.inSampleSize = Math.max(1, sample);
            try (InputStream in = resolver.openInputStream(photo.uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public void shutdown() { pool.shutdownNow(); }
}
