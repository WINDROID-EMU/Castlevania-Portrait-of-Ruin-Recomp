package com.retroporting.castlevaniapor;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsDialog {
    private static final String PREFS_NAME = "castlevania_settings";

    public static void show(final Activity activity, final VirtualControlsOverlay overlay) {
        show(activity, overlay, false);
    }

    public static void show(final Activity activity, final VirtualControlsOverlay overlay, boolean startInControlsTab) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final float density = activity.getResources().getDisplayMetrics().density;

        // Container Card
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * density);
        root.setPadding(pad, pad, pad, pad);

        // Dark glass background
        GradientDrawable bgCard = new GradientDrawable();
        bgCard.setColor(Color.parseColor("#151b27"));
        bgCard.setCornerRadius(20 * density);
        bgCard.setStroke((int) (1.5f * density), Color.parseColor("#2a384e"));
        root.setBackground(bgCard);

        // Header Row: Title & Close
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText("⚙ CONFIGURAÇÕES");
        title.setTextColor(Color.parseColor("#ffffff"));
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, titleLp);

        TextView btnClose = new TextView(activity);
        btnClose.setText("✕");
        btnClose.setTextColor(Color.parseColor("#8ea2c0"));
        btnClose.setTextSize(20);
        btnClose.setPadding((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        header.addView(btnClose);

        root.addView(header);


        // Tabs Header Row
        LinearLayout tabsRow = new LinearLayout(activity);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setGravity(Gravity.CENTER);
        tabsRow.setPadding(0, (int) (4 * density), 0, (int) (8 * density));

        final Button tabVideo = new Button(activity);
        tabVideo.setText("🖥️ VÍDEO");
        tabVideo.setTextSize(12);
        tabVideo.setTypeface(Typeface.DEFAULT_BOLD);

        final Button tabControls = new Button(activity);
        tabControls.setText("🕹️ CONTROLES");
        tabControls.setTextSize(12);
        tabControls.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f);
        tabLp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
        tabsRow.addView(tabVideo, tabLp);
        tabsRow.addView(tabControls, tabLp);
        root.addView(tabsRow);

        // Content Area with ScrollView
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);

        final LinearLayout contentVideo = new LinearLayout(activity);
        contentVideo.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout contentControls = new LinearLayout(activity);
        contentControls.setOrientation(LinearLayout.VERTICAL);

        FrameLayout contentFrame = new FrameLayout(activity);
        contentFrame.addView(contentVideo);
        contentFrame.addView(contentControls);
        scrollView.addView(contentFrame);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (int) (230 * density));
        root.addView(scrollView, scrollLp);

        // Styling helpers for tabs
        final Runnable updateTabs = () -> {
            boolean isVideo = contentVideo.getVisibility() == View.VISIBLE;

            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setColor(Color.parseColor("#1e293b"));
            activeBg.setCornerRadius(8 * density);
            activeBg.setStroke((int) (1.5f * density), Color.parseColor("#38bdf8"));

            GradientDrawable inactiveBg = new GradientDrawable();
            inactiveBg.setColor(Color.parseColor("#0e1522"));
            inactiveBg.setCornerRadius(8 * density);
            inactiveBg.setStroke((int) (1.0f * density), Color.parseColor("#1e293b"));

            if (isVideo) {
                tabVideo.setBackground(activeBg);
                tabVideo.setTextColor(Color.parseColor("#38bdf8"));
                tabControls.setBackground(inactiveBg);
                tabControls.setTextColor(Color.parseColor("#94a3b8"));
            } else {
                tabVideo.setBackground(inactiveBg);
                tabVideo.setTextColor(Color.parseColor("#94a3b8"));
                tabControls.setBackground(activeBg);
                tabControls.setTextColor(Color.parseColor("#38bdf8"));
            }
        };

        tabVideo.setOnClickListener(v -> {
            contentVideo.setVisibility(View.VISIBLE);
            contentControls.setVisibility(View.GONE);
            updateTabs.run();
        });

        tabControls.setOnClickListener(v -> {
            contentVideo.setVisibility(View.GONE);
            contentControls.setVisibility(View.VISIBLE);
            updateTabs.run();
        });

        // ==================== ABA VÍDEO ====================
        // 1. Aspect Ratio (Esticar / Manter Proporção)
        addSectionHeader(contentVideo, "PROPORÇÃO DA TELA (ESTIRAR / AJUSTAR)", density);
        int curAspect = prefs.getInt("video_aspect_mode", 0);
        RadioGroup rgAspect = new RadioGroup(activity);
        rgAspect.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbAspectFit = createRadioButton(activity, "4:3 Original", density);
        RadioButton rbAspectStretch = createRadioButton(activity, "Esticar Tela Cheia", density);
        RadioButton rbAspectCrop = createRadioButton(activity, "Zoom (Preencher)", density);
        rgAspect.addView(rbAspectFit);
        rgAspect.addView(rbAspectStretch);
        rgAspect.addView(rbAspectCrop);
        if (curAspect == 1) rbAspectStretch.setChecked(true);
        else if (curAspect == 2) rbAspectCrop.setChecked(true);
        else rbAspectFit.setChecked(true);

        rgAspect.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = 0;
            if (checkedId == rbAspectStretch.getId()) mode = 1;
            else if (checkedId == rbAspectCrop.getId()) mode = 2;
            prefs.edit().putInt("video_aspect_mode", mode).apply();
            overlay.setAspectMode(mode);
        });
        contentVideo.addView(rgAspect);

        // 2. Resolução Interna 3D
        addSectionHeader(contentVideo, "RESOLUÇÃO INTERNA 3D DO EMULADOR", density);
        int curRes = prefs.getInt("video_internal_resolution", 1);
        RadioGroup rgRes = new RadioGroup(activity);
        rgRes.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbRes1 = createRadioButton(activity, "1x (256x192)", density);
        RadioButton rbRes2 = createRadioButton(activity, "2x HD (512x384)", density);
        RadioButton rbRes3 = createRadioButton(activity, "3x FHD", density);
        RadioButton rbRes4 = createRadioButton(activity, "4x UHD", density);
        rgRes.addView(rbRes1);
        rgRes.addView(rbRes2);
        rgRes.addView(rbRes3);
        rgRes.addView(rbRes4);
        if (curRes == 2) rbRes2.setChecked(true);
        else if (curRes == 3) rbRes3.setChecked(true);
        else if (curRes == 4) rbRes4.setChecked(true);
        else rbRes1.setChecked(true);

        rgRes.setOnCheckedChangeListener((group, checkedId) -> {
            int scale = 1;
            if (checkedId == rbRes2.getId()) scale = 2;
            else if (checkedId == rbRes3.getId()) scale = 3;
            else if (checkedId == rbRes4.getId()) scale = 4;
            prefs.edit().putInt("video_internal_resolution", scale).apply();
            overlay.setInternalResolution(scale);
        });
        contentVideo.addView(rgRes);

        // 3. Filtros de Vídeo
        addSectionHeader(contentVideo, "FILTROS DE VÍDEO", density);
        int curFilter = prefs.getInt("video_filter", 0);
        RadioGroup rgFilter = new RadioGroup(activity);
        rgFilter.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbFilterNone = createRadioButton(activity, "Nítido (Pixel Art)", density);
        RadioButton rbFilterLinear = createRadioButton(activity, "Suave (Bilinear)", density);
        RadioButton rbFilterScanlines = createRadioButton(activity, "Scanlines CRT", density);
        RadioButton rbFilterLcd = createRadioButton(activity, "Grade LCD NDS", density);
        rgFilter.addView(rbFilterNone);
        rgFilter.addView(rbFilterLinear);
        rgFilter.addView(rbFilterScanlines);
        rgFilter.addView(rbFilterLcd);
        if (curFilter == 1) rbFilterLinear.setChecked(true);
        else if (curFilter == 2) rbFilterScanlines.setChecked(true);
        else if (curFilter == 3) rbFilterLcd.setChecked(true);
        else rbFilterNone.setChecked(true);

        rgFilter.setOnCheckedChangeListener((group, checkedId) -> {
            int filter = 0;
            if (checkedId == rbFilterLinear.getId()) filter = 1;
            else if (checkedId == rbFilterScanlines.getId()) filter = 2;
            else if (checkedId == rbFilterLcd.getId()) filter = 3;
            prefs.edit().putInt("video_filter", filter).apply();
            overlay.setVideoFilter(filter);
        });
        contentVideo.addView(rgFilter);

        // ==================== ABA CONTROLES ====================
        // 1. Opacidade
        int curOpacity = prefs.getInt("controls_opacity", 100);
        final TextView tvOpacity = addSectionHeader(contentControls, "OPACIDADE DOS BOTÕES: " + curOpacity + "%", density);
        SeekBar sbOpacity = new SeekBar(activity);
        sbOpacity.setMax(80); // 20% to 100%
        sbOpacity.setProgress(curOpacity - 20);
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + 20;
                tvOpacity.setText("OPACIDADE DOS BOTÕES: " + val + "%");
                prefs.edit().putInt("controls_opacity", val).apply();
                overlay.setControlsOpacity(val / 100.0f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        contentControls.addView(sbOpacity);

        // Section: Editar Posição e Tamanho dos Botões
        addSectionHeader(contentControls, "POSIÇÃO E TAMANHO DOS BOTÕES", density);

        TextView tvInfo = new TextView(activity);
        tvInfo.setText("Mova e ajuste o tamanho de cada botão ou do analógico de forma totalmente individual, com grade magnética para alinhamento perfeito.");
        tvInfo.setTextColor(Color.parseColor("#94a3b8"));
        tvInfo.setTextSize(12);
        tvInfo.setPadding(0, (int) (4 * density), 0, (int) (12 * density));
        contentControls.addView(tvInfo);

        Button btnEditControlsTab = new Button(activity);
        btnEditControlsTab.setText("✏️ EDITAR POSIÇÃO DOS BOTÕES");
        btnEditControlsTab.setTextSize(13);
        btnEditControlsTab.setTypeface(Typeface.DEFAULT_BOLD);
        btnEditControlsTab.setTextColor(Color.WHITE);

        GradientDrawable btnTabBg = new GradientDrawable();
        btnTabBg.setColor(Color.parseColor("#0284c7"));
        btnTabBg.setCornerRadius(10 * density);
        btnTabBg.setStroke((int) (1.5f * density), Color.parseColor("#38bdf8"));
        btnEditControlsTab.setBackground(btnTabBg);
        btnEditControlsTab.setPadding(0, (int) (12 * density), 0, (int) (12 * density));
        btnEditControlsTab.setOnClickListener(v -> {
            dialog.dismiss();
            overlay.startEditMode();
        });
        contentControls.addView(btnEditControlsTab);

        // Initial tab state
        if (startInControlsTab) {
            contentVideo.setVisibility(View.GONE);
            contentControls.setVisibility(View.VISIBLE);
        } else {
            contentVideo.setVisibility(View.VISIBLE);
            contentControls.setVisibility(View.GONE);
        }
        updateTabs.run();

        dialog.setContentView(root);

        // Window presentation
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.width = (int) (520 * density);
            wlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            wlp.gravity = Gravity.CENTER;
            window.setAttributes(wlp);

            // Immersive fullscreen flags for the dialog window
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        dialog.setOnDismissListener(d -> {
            // Restore immersive mode in main window
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).hideSystemUI();
            }
        });

        dialog.show();
    }

    private static TextView addSectionHeader(LinearLayout parent, String text, float density) {
        TextView tv = new TextView(parent.getContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#93c5fd"));
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, (int) (10 * density), 0, (int) (4 * density));
        parent.addView(tv);
        return tv;
    }

    private static RadioButton createRadioButton(Context context, String text, float density) {
        RadioButton rb = new RadioButton(context);
        rb.setText(text);
        rb.setTextColor(Color.parseColor("#e2e8f0"));
        rb.setTextSize(12);
        rb.setPadding((int) (4 * density), (int) (4 * density), (int) (12 * density), (int) (4 * density));
        return rb;
    }
}
