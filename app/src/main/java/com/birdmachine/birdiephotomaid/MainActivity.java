package com.birdmachine.birdiephotomaid;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA = 42;
    private static final int REQUEST_DELETE = 43;
    private final Set<PhotoRecord> selected = new LinkedHashSet<>();
    private static final int BG = Color.rgb(255, 248, 235);
    private static final int INK = Color.rgb(40, 28, 24);
    private static final int MUTED = Color.rgb(91, 65, 50);
    private static final int GOLD = Color.rgb(215, 146, 45);

    private final Handler main = new Handler(Looper.getMainLooper());
    private AppPrefs prefs;
    private ThumbnailLoader thumbnails;
    private LinearLayout content;
    private ScrollView scroll;
    private int systemTopInset;
    private int systemBottomInset;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new AppPrefs(this);
        thumbnails = new ThumbnailLoader(this);
        showDashboard();
    }

    @Override protected void onDestroy() {
        thumbnails.shutdown();
        super.onDestroy();
    }

    private void showDashboard() {
        beginScreen(BirdieState.IDLE);
        addTitle("Birdie Photo Maid", "Local-first photo cleanup for Kestrel 🦅✨");

        PhotoScanner.Result last = ScanRepository.get();
        if (last == null) {
            addCard("Fresh nest", "Nothing has been scanned in this app session yet. Birdie Photo Maid remains read-only in v0.2.");
        } else {
            addCard("Last inspection", formatCount(last.photos.size()) + " images · " +
                    formatCount(last.screenshotCount) + " screenshots\n" +
                    last.exactDuplicateGroups.size() + " exact duplicate groups · " +
                    formatBytes(last.exactRecoverableBytes) + " potentially recoverable");
        }

        Button scan = primaryButton(last == null ? "Scan photo library" : "Scan again");
        scan.setOnClickListener(v -> ensurePermissionAndScan());
        content.addView(scan);

        if (last != null) {
            Button results = secondaryButton("Open latest inspection");
            results.setOnClickListener(v -> showResults(last));
            content.addView(results);
        }

        Button comfort = secondaryButton("Screen comfort & dead zones");
        comfort.setOnClickListener(v -> showDeadZoneEditor());
        content.addView(comfort);

        Button personality = secondaryButton("Mascot presence");
        personality.setOnClickListener(v -> showMascotSettings());
        content.addView(personality);

        addCard("Your cleanup flow", "Review photos, select files, share them to your cloud app, verify the backup there, then move local originals to Trash. Empty Trash in your gallery to reclaim space, or choose permanent deletion explicitly.");
        addBottomSafetySpacer();
        finishScreen();
    }

    private void ensurePermissionAndScan() {
        if (hasBroadPhotoAccess() || hasPartialPhotoAccess()) {
            startScan(hasPartialPhotoAccess() && !hasBroadPhotoAccess());
            return;
        }
        requestPhotoAccess();
    }

    private void requestPhotoAccess() {
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            }, REQUEST_MEDIA);
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_MEDIA);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_MEDIA);
        }
    }

    private boolean hasBroadPhotoAccess() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPartialPhotoAccess() {
        return Build.VERSION.SDK_INT >= 34 &&
                checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_MEDIA) return;
        if (hasBroadPhotoAccess() || hasPartialPhotoAccess()) {
            startScan(hasPartialPhotoAccess() && !hasBroadPhotoAccess());
        } else {
            Toast.makeText(this, "Photo access is needed to inspect the library.", Toast.LENGTH_LONG).show();
            showDashboard();
        }
    }

    private void startScan(boolean partial) {
        selected.clear();
        beginScreen(BirdieState.SEARCHING);
        addTitle("Inspecting the nest", partial ?
                "Android granted access to selected photos only." : "Full photo-library access granted.");
        TextView phase = body("Preparing MediaStore…");
        phase.setPadding(0, dp(8), 0, dp(6));
        content.addView(phase);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        content.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(9)));
        addCard("Read-only pass", "I’m indexing metadata, hashing only plausible exact-duplicate candidates, then comparing screenshot perceptual hashes. No files will be modified.");
        addBottomSafetySpacer();
        finishScreen();

        new Thread(() -> {
            try {
                PhotoScanner.Result result = PhotoScanner.scan(this, partial, (done, total, label) -> main.post(() -> {
                    progress.setProgress((int) (1000.0 * done / Math.max(1, total)));
                    phase.setText(label + "  " + formatCount(done) + "/" + formatCount(total));
                }));
                ScanRepository.set(result);
                main.post(() -> showResults(result));
            } catch (Exception error) {
                main.post(() -> showScanError(error));
            }
        }, "birdie-photo-scan").start();
    }

    private void showScanError(Exception error) {
        beginScreen(BirdieState.DISASTER);
        addTitle("The duster jammed", "Nothing was modified.");
        addCard("Scan error", error.getClass().getSimpleName() + "\n" + safe(error.getMessage()));
        Button back = primaryButton("Back to dashboard");
        back.setOnClickListener(v -> showDashboard());
        content.addView(back);
        addBottomSafetySpacer();
        finishScreen();
    }

    private void showResults(PhotoScanner.Result result) {
        int duplicateFiles = 0;
        for (List<PhotoRecord> group : result.exactDuplicateGroups) duplicateFiles += group.size();
        BirdieState state;
        if (result.photos.size() > 12000 || result.exactDuplicateGroups.size() > 150) state = BirdieState.TIRED;
        else if (result.exactDuplicateGroups.isEmpty() && result.nearDuplicateGroups.isEmpty()) state = BirdieState.CLEAN;
        else if (result.screenshotCount > 5000) state = BirdieState.DISASTER;
        else state = BirdieState.FOUND;

        beginScreen(state);
        addTitle("Maid’s inspection report", result.partialAccess ?
                "Based on the photos Android currently allows this app to see." : "Full accessible image library scanned.");
        addMetricRow(new String[][]{
                {formatCount(result.photos.size()), "Images"},
                {formatCount(result.screenshotCount), "Screenshots"},
                {Integer.toString(result.exactDuplicateGroups.size()), "Exact groups"}
        });
        addCard("Exact duplicate evidence", duplicateFiles + " files appear in " + result.exactDuplicateGroups.size() +
                " byte-identical groups. Keeping one file from each group would free up to " +
                formatBytes(result.exactRecoverableBytes) + ".");
        addCard("Visual screenshot matches", result.nearDuplicateGroups.size() +
                " conservative perceptual groups. These are candidates only — visual similarity is not proof of duplication.");
        addCard("Before deleting", "Sharing opens your chosen cloud app, but Feist cannot confirm its upload finished. Check the files in that app before returning to delete them.");

        Button exact = primaryButton("Review exact duplicate groups");
        exact.setEnabled(!result.exactDuplicateGroups.isEmpty());
        exact.setOnClickListener(v -> showDuplicateReview(result, false));
        content.addView(exact);

        Button near = secondaryButton("Review near-duplicate screenshots");
        near.setEnabled(!result.nearDuplicateGroups.isEmpty());
        near.setOnClickListener(v -> showDuplicateReview(result, true));
        content.addView(near);

        Button sources = secondaryButton("Browse screenshots by app/source");
        sources.setEnabled(!result.screenshotsBySource.isEmpty());
        sources.setOnClickListener(v -> showSourceBrowser(result));
        content.addView(sources);

        if (result.partialAccess && Build.VERSION.SDK_INT >= 34) {
            Button reselect = secondaryButton("Change selected photo access");
            reselect.setOnClickListener(v -> requestPhotoAccess());
            content.addView(reselect);
        }

        Button home = secondaryButton("← Dashboard");
        home.setOnClickListener(v -> showDashboard());
        content.addView(home);
        addBottomSafetySpacer();
        finishScreen();

        if (state == BirdieState.TIRED) {
            main.postDelayed(() -> {
                // Only refresh if this result is still current and the Activity is alive.
                if (!isFinishing() && ScanRepository.get() == result) {
                    Toast.makeText(this, "Okay. Worth it. ✨", Toast.LENGTH_SHORT).show();
                }
            }, 3600);
        }
    }

    private void showDuplicateReview(PhotoScanner.Result result, boolean near) {
        List<List<PhotoRecord>> groups = near ? result.nearDuplicateGroups : result.exactDuplicateGroups;
        beginScreen(BirdieState.REVIEWING);
        addTitle(near ? "Visual match review" : "Exact duplicate review",
                near ? "Similar-looking screenshot clusters — inspect before trusting." :
                        "SHA-256 identical files. Select specific copies to share or delete.");
        addSelectionActions(() -> showDuplicateReview(result, near));

        if (groups.isEmpty()) {
            addCard("Nothing here", "No groups were found in this category.");
        } else {
            int groupLimit = Math.min(groups.size(), 50);
            for (int i = 0; i < groupLimit; i++) addDuplicateGroupCard(groups.get(i), i + 1, near);
            if (groups.size() > groupLimit) {
                addCard("Large queue", "Showing the first " + groupLimit + " of " + groups.size() +
                        " groups in v0.2. A virtualized review list is next on the roadmap.");
            }
        }

        Button back = secondaryButton("← Inspection report");
        back.setOnClickListener(v -> showResults(result));
        content.addView(back);
        addBottomSafetySpacer();
        finishScreen();
    }

    private void addDuplicateGroupCard(List<PhotoRecord> group, int number, boolean near) {
        LinearLayout card = cardContainer();
        long savings = group.isEmpty() ? 0 : group.get(0).size * Math.max(0, group.size() - 1L);
        card.addView(heading("Group " + number + " · " + group.size() + " files"));
        TextView detail = body(near ? "Visual candidate — compare carefully" : "Potential savings: " + formatBytes(savings));
        detail.setPadding(0, dp(3), 0, dp(8));
        card.addView(detail);

        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int thumb = dp(132);
        for (PhotoRecord photo : group) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(0, 0, dp(10), 0);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(238, 228, 211));
            cell.addView(image, new LinearLayout.LayoutParams(thumb, thumb));
            thumbnails.load(photo, image, thumb);
            TextView name = body(trim(photo.displayName, 22));
            name.setTextSize(11);
            name.setMaxWidth(thumb);
            cell.addView(name);
            row.addView(cell);
            addSelectionCheckbox(cell, photo);
        }
        strip.addView(row);
        card.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(card, cardParams());
    }

    private void showSourceBrowser(PhotoScanner.Result result) {
        beginScreen(BirdieState.REVIEWING);
        addTitle("Screenshot sources", "Metadata-first guesses. Unknown stays unknown instead of bluffing.");
        for (Map.Entry<String, List<PhotoRecord>> entry : result.screenshotsBySource.entrySet()) {
            LinearLayout card = cardContainer();
            String source = entry.getKey();
            int count = entry.getValue().size();
            card.addView(heading(source));
            card.addView(body(formatCount(count) + " screenshots · tap to browse thumbnails"));
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> showSourceDetail(result, source, 60));
            content.addView(card, cardParams());
        }
        Button back = secondaryButton("← Inspection report");
        back.setOnClickListener(v -> showResults(result));
        content.addView(back);
        addBottomSafetySpacer();
        finishScreen();
    }

    private void showSourceDetail(PhotoScanner.Result result, String source, int limit) {
        List<PhotoRecord> photos = result.screenshotsBySource.get(source);
        if (photos == null) photos = new ArrayList<>();
        final List<PhotoRecord> sourcePhotos = photos;
        beginScreen(BirdieState.REVIEWING);
        addTitle(source, formatCount(sourcePhotos.size()) + " screenshots");
        addSelectionActions(() -> showSourceDetail(result, source, limit));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        int width = getResources().getDisplayMetrics().widthPixels;
        int horizontalReserved = Math.round(width * (prefs.left() + prefs.right()) / 100f) + dp(56);
        int cell = Math.max(dp(76), (width - horizontalReserved) / 3);
        int shown = Math.min(limit, sourcePhotos.size());
        for (int i = 0; i < shown; i++) {
            PhotoRecord photo = sourcePhotos.get(i);
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(3), dp(3), dp(3), dp(8));
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(238, 228, 211));
            tile.addView(image, new LinearLayout.LayoutParams(cell - dp(8), cell - dp(8)));
            thumbnails.load(photo, image, cell);
            TextView stamp = body(dateLabel(photo.dateTaken));
            stamp.setTextSize(10);
            stamp.setGravity(Gravity.CENTER);
            tile.addView(stamp, new LinearLayout.LayoutParams(cell - dp(8), ViewGroup.LayoutParams.WRAP_CONTENT));
            addSelectionCheckbox(tile, photo);
            grid.addView(tile, new GridLayout.LayoutParams());
        }
        content.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (shown < sourcePhotos.size()) {
            Button more = primaryButton("Show 60 more (" + shown + "/" + sourcePhotos.size() + ")");
            more.setOnClickListener(v -> showSourceDetail(result, source, limit + 60));
            content.addView(more);
        }
        Button back = secondaryButton("← Screenshot sources");
        back.setOnClickListener(v -> showSourceBrowser(result));
        content.addView(back);
        addBottomSafetySpacer();
        finishScreen();
    }

    private void addSelectionCheckbox(LinearLayout parent, PhotoRecord photo) {
        CheckBox box = new CheckBox(this);
        box.setText("Select");
        box.setTextSize(12);
        box.setChecked(selected.contains(photo));
        box.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selected.add(photo);
            else selected.remove(photo);
        });
        parent.addView(box);
    }

    private void addSelectionActions(Runnable refresh) {
        Button share = primaryButton("Back up selected with cloud app");
        share.setOnClickListener(v -> shareSelected());
        content.addView(share);
        Button trash = secondaryButton("Move selected to Trash…");
        trash.setOnClickListener(v -> confirmRemoval(false));
        content.addView(trash);
        Button delete = secondaryButton("Permanently delete selected…");
        delete.setOnClickListener(v -> confirmRemoval(true));
        content.addView(delete);
        Button clear = secondaryButton("Clear selection");
        clear.setOnClickListener(v -> { selected.clear(); refresh.run(); });
        content.addView(clear);
    }

    private void shareSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one image first.", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        for (PhotoRecord photo : selected) uris.add(photo.uri);
        Intent share = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        share.setType("image/*");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (uris.size() == 1) share.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        android.content.ClipData clip = android.content.ClipData.newUri(getContentResolver(), "Feist backup", uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new android.content.ClipData.Item(uris.get(i)));
        share.setClipData(clip);
        try {
            startActivity(Intent.createChooser(share, "Back up to your cloud app"));
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "No app can receive these images.", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmRemoval(boolean permanent) {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one image first.", Toast.LENGTH_SHORT).show();
            return;
        }
        PhotoScanner.Result scan = ScanRepository.get();
        if (scan != null) {
            for (List<PhotoRecord> group : scan.exactDuplicateGroups) {
                if (selected.containsAll(group)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Keep a copy of each duplicate")
                            .setMessage("You selected every file in an exact duplicate group. Deselect at least one copy before deleting.")
                            .setPositiveButton("OK", null).show();
                    return;
                }
            }
        }
        long bytes = 0;
        ArrayList<Uri> uris = new ArrayList<>();
        for (PhotoRecord photo : selected) {
            bytes += Math.max(0, photo.size);
            uris.add(photo.uri);
        }
        new AlertDialog.Builder(this)
                .setTitle((permanent ? "Permanently delete " : "Move to Trash: ") + uris.size() + " images?")
                .setMessage((permanent ? "This cannot be undone and may free " + formatBytes(bytes) + ". " : "These images may still occupy storage until you empty Trash in your gallery. ") +
                        "Verify any cloud backup before continuing. Android will ask you to approve next.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {
                    try {
                        PendingIntent request = permanent ? MediaStore.createDeleteRequest(getContentResolver(), uris)
                                : MediaStore.createTrashRequest(getContentResolver(), uris, true);
                        startIntentSenderForResult(request.getIntentSender(), REQUEST_DELETE, null, 0, 0, 0);
                    } catch (Exception error) {
                        Toast.makeText(this, "Could not request removal: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DELETE) {
            selected.clear();
            ScanRepository.set(null);
            Toast.makeText(this, resultCode == RESULT_OK ? "Deletion approved. Scan again for fresh results." : "Deletion cancelled. Scan again to refresh.", Toast.LENGTH_LONG).show();
            showDashboard();
        }
    }

    private void showDeadZoneEditor() {
        beginScreen(BirdieState.IDLE);
        addTitle("Screen comfort", "Drag a boundary in the preview or use the sliders.");
        int[] zones = {prefs.top(), prefs.bottom(), prefs.left(), prefs.right()};
        SafeZonePreview preview = new SafeZonePreview(this);
        preview.setZones(zones[0], zones[1], zones[2], zones[3]);
        content.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        TextView readout = heading(zoneReadout(zones));
        readout.setPadding(0, dp(8), 0, dp(8));
        content.addView(readout);
        SeekBar[] bars = new SeekBar[4];
        String[] names = {"Top", "Bottom", "Left", "Right"};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            content.addView(body(names[i] + " dead zone"));
            SeekBar bar = new SeekBar(this);
            bar.setMax(45);
            bar.setProgress(zones[i]);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                    if (!fromUser) return;
                    zones[index] = value;
                    constrainZones(zones, index);
                    for (int j = 0; j < bars.length; j++) if (bars[j] != null && bars[j].getProgress() != zones[j]) bars[j].setProgress(zones[j]);
                    preview.setZones(zones[0], zones[1], zones[2], zones[3]);
                    readout.setText(zoneReadout(zones));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            bars[i] = bar;
            content.addView(bar);
        }
        preview.setListener((top, bottom, left, right) -> {
            zones[0] = top; zones[1] = bottom; zones[2] = left; zones[3] = right;
            for (int i = 0; i < bars.length; i++) bars[i].setProgress(zones[i]);
            readout.setText(zoneReadout(zones));
        });

        addCard("How Birdie uses this", "Critical content gets horizontal safe padding, top content starts below the reserved top region, and every screen gains enough trailing space that its last controls can scroll above the bottom damaged region.");
        Button save = primaryButton("Save comfort zones");
        save.setOnClickListener(v -> {
            prefs.setDeadZones(zones[0], zones[1], zones[2], zones[3]);
            showDashboard();
        });
        content.addView(save);
        Button reset = secondaryButton("Reset to Kestrel default (bottom 34%)");
        reset.setOnClickListener(v -> {
            prefs.setDeadZones(0, 34, 0, 0);
            showDeadZoneEditor();
        });
        content.addView(reset);
        addBottomSafetySpacer();
        finishScreen();
    }

    private void showMascotSettings() {
        beginScreen(BirdieState.IDLE);
        addTitle("Mascot presence", "Character flavor without making the utility hostage to the mascot.");
        int[] value = {prefs.mascotPresence()};
        String[] labels = {"Off", "Quiet", "Standard", "Animated"};
        TextView current = heading(labels[value[0]]);
        content.addView(current);
        SeekBar bar = new SeekBar(this);
        bar.setMax(3);
        bar.setProgress(value[0]);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value[0] = progress;
                current.setText(labels[progress]);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        content.addView(bar);
        addCard("Current v0.2 behavior", "Off removes mascot artwork. Quiet keeps her static. Standard enables state reactions. Animated uses slightly stronger motion. Future character packs can share the same state engine.");
        Button save = primaryButton("Save mascot setting");
        save.setOnClickListener(v -> { prefs.setMascotPresence(value[0]); showDashboard(); });
        content.addView(save);
        addBottomSafetySpacer();
        finishScreen();
    }

    private void beginScreen(BirdieState state) {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int width = getResources().getDisplayMetrics().widthPixels;
        int leftPad = Math.max(dp(18), Math.round(width * prefs.left() / 100f) + dp(10));
        int rightPad = Math.max(dp(18), Math.round(width * prefs.right() / 100f) + dp(10));
        content.setPadding(leftPad, dp(12), rightPad, dp(10));
        if (prefs.top() > 0) {
            int h = getResources().getDisplayMetrics().heightPixels;
            content.addView(new View(this), new LinearLayout.LayoutParams(1, Math.round(h * prefs.top() / 100f)));
        }
        MascotHeaderView mascot = new MascotHeaderView(this, prefs.mascotPresence());
        mascot.setState(state);
        content.addView(mascot);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            systemTopInset = insets.getSystemWindowInsetTop();
            systemBottomInset = insets.getSystemWindowInsetBottom();
            v.setPadding(0, systemTopInset, 0, 0);
            return insets;
        });
    }

    private void finishScreen() {
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    private void addTitle(String title, String subtitle) {
        TextView t = heading(title);
        t.setTextSize(26);
        content.addView(t);
        TextView s = body(subtitle);
        s.setPadding(0, dp(3), 0, dp(14));
        content.addView(s);
    }

    private void addMetricRow(String[][] metrics) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String[] metric : metrics) {
            LinearLayout cell = cardContainer();
            TextView value = heading(metric[0]);
            value.setTextSize(20);
            TextView label = body(metric[1]);
            label.setTextSize(11);
            cell.addView(value);
            cell.addView(label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(6));
            row.addView(cell, lp);
        }
        content.addView(row);
    }

    private void addCard(String title, String copy) {
        LinearLayout card = cardContainer();
        card.addView(heading(title));
        TextView body = body(copy);
        body.setPadding(0, dp(5), 0, 0);
        card.addView(body);
        content.addView(card, cardParams());
    }

    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(246, 255, 255, 255));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(232, 208, 171));
        card.setBackground(bg);
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        return lp;
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(INK);
        view.setTextSize(17);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(MUTED);
        view.setTextSize(14);
        view.setLineSpacing(0, 1.14f);
        return view;
    }

    private Button primaryButton(String text) { return styledButton(text, true); }
    private Button secondaryButton(String text) { return styledButton(text, false); }

    private Button styledButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(52));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setTextColor(primary ? Color.WHITE : INK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? GOLD : Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), primary ? GOLD : Color.rgb(226, 202, 164));
        button.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        button.setLayoutParams(lp);
        return button;
    }

    private void addBottomSafetySpacer() {
        int height = getResources().getDisplayMetrics().heightPixels;
        int reserve = Math.max(dp(72) + systemBottomInset, Math.round(height * prefs.bottom() / 100f));
        content.addView(new View(this), new LinearLayout.LayoutParams(1, reserve));
    }

    private String zoneReadout(int[] zones) {
        return "Top " + zones[0] + "% · Bottom " + zones[1] + "% · Left " + zones[2] + "% · Right " + zones[3] + "%";
    }

    private void constrainZones(int[] zones, int changed) {
        for (int i = 0; i < zones.length; i++) zones[i] = Math.max(0, Math.min(45, zones[i]));
        if (zones[0] + zones[1] > 80) {
            if (changed == 0) zones[0] = 80 - zones[1]; else zones[1] = 80 - zones[0];
        }
        if (zones[2] + zones[3] > 80) {
            if (changed == 2) zones[2] = 80 - zones[3]; else zones[3] = 80 - zones[2];
        }
    }

    private String dateLabel(long millis) {
        if (millis <= 0) return "date unknown";
        return DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(millis));
    }

    private String formatCount(long value) { return String.format(Locale.US, "%,d", value); }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024) return String.format(Locale.US, "%.1f KB", value);
        value /= 1024.0;
        if (value < 1024) return String.format(Locale.US, "%.1f MB", value);
        value /= 1024.0;
        return String.format(Locale.US, "%.2f GB", value);
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private String safe(String value) { return value == null || value.trim().isEmpty() ? "No detail provided." : value; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
