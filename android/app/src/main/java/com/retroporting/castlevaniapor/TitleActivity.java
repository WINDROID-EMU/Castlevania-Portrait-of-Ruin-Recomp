package com.retroporting.castlevaniapor;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TitleActivity extends Activity {
    private static final String TAG = "TitleActivity";
    private static final int REQUEST_CODE_PICK_ROM = 1001;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1002;
    private static final String ROM_FILENAME = "Castlevania Portrait of Ruin.nds";
    public static final String EXPECTED_ROM_SHA1 = "c1fb223c706be6efd66827675eff2360a1605cdc";

    private boolean mStartingGame = false;
    private boolean mCheckingRom = true;
    private boolean mHasRom = false;

    private TextView mTxtPressStart;
    private View mRootView;
    private View mBtnSelectRom;
    private TextView mTxtSelectRom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw into camera cutout / notch area to eliminate black bars on the sides
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_title);

        mTxtPressStart = findViewById(R.id.txt_press_start);
        mRootView = findViewById(R.id.title_root);
        mBtnSelectRom = findViewById(R.id.btn_select_rom);
        mTxtSelectRom = findViewById(R.id.txt_select_rom);

        if (mBtnSelectRom != null) {
            mBtnSelectRom.setOnClickListener(v -> openRomPicker());
        }

        // Immersive full-bleed
        hideSystemUI();

        // Pulsing glow animation
        startPulsingAnimation();

        // Touch to start or select ROM - support tapping anywhere on the screen
        if (mRootView != null) {
            mRootView.setOnClickListener(v -> onScreenTapped());
        }
        View imgBg = findViewById(R.id.img_title_bg);
        if (imgBg != null) {
            imgBg.setOnClickListener(v -> onScreenTapped());
        }

        checkAndExtractAssets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mHasRom && !mCheckingRom && !mStartingGame) {
            checkAndExtractAssets();
        }
    }

    private void hideSystemUI() {
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

    private void startPulsingAnimation() {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(mTxtPressStart, "alpha", 1.0f, 0.2f);
        pulse.setDuration(850);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
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

    private void checkAndExtractAssets() {
        mCheckingRom = true;
        mTxtPressStart.setText("VERIFICANDO ROM...");

        new Thread(() -> {
            File filesDir = getFilesDir();
            // Extract game.toml if needed
            copyAssetToFile("game.toml", new File(filesDir, "game.toml"), true);

            File romFile = new File(filesDir, ROM_FILENAME);
            String sha1 = calculateSHA1(romFile);

            if (!EXPECTED_ROM_SHA1.equalsIgnoreCase(sha1)) {
                // Try extracting from assets if bundled
                copyAssetToFile(ROM_FILENAME, romFile, true);
                sha1 = calculateSHA1(romFile);
            }

            boolean valid = EXPECTED_ROM_SHA1.equalsIgnoreCase(sha1);
            if (!valid && romFile.exists()) {
                // Don't keep corrupt or wrong-version ROM
                romFile.delete();
            }

            final boolean hasRom = valid;
            runOnUiThread(() -> {
                mCheckingRom = false;
                mHasRom = hasRom;
                if (mHasRom) {
                    mTxtPressStart.setText("TOQUE NA TELA PARA INICIAR");
                    if (mBtnSelectRom != null) {
                        mBtnSelectRom.setVisibility(View.VISIBLE);
                    }
                    if (mTxtSelectRom != null) {
                        mTxtSelectRom.setText("TROCAR ROM");
                    }
                } else {
                    mTxtPressStart.setText("SELECIONE A ROM (.NDS)");
                    if (mBtnSelectRom != null) {
                        mBtnSelectRom.setVisibility(View.VISIBLE);
                    }
                    if (mTxtSelectRom != null) {
                        mTxtSelectRom.setText("SELECIONAR ROM");
                    }
                }
            });
        }).start();
    }

    private void openRomPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_ROM);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch document picker", e);
            Toast.makeText(this, "Não foi possível abrir o seletor de arquivos.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_ROM && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importRomFromUri(uri);
            }
        }
    }

    private void importRomFromUri(Uri uri) {
        mCheckingRom = true;
        mTxtPressStart.setText("IMPORTANDO E VALIDANDO ROM...");
        if (mBtnSelectRom != null) {
            mBtnSelectRom.setEnabled(false);
        }

        new Thread(() -> {
            File filesDir = getFilesDir();
            File tempRom = new File(filesDir, "temp_rom.nds");
            File targetRom = new File(filesDir, ROM_FILENAME);

            boolean copied = false;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tempRom)) {
                if (in != null) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                    copied = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error importing ROM from URI", e);
            }

            if (!copied || !tempRom.exists() || tempRom.length() == 0) {
                if (tempRom.exists()) tempRom.delete();
                runOnUiThread(() -> {
                    mCheckingRom = false;
                    if (mBtnSelectRom != null) mBtnSelectRom.setEnabled(true);
                    mTxtPressStart.setText("ERRO AO IMPORTAR ARQUIVO");
                    Toast.makeText(TitleActivity.this, "Falha ao ler o arquivo selecionado.", Toast.LENGTH_LONG).show();
                });
                return;
            }

            String sha1 = calculateSHA1(tempRom);
            boolean valid = EXPECTED_ROM_SHA1.equalsIgnoreCase(sha1);

            if (valid) {
                if (targetRom.exists()) targetRom.delete();
                tempRom.renameTo(targetRom);
            } else {
                tempRom.delete();
            }

            runOnUiThread(() -> {
                mCheckingRom = false;
                if (mBtnSelectRom != null) mBtnSelectRom.setEnabled(true);
                if (valid) {
                    mHasRom = true;
                    mTxtPressStart.setText("ROM VALIDADA! TOQUE NA TELA PARA INICIAR");
                    if (mTxtSelectRom != null) {
                        mTxtSelectRom.setText("TROCAR ROM");
                    }
                    Toast.makeText(TitleActivity.this, "ROM validada com sucesso!", Toast.LENGTH_SHORT).show();
                    // Do NOT auto-launch; game only starts when the user taps the screen
                } else {
                    mTxtPressStart.setText("ROM INVÁLIDA! SELECIONE A ROM CORRETA");
                    Toast.makeText(TitleActivity.this,
                        "SHA-1 incompatível!\nEsperado: " + EXPECTED_ROM_SHA1.substring(0, 10) + "...\nEncontrado: " + (sha1.length() > 10 ? sha1.substring(0, 10) : sha1),
                        Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void onScreenTapped() {
        if (mCheckingRom || mStartingGame) return;

        if (mHasRom) {
            launchGame();
        } else {
            openRomPicker();
        }
    }

    private void vibrateFeedback() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
        } catch (Exception ignored) {}
    }

    private void launchGame() {
        if (mStartingGame || !mHasRom) return;
        mStartingGame = true;

        vibrateFeedback();

        if (mBtnSelectRom != null) {
            mBtnSelectRom.animate().alpha(0f).setDuration(180).start();
        }

        // Quick flash on prompt
        mTxtPressStart.setTextColor(0xFFFFD700);
        mTxtPressStart.setAlpha(1.0f);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(TitleActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 200);
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
