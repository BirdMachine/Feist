package com.birdmachine.birdiephotomaid;

public final class ScanProgress {
    private ScanProgress() {}

    public static int overall(String phase, int done, int total) {
        double fraction = Math.max(0, Math.min(1, done / (double) Math.max(1, total)));
        if ("Indexing library".equals(phase)) return (int) (fraction * 250);
        if ("Finding exact duplicates".equals(phase)) return 250 + (int) (fraction * 400);
        if ("Comparing screenshots".equals(phase)) return 650 + (int) (fraction * 350);
        return 0;
    }
}
