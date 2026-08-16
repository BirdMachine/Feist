package com.birdmachine.birdiephotomaid;

public enum BirdieState {
    IDLE("Ready to tidy.", "Sharp eyes, clean folders."),
    SEARCHING("Target acquisition…", "Scanning the gallery without touching anything."),
    FOUND("Caught something.", "I found cleanup candidates for you to review."),
    REVIEWING("Your call, Bird.", "I’ll show the evidence; you decide what stays."),
    TIRED("Give me a second…", "That was a large nest of files. Worth it."),
    CLEAN("Suspiciously tidy.", "There isn’t much duplicate clutter here."),
    DISASTER("…Bird.", "What happened in this gallery?");

    public final String headline;
    public final String subtitle;

    BirdieState(String headline, String subtitle) {
        this.headline = headline;
        this.subtitle = subtitle;
    }
}
