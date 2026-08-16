package com.birdmachine.birdiephotomaid;

import android.net.Uri;

public final class PhotoRecord {
    public final long id;
    public final Uri uri;
    public final String displayName;
    public final String relativePath;
    public final long size;
    public final long dateTaken;
    public final int width;
    public final int height;
    public final boolean screenshot;
    public final String screenshotSource;

    public String sha256;
    public long dHash;
    public boolean dHashValid;

    public PhotoRecord(long id, Uri uri, String displayName, String relativePath,
                       long size, long dateTaken, int width, int height,
                       boolean screenshot, String screenshotSource) {
        this.id = id;
        this.uri = uri;
        this.displayName = displayName == null ? "(unnamed)" : displayName;
        this.relativePath = relativePath == null ? "" : relativePath;
        this.size = size;
        this.dateTaken = dateTaken;
        this.width = width;
        this.height = height;
        this.screenshot = screenshot;
        this.screenshotSource = screenshotSource;
    }
}
