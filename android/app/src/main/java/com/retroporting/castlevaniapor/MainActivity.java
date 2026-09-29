package com.retroporting.castlevaniapor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

public class MainActivity extends SDLActivity {
    private static final String TAG = "CastlevaniaPoR";

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "SDL2",
            "nds_runner"
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        try {
            Os.setenv("NDS_SCREEN_LAYOUT", "single", true);
            Os.setenv("NDS_GPU2D_THREADED", "0", true);
            Os.setenv("NDS_GPU2D_WORKERS", "1", true);
            Os.setenv("NDS_PERFORMANCE_GOVERNOR", "auto", true);
            Os.setenv("NDS_CYCLE_FAST_LIMIT", "1", true);
            Os.setenv("NDS_CPU_FAST_POLL", "1", true);
            Os.setenv("NDS_3D_RENDERER", "soft", true);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set env vars", e);
        }

        extractAssetsIfNeeded();

        File romFile = new File(getFilesDir(), "Castlevania Portrait of Ruin.nds");
        String sha1 = calculateSHA1(romFile);
        if (!TitleActivity.EXPECTED_ROM_SHA1.equalsIgnoreCase(sha1)) {
            Log.w(TAG, "ROM missing or SHA-1 mismatch in MainActivity! Attempting re-extraction...");
            if (romFile.exists()) {
                romFile.delete();
            }
            copyAssetToFile("Castlevania Portrait of Ruin.nds", romFile, true);
            sha1 = calculateSHA1(romFile);
        }

        if (!TitleActivity.EXPECTED_ROM_SHA1.equalsIgnoreCase(sha1)) {
            Log.w(TAG, "ROM invalid in MainActivity! Returning to TitleActivity.");
            Intent intent = new Intent(this, TitleActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        super.onCreate(savedInstanceState);

        VirtualControlsOverlay overlay = null;
        if (mLayout != null) {
            overlay = new VirtualControlsOverlay(this);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            );
            mLayout.addView(overlay, lp);
        }

        final VirtualControlsOverlay fOverlay = overlay;
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.retroporting.castlevaniapor.OPEN_SETTINGS");
        filter.addAction("com.retroporting.castlevaniapor.OPEN_CONTROLS_TAB");
        filter.addAction("com.retroporting.castlevaniapor.START_EDIT_MODE");
        filter.addAction("com.retroporting.castlevaniapor.SWITCH_SCREEN");
        filter.addAction("com.retroporting.castlevaniapor.SET_SCREEN");
        BroadcastReceiver testReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.retroporting.castlevaniapor.OPEN_SETTINGS".equals(intent.getAction())) {
                    SettingsDialog.show(MainActivity.this, fOverlay);
                } else if ("com.retroporting.castlevaniapor.OPEN_CONTROLS_TAB".equals(intent.getAction())) {
                    SettingsDialog.show(MainActivity.this, fOverlay, true);
                } else if ("com.retroporting.castlevaniapor.START_EDIT_MODE".equals(intent.getAction())) {
                    if (fOverlay != null) fOverlay.startEditMode();
                } else if ("com.retroporting.castlevaniapor.SWITCH_SCREEN".equals(intent.getAction())) {
                    try {
                        VirtualControlsOverlay.nativeToggleMapScreen();
                        Log.i(TAG, "Switched screen via broadcast");
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to toggle screen", t);
                    }
                } else if ("com.retroporting.castlevaniapor.SET_SCREEN".equals(intent.getAction())) {
                    int screen = intent.getIntExtra("screen", 1);
                    try {
                        VirtualControlsOverlay.nativeSetSingleScreen(screen);
                        Log.i(TAG, "Set screen to " + screen + " via broadcast");
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to set screen", t);
                    }
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(testReceiver, filter);
        }

        hideSystemUI();
    }

    private String calculateSHA1(File file) {
        if (file == null || !file.exists() || file.length() == 0) return "";
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error calculating SHA-1", e);
            return "";
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    public void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void extractAssetsIfNeeded() {
        File filesDir = getFilesDir();
        copyAssetToFile("game.toml", new File(filesDir, "game.toml"), true);
        copyAssetToFile("Castlevania Portrait of Ruin.nds", new File(filesDir, "Castlevania Portrait of Ruin.nds"), false);
    }

    private void copyAssetToFile(String assetName, File destination, boolean overwrite) {
        if (!overwrite && destination.exists() && destination.length() > 0) {
            return;
        }
        try (InputStream in = getAssets().open(assetName);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            Log.i(TAG, "Extracted asset: " + assetName + " to " + destination.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy asset: " + assetName, e);
        }
    }
}
