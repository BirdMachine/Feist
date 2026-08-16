package com.birdmachine.birdiephotomaid;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScreenshotClassifier {
    private static final Pattern SAMSUNG_SUFFIX = Pattern.compile(
            "(?i)^screenshot[_ -]\\d{8}[-_]?\\d{6}[_ -](.+?)\\.(png|jpg|jpeg|webp)$");

    private ScreenshotClassifier() {}

    public static boolean isScreenshot(String name, String path) {
        String haystack = (safe(name) + " " + safe(path)).toLowerCase(Locale.US);
        return haystack.contains("screenshot") || haystack.contains("screen capture")
                || haystack.contains("screen_capture");
    }

    public static String classify(String name, String path) {
        String n = safe(name);
        Matcher samsung = SAMSUNG_SUFFIX.matcher(n);
        if (samsung.find()) return prettify(samsung.group(1));

        String haystack = (n + " " + safe(path)).toLowerCase(Locale.US);
        String[][] hints = {
                {"discord", "Discord"}, {"reddit", "Reddit"}, {"chrome", "Chrome"},
                {"firefox", "Firefox"}, {"youtube", "YouTube"}, {"spotify", "Spotify"},
                {"slack", "Slack"}, {"gmail", "Gmail"}, {"messages", "Messages"},
                {"settings", "Settings"}, {"camera", "Camera"}, {"gallery", "Gallery"},
                {"elona", "Elona Mobile"}, {"chatgpt", "ChatGPT"}, {"maps", "Maps"},
                {"instagram", "Instagram"}, {"facebook", "Facebook"}, {"tiktok", "TikTok"},
                {"amazon", "Amazon"}, {"walmart", "Walmart"}, {"ebay", "eBay"},
                {"android", "Android"}, {"launcher", "Launcher"}
        };
        for (String[] hint : hints) {
            if (haystack.contains(hint[0])) return hint[1];
        }
        return "Unknown app";
    }

    private static String prettify(String raw) {
        String value = raw.replace('_', ' ').replace('-', ' ').trim();
        if (value.isEmpty()) return "Unknown app";
        return value.length() > 44 ? value.substring(0, 44) + "…" : value;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
