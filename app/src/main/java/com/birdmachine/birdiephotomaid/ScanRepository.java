package com.birdmachine.birdiephotomaid;

public final class ScanRepository {
    private static volatile PhotoScanner.Result latest;

    private ScanRepository() {}

    public static PhotoScanner.Result get() { return latest; }
    public static void set(PhotoScanner.Result result) { latest = result; }
}
