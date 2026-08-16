package com.birdmachine.birdiephotomaid;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MascotHeaderView extends LinearLayout {
    private final ImageView image;
    private final TextView headline;
    private final TextView subtitle;
    private final int presence;

    public MascotHeaderView(Context context, int presence) {
        super(context);
        this.presence = presence;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(0, dp(4), 0, dp(12));

        image = new ImageView(context);
        image.setImageResource(R.drawable.birdie_maid_mascot);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setClipToOutline(true);
        if (presence > 0) addView(image, new LayoutParams(dp(94), dp(94)));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(VERTICAL);
        copy.setPadding(presence > 0 ? dp(14) : 0, 0, 0, 0);
        headline = new TextView(context);
        headline.setTextColor(Color.rgb(40, 28, 24));
        headline.setTextSize(23);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        subtitle = new TextView(context);
        subtitle.setTextColor(Color.rgb(91, 65, 50));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(3), 0, 0);
        copy.addView(headline, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        copy.addView(subtitle, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(copy, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    public void setState(BirdieState state) {
        headline.setText(state.headline);
        subtitle.setText(state.subtitle);
        if (presence == 0) return;
        image.animate().cancel();
        image.setAlpha(1f);
        image.setRotation(0f);
        image.setScaleX(1f);
        image.setScaleY(1f);
        if (presence < 2) return;

        switch (state) {
            case SEARCHING -> image.animate().rotationBy(1.5f).scaleX(1.025f).scaleY(1.025f).setDuration(500).withEndAction(() ->
                    image.animate().rotationBy(-1.5f).scaleX(1f).scaleY(1f).setDuration(500));
            case FOUND, CLEAN -> image.animate().scaleX(1.045f).scaleY(1.045f).setDuration(220).withEndAction(() ->
                    image.animate().scaleX(1f).scaleY(1f).setDuration(280));
            case TIRED -> image.animate().alpha(0.82f).rotationBy(-1.5f).setDuration(650);
            case DISASTER -> image.animate().rotationBy(2f).setDuration(150).withEndAction(() ->
                    image.animate().rotationBy(-4f).setDuration(180).withEndAction(() ->
                            image.animate().rotation(0f).setDuration(180)));
            default -> { }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
