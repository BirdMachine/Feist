package com.birdmachine.birdiephotomaid;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PhotoScanner {
    public interface Progress {
        void onProgress(int done, int total, String phase);
    }

    public static final class Result {
        public final List<PhotoRecord> photos = new ArrayList<>();
        public final List<List<PhotoRecord>> exactDuplicateGroups = new ArrayList<>();
        public final List<List<PhotoRecord>> nearDuplicateGroups = new ArrayList<>();
        public final Map<String, List<PhotoRecord>> screenshotsBySource = new LinkedHashMap<>();
        public long exactRecoverableBytes;
        public int screenshotCount;
        public boolean partialAccess;
    }

    private PhotoScanner() {}

    public static Result scan(Context context, boolean partialAccess, Progress progress) throws Exception {
        Result result = new Result();
        result.partialAccess = partialAccess;
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
        };

        try (Cursor cursor = resolver.query(collection, projection, null, null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC")) {
            if (cursor == null) return result;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
            int widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH);
            int heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT);
            int total = cursor.getCount();
            int done = 0;

            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Scan stopped");
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                String path = cursor.getString(pathCol);
                long size = cursor.getLong(sizeCol);
                long date = cursor.getLong(dateCol);
                int width = cursor.getInt(widthCol);
                int height = cursor.getInt(heightCol);
                Uri uri = ContentUris.withAppendedId(collection, id);
                boolean screenshot = ScreenshotClassifier.isScreenshot(name, path);
                String source = screenshot ? ScreenshotClassifier.classify(name, path) : "";
                PhotoRecord record = new PhotoRecord(id, uri, name, path, size, date, width, height,
                        screenshot, source);
                result.photos.add(record);
                if (screenshot) {
                    result.screenshotCount++;
                    result.screenshotsBySource.computeIfAbsent(source, key -> new ArrayList<>()).add(record);
                }
                done++;
                if (progress != null && (done % 125 == 0 || done == total)) {
                    progress.onProgress(done, Math.max(total, 1), "Indexing library");
                }
            }
        }

        findExactDuplicates(resolver, result, progress);
        findNearDuplicateScreenshots(resolver, result, progress);
        sortSourceBuckets(result);
        return result;
    }

    private static void findExactDuplicates(ContentResolver resolver, Result result, Progress progress) throws Exception {
        Map<Long, List<PhotoRecord>> sizeBuckets = new HashMap<>();
        for (PhotoRecord photo : result.photos) {
            if (photo.size > 0) sizeBuckets.computeIfAbsent(photo.size, key -> new ArrayList<>()).add(photo);
        }

        int candidateCount = 0;
        for (List<PhotoRecord> bucket : sizeBuckets.values()) {
            if (bucket.size() > 1) candidateCount += bucket.size();
        }

        Map<String, List<PhotoRecord>> hashBuckets = new LinkedHashMap<>();
        int done = 0;
        for (List<PhotoRecord> bucket : sizeBuckets.values()) {
            if (bucket.size() < 2) continue;
            for (PhotoRecord photo : bucket) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Scan stopped");
                photo.sha256 = sha256(resolver, photo.uri);
                if (photo.sha256 != null) {
                    hashBuckets.computeIfAbsent(photo.sha256, key -> new ArrayList<>()).add(photo);
                }
                done++;
                if (progress != null && (done % 20 == 0 || done == candidateCount)) {
                    progress.onProgress(done, Math.max(candidateCount, 1), "Finding exact duplicates");
                }
            }
        }

        for (List<PhotoRecord> group : hashBuckets.values()) {
            if (group.size() > 1) {
                result.exactDuplicateGroups.add(group);
                long each = group.get(0).size;
                result.exactRecoverableBytes += each * (group.size() - 1L);
            }
        }
        Collections.sort(result.exactDuplicateGroups, (a, b) ->
                Long.compare(groupSavings(b), groupSavings(a)));
    }

    private static void findNearDuplicateScreenshots(ContentResolver resolver, Result result, Progress progress) throws InterruptedException {
        List<PhotoRecord> screenshots = new ArrayList<>();
        for (PhotoRecord photo : result.photos) if (photo.screenshot) screenshots.add(photo);
        if (screenshots.size() < 2) return;

        for (int i = 0; i < screenshots.size(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Scan stopped");
            PhotoRecord photo = screenshots.get(i);
            DHash hash = dHash(resolver, photo.uri);
            photo.dHash = hash.value;
            photo.dHashValid = hash.valid;
            if (progress != null && (i % 20 == 0 || i == screenshots.size() - 1)) {
                progress.onProgress(i + 1, screenshots.size(), "Comparing screenshots");
            }
        }

        UnionFind union = new UnionFind(screenshots.size());
        Set<Long> comparedPairs = new HashSet<>();

        // Four 16-bit bands: if two 64-bit hashes are within Hamming distance 3,
        // at least one entire band must be identical. This avoids an O(n²) full pass.
        for (int band = 0; band < 4; band++) {
            Map<Integer, List<Integer>> buckets = new HashMap<>();
            int shift = band * 16;
            for (int i = 0; i < screenshots.size(); i++) {
                PhotoRecord photo = screenshots.get(i);
                if (!photo.dHashValid) continue;
                int key = (int) ((photo.dHash >>> shift) & 0xffffL);
                buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
            }
            for (List<Integer> bucket : buckets.values()) {
                if (bucket.size() < 2) continue;
                for (int a = 0; a < bucket.size() - 1; a++) {
                    int ia = bucket.get(a);
                    for (int b = a + 1; b < bucket.size(); b++) {
                        int ib = bucket.get(b);
                        long pairKey = (((long) Math.min(ia, ib)) << 32) | (Math.max(ia, ib) & 0xffffffffL);
                        if (!comparedPairs.add(pairKey)) continue;
                        PhotoRecord pa = screenshots.get(ia);
                        PhotoRecord pb = screenshots.get(ib);
                        if (pa.sha256 != null && pa.sha256.equals(pb.sha256)) continue;
                        if (Long.bitCount(pa.dHash ^ pb.dHash) <= 3) union.union(ia, ib);
                    }
                }
            }
        }

        Map<Integer, List<PhotoRecord>> groups = new LinkedHashMap<>();
        for (int i = 0; i < screenshots.size(); i++) {
            if (!screenshots.get(i).dHashValid) continue;
            int root = union.find(i);
            groups.computeIfAbsent(root, ignored -> new ArrayList<>()).add(screenshots.get(i));
        }
        for (List<PhotoRecord> group : groups.values()) {
            if (group.size() > 1) result.nearDuplicateGroups.add(group);
        }
        Collections.sort(result.nearDuplicateGroups, (a, b) -> Integer.compare(b.size(), a.size()));
    }

    private static void sortSourceBuckets(Result result) {
        List<Map.Entry<String, List<PhotoRecord>>> entries = new ArrayList<>(result.screenshotsBySource.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
        result.screenshotsBySource.clear();
        for (Map.Entry<String, List<PhotoRecord>> entry : entries) {
            result.screenshotsBySource.put(entry.getKey(), entry.getValue());
        }
    }

    private static long groupSavings(List<PhotoRecord> group) {
        if (group.isEmpty()) return 0;
        return group.get(0).size * Math.max(0, group.size() - 1L);
    }

    private static String sha256(ContentResolver resolver, Uri uri) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream raw = resolver.openInputStream(uri)) {
            if (raw == null) return null;
            try (BufferedInputStream in = new BufferedInputStream(raw, 128 * 1024)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    private static DHash dHash(ContentResolver resolver, Uri uri) {
        Bitmap source = null;
        Bitmap small = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) return DHash.invalid();
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return DHash.invalid();

            BitmapFactory.Options options = new BitmapFactory.Options();
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / sample > 384) sample *= 2;
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) return DHash.invalid();
                source = BitmapFactory.decodeStream(in, null, options);
            }
            if (source == null) return DHash.invalid();
            small = Bitmap.createScaledBitmap(source, 9, 8, true);

            long hash = 0L;
            int bit = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int left = luminance(small.getPixel(x, y));
                    int right = luminance(small.getPixel(x + 1, y));
                    if (left > right) hash |= (1L << bit);
                    bit++;
                }
            }
            return new DHash(hash, true);
        } catch (Exception ignored) {
            return DHash.invalid();
        } finally {
            if (small != null && small != source) small.recycle();
            if (source != null) source.recycle();
        }
    }

    private static int luminance(int color) {
        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;
        return (299 * r + 587 * g + 114 * b) / 1000;
    }

    private static final class DHash {
        final long value;
        final boolean valid;
        DHash(long value, boolean valid) { this.value = value; this.valid = valid; }
        static DHash invalid() { return new DHash(0L, false); }
    }

    private static final class UnionFind {
        private final int[] parent;
        private final byte[] rank;
        UnionFind(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int i = 0; i < size; i++) parent[i] = i;
        }
        int find(int value) {
            int root = value;
            while (parent[root] != root) root = parent[root];
            while (parent[value] != value) {
                int next = parent[value];
                parent[value] = root;
                value = next;
            }
            return root;
        }
        void union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra == rb) return;
            if (rank[ra] < rank[rb]) parent[ra] = rb;
            else if (rank[ra] > rank[rb]) parent[rb] = ra;
            else { parent[rb] = ra; rank[ra]++; }
        }
    }
}
