package com.birdmachine.birdiephotomaid;

public final class ScanRepository {
    private static volatile PhotoScanner.Result latest;
    private static volatile boolean running;
    private static volatile int done;
    private static volatile int total = 1;
    private static volatile String phase = "Preparing library";
    private static volatile String error;
    private static volatile Runnable listener;

    private ScanRepository() {}

    public static PhotoScanner.Result get() { return latest; }
    public static void set(PhotoScanner.Result result) { latest = result; }
    public static boolean isRunning() { return running; }
    public static int done() { return done; }
    public static int total() { return total; }
    public static String phase() { return phase; }
    public static String error() { return error; }
    public static void listen(Runnable callback) { listener = callback; }

    public static void started() {
        latest = null;
        error = null;
        running = true;
        progress(0, 1, "Preparing library");
    }
    public static void progress(int current, int count, String label) {
        done = current;
        total = Math.max(count, 1);
        phase = label;
        notifyListener();
    }
    public static void finished(PhotoScanner.Result result) {
        latest = result;
        running = false;
        notifyListener();
    }
    public static void failed(String message) {
        error = message;
        running = false;
        notifyListener();
    }
    private static void notifyListener() {
        Runnable callback = listener;
        if (callback != null) callback.run();
    }
}
