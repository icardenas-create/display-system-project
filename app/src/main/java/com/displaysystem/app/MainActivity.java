package com.displaysystem.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen slideshow. Remote control:
 *   OK or Menu  = open settings
 *   Left / Right = previous / next image
 *   Back = ignored, so the display can't be closed by accident (use Home to leave)
 */
public class MainActivity extends Activity {
    private static final long SYNC_EVERY_MS = 5 * 60 * 1000;
    private static final long STATUS_SHOW_MS = 10_000;
    private static final int HIGHLIGHT = 0xFF8A5A2B;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Settings settings;
    private DriveSync drive;

    private FrameLayout root;
    private FrameLayout content;
    private SlideView slide;
    private TextView status;
    private LinearLayout panel;
    private EditText folderField;
    private TextView secondsRow;
    private TextView rotationRow;
    private TextView infoText;
    private final List<View> panelItems = new ArrayList<>();

    private List<File> images = new ArrayList<>();
    private int index;
    private String lastSync = "Not checked yet.";

    private final Runnable advance = () -> {
        showImage(index + 1);
        scheduleAdvance();
    };
    private final Runnable hideStatus = () -> {
        if (!images.isEmpty()) status.setVisibility(View.GONE);
    };
    private final Runnable syncLoop = new Runnable() {
        @Override
        public void run() {
            syncNow();
            handler.postDelayed(this, SYNC_EVERY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        settings = new Settings(this);
        drive = new DriveSync(new File(getFilesDir(), "images"));
        applyExtras(getIntent());
        buildViews();
        setContentView(root);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyRotation());
        images = drive.savedImages();
        showImage(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(syncLoop);
        scheduleAdvance();
        showStatus(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    /** The setup script sends settings this way, so nobody has to type a long link with the remote. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyExtras(intent);
        folderField.setText(settings.folder());
        refreshLabels();
        applyRotation();
        scheduleAdvance();
        syncNow();
    }

    private void applyExtras(Intent intent) {
        if (intent == null) return;
        if (intent.hasExtra("folder")) settings.setFolder(intent.getStringExtra("folder"));
        if (intent.hasExtra("seconds")) settings.setSeconds(intent.getIntExtra("seconds", 10));
        if (intent.hasExtra("rotation")) settings.setRotation(intent.getIntExtra("rotation", 90));
    }

    // ---------- Screen layout ----------

    private void buildViews() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        content = new FrameLayout(this);
        root.addView(content);

        slide = new SlideView(this);
        content.addView(slide, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(this);
        status.setTextSize(20);
        status.setTextColor(0xFFFFFFFF);
        status.setBackgroundColor(0xCC000000);
        status.setGravity(Gravity.CENTER);
        status.setPadding(32, 24, 32, 24);
        content.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF0101010);
        panel.setPadding(48, 48, 48, 48);
        panel.setVisibility(View.GONE);
        content.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        panel.addView(label("Display System settings", 28));
        panel.addView(label("Google Drive folder link (press OK to type):", 18));
        folderField = new EditText(this);
        folderField.setSingleLine(true);
        folderField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        folderField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        folderField.setText(settings.folder());
        folderField.setTextColor(0xFFFFFFFF);
        folderField.setTextSize(18);
        addItem(folderField);
        secondsRow = addItem(label("", 22));
        rotationRow = addItem(label("", 22));
        addItem(action("Check for new images now", this::syncNow));
        addItem(action("Close", this::closePanel));
        infoText = label("", 16);
        infoText.setPadding(0, 32, 0, 0);
        panel.addView(infoText);
        panel.addView(label("Up/Down: choose   Left/Right: change   Back: close", 16));
        refreshLabels();
    }

    private TextView label(String text, int sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(16, 12, 16, 12);
        return t;
    }

    private TextView action(String text, Runnable onPress) {
        TextView t = label(text, 22);
        t.setTag(onPress);
        return t;
    }

    private <T extends View> T addItem(T view) {
        view.setFocusable(true);
        view.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? HIGHLIGHT : 0x00000000));
        if (view != folderField) panel.addView(view);
        else panel.addView(view, new LinearLayout.LayoutParams(-1, -2));
        panelItems.add(view);
        return view;
    }

    private void refreshLabels() {
        secondsRow.setText("Time per image:  ◀ " + Settings.secondsLabel(settings.seconds()) + " ▶");
        rotationRow.setText("Rotation:  ◀ " + Settings.rotationLabel(settings.rotation()) + " ▶");
        infoText.setText("TV address: " + ipAddress() + "\nImages saved on TV: " + images.size()
                + "\nLast check: " + lastSync);
    }

    /** Fire TV can't rotate its own screen, so the app turns its whole picture instead. */
    private void applyRotation() {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        int r = settings.rotation();
        boolean sideways = r == 90 || r == 270;
        int cw = sideways ? h : w, ch = sideways ? w : h;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) content.getLayoutParams();
        if (lp.width != cw || lp.height != ch) {
            content.setLayoutParams(new FrameLayout.LayoutParams(cw, ch, Gravity.CENTER));
        }
        content.setRotation(r);
    }

    // ---------- Slideshow ----------

    private void showImage(int i) {
        if (images.isEmpty()) {
            slide.show(null);
            showStatus(null);
            return;
        }
        index = ((i % images.size()) + images.size()) % images.size();
        slide.show(decode(images.get(index)));
    }

    private void scheduleAdvance() {
        handler.removeCallbacks(advance);
        if (settings.seconds() > 0 && images.size() > 1 && panel.getVisibility() != View.VISIBLE) {
            handler.postDelayed(advance, settings.seconds() * 1000L);
        }
    }

    /** Shrinks huge photos while loading so the TV doesn't run out of memory. */
    private Bitmap decode(File file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o);
        int target = Math.max(1920, Math.max(root.getWidth(), root.getHeight()));
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getPath(), o);
    }

    private void showStatus(String extra) {
        String text = "Display System   •   " + ipAddress() + "\n" + lastSync;
        if (images.isEmpty()) {
            text += "\n\nNo images yet. Press OK on the remote to open settings and add your Google Drive folder link.";
        }
        if (extra != null) text = extra;
        status.setText(text);
        status.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideStatus);
        handler.postDelayed(hideStatus, STATUS_SHOW_MS);
    }

    // ---------- Google Drive ----------

    private void syncNow() {
        String folder = settings.folder();
        lastSync = "Checking Google Drive…";
        refreshLabels();
        worker.execute(() -> {
            DriveSync.Result result = drive.sync(folder);
            handler.post(() -> onSynced(result));
        });
    }

    private void onSynced(DriveSync.Result result) {
        lastSync = result.message;
        boolean changed = !result.images.equals(images);
        images = result.images;
        refreshLabels();
        if (changed) {
            showImage(0);
            scheduleAdvance();
            showStatus(null);
        }
    }

    // ---------- Remote control ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (panel.getVisibility() == View.VISIBLE) {
            return panelKey(e) || super.dispatchKeyEvent(e);
        }
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MENU:
                if (down) openPanel();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (down) { showImage(index + 1); scheduleAdvance(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) { showImage(index - 1); scheduleAdvance(); }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) showStatus("Press the Home button on the remote to leave the display.");
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Moves by what the user sees on screen, whichever way the picture is rotated. */
    private boolean panelKey(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        View focused = getCurrentFocus();
        int at = Math.max(0, panelItems.indexOf(focused));
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) panelItems.get(Math.max(0, at - 1)).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) panelItems.get(Math.min(panelItems.size() - 1, at + 1)).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focused == folderField) return false;
                if (down) change(focused, e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (focused == folderField) return false; // let the keyboard open
                if (down) change(focused, 1);
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
                if (down) closePanel();
                return true;
        }
        return false;
    }

    private void change(View row, int direction) {
        if (row == secondsRow) {
            settings.setSeconds(Settings.step(Settings.SECONDS_STEPS, settings.seconds(), direction));
        } else if (row == rotationRow) {
            settings.setRotation(Settings.step(Settings.ROTATIONS, settings.rotation(), direction));
            applyRotation();
        } else if (row != null && row.getTag() instanceof Runnable) {
            ((Runnable) row.getTag()).run();
        }
        refreshLabels();
    }

    private void openPanel() {
        handler.removeCallbacks(advance);
        status.setVisibility(View.GONE);
        folderField.setText(settings.folder());
        refreshLabels();
        panel.setVisibility(View.VISIBLE);
        secondsRow.requestFocus();
    }

    private void closePanel() {
        String typed = folderField.getText().toString().trim();
        boolean folderChanged = !typed.equals(settings.folder());
        settings.setFolder(typed);
        panel.setVisibility(View.GONE);
        root.requestFocus();
        scheduleAdvance();
        if (folderChanged) syncNow();
    }

    private static String ipAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // No network: fall through.
        }
        return "no network";
    }
}
