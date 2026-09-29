package com.retroporting.castlevaniapor;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import org.libsdl.app.SDLActivity;

public class VirtualControlsOverlay extends View {
    private static final String TAG = "VirtualControls";
    private static final String PREFS_NAME = "castlevania_settings";

    // Assets
    private Bitmap mBmpAttackMan;
    private Bitmap mBmpAttackGirl;
    private Bitmap mBmpJumpMan;
    private Bitmap mBmpJumpGirl;
    private Bitmap mBmpBackdashMan;
    private Bitmap mBmpBackdashGirl;
    private Bitmap mBmpAMan;
    private Bitmap mBmpAGirl;
    private Bitmap mBmpStatus;
    private Bitmap mBmpStart;
    private Bitmap mBmpMenu;
    private Bitmap mBmpSwitch;

    // Paints
    private Paint mBitmapPaint;
    private Paint mJoyBasePaint;
    private Paint mJoyRingPaint;
    private Paint mJoyKnobPaint;
    private Paint mJoyAccentPaint;
    private Paint mGlowPaint;

    // Edit Mode Paints
    private Paint mGridPaint;
    private Paint mEditBoxPaint;
    private Paint mEditTextPaint;
    private Paint mToolbarBgPaint;
    private Paint mToolbarBtnPaint;
    private Paint mToolbarTextPaint;
    private Paint mCrosshairPaint;

    // Game & Character state
    private boolean mIsCharlotte = false;
    private boolean mCanSwitch = false;
    private boolean mInGame = false;
    private int mCurScreen = 1; // 0 = top (map/stats), 1 = bottom (main gameplay & menus)

    // Customization & Settings
    private float mButtonOpacity = 1.0f;
    private float mButtonScale = 1.0f;
    private boolean mIsEditMode = false;
    private boolean mSnapToGrid = true;
    private float mGridSize;

    // Dragging state
    private ActionButton mDraggingButton = null;
    private boolean mDraggingJoy = false;
    private int mEditPointerId = -1;
    private float mDragOffsetX = 0.0f;
    private float mDragOffsetY = 0.0f;

    // Selection state in Edit Mode
    private ActionButton mSelectedButton = null;
    private boolean mSelectedJoy = false;

    // Toolbar rects in Edit Mode
    private final RectF mToolbarRect = new RectF();
    private final RectF mToolbarSnapRect = new RectF();
    private final RectF mToolbarScaleDownRect = new RectF();
    private final RectF mToolbarScaleUpRect = new RectF();
    private final RectF mToolbarResetRect = new RectF();
    private final RectF mToolbarDoneRect = new RectF();

    // Native JNI calls to inspect game state and toggle single screen directly from runner RAM
    public static native int nativeGetPlayerState();
    public static native int nativeGetSubscreenMode();
    public static native void nativeSetSingleScreen(int screen);
    public static native void nativeToggleMapScreen();
    public static native void nativeSetScreenAspectMode(int mode);
    public static native void nativeSetVideoFilter(int filter);
    public static native void nativeSetInternalResolution(int scale);

    private final Runnable mStatePoller = new Runnable() {
        @Override
        public void run() {
            updatePlayerState();
            postDelayed(this, 40); // 25 fps state polling
        }
    };

    private void updatePlayerState() {
        try {
            int state = nativeGetPlayerState();
            if (state >= 0) {
                boolean charlotte = (state & 1) != 0;
                boolean canSwitch = (state & 2) != 0;
                boolean inGame = (state & 4) != 0;
                int curScreen = ((state & 8) != 0) ? 1 : 0;
                boolean needInvalidate = false;
                if (mIsCharlotte != charlotte) {
                    mIsCharlotte = charlotte;
                    mSwitchAnimStartTime = System.currentTimeMillis();
                    needInvalidate = true;
                }
                if (mCanSwitch != canSwitch) {
                    mCanSwitch = canSwitch;
                    needInvalidate = true;
                }
                if (!mInGame && inGame) {
                    nativeSetSingleScreen(1);
                    curScreen = 1;
                }
                if (mInGame != inGame) {
                    mInGame = inGame;
                    needInvalidate = true;
                }
                if (mCurScreen != curScreen) {
                    mCurScreen = curScreen;
                    needInvalidate = true;
                }
                if (needInvalidate) {
                    invalidate();
                }
            }
        } catch (UnsatisfiedLinkError | Exception ignored) {}
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(mStatePoller);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mStatePoller);
    }

    // Switch pulse animation
    private long mSwitchAnimStartTime = 0;
    private static final long SWITCH_ANIM_DURATION = 220; // ms

    // Haptics
    private Vibrator mVibrator;

    // Layout metrics (density scaled)
    private float mDensity = 1.0f;

    // Dynamic Joystick State
    private int mJoyPointerId = -1;
    private boolean mJoyActive = false;
    private float mJoyBaseX, mJoyBaseY;
    private float mJoyStickX, mJoyStickY;
    private float mJoyDefaultBaseX, mJoyDefaultBaseY;
    private float mJoyMaxRadius;
    private float mJoyDeadzone;
    private float mJoyKnobRadius;
    private float mBaseJoyMaxRadius;
    private float mBaseJoyDeadzone;
    private float mBaseJoyKnobRadius;
    private float mJoyScale = 1.0f;

    public void setJoyScale(float scale) {
        mJoyScale = Math.max(0.5f, Math.min(scale, 2.2f));
        mJoyMaxRadius = mBaseJoyMaxRadius * mJoyScale;
        mJoyKnobRadius = mBaseJoyKnobRadius * mJoyScale;
        mJoyDeadzone = mBaseJoyDeadzone * mJoyScale;
    }

    // Active D-Pad key states
    private boolean mDpadUp = false;
    private boolean mDpadDown = false;
    private boolean mDpadLeft = false;
    private boolean mDpadRight = false;

    // Action Buttons
    public static class ActionButton {
        public String label;
        public float baseRadius;
        public float basePadding;
        public float centerX;
        public float centerY;
        public float radius;
        public float touchRadius;
        public float scale = 1.0f;
        public int keyCode;
        public int pointerId = -1;
        public boolean pressed = false;
        public RectF drawRect = new RectF();

        public ActionButton(String label, int keyCode, float radiusDp, float touchPaddingDp, float density) {
            this.label = label;
            this.keyCode = keyCode;
            this.baseRadius = radiusDp * density;
            this.basePadding = touchPaddingDp * density;
            setScale(1.0f);
        }

        public void setScale(float s) {
            this.scale = Math.max(0.5f, Math.min(s, 2.2f));
            this.radius = this.baseRadius * this.scale;
            this.touchRadius = (this.baseRadius + this.basePadding) * this.scale;
            this.drawRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        }

        public void applyScale(float scale) {
            setScale(scale);
        }

        public void updatePosition(float cx, float cy) {
            this.centerX = cx;
            this.centerY = cy;
            this.drawRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        }

        public boolean hitTest(float x, float y) {
            float dx = x - centerX;
            float dy = y - centerY;
            return (dx * dx + dy * dy) <= (touchRadius * touchRadius);
        }
    }

    private ActionButton mBtnAttack;
    private ActionButton mBtnJump;
    private ActionButton mBtnA;
    private ActionButton mBtnBackdash;
    private ActionButton mBtnSwitch;
    private ActionButton mBtnStatus;
    private ActionButton mBtnStart;
    private ActionButton mBtnMenu;

    public VirtualControlsOverlay(Context context) {
        super(context);
        init(context);
    }

    public VirtualControlsOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VirtualControlsOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mGridSize = 24.0f * mDensity;

        try {
            mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignored) {}

        // Load Bitmaps
        mBmpAttackMan = BitmapFactory.decodeResource(getResources(), R.drawable.btn_attack_man);
        mBmpAttackGirl = BitmapFactory.decodeResource(getResources(), R.drawable.btn_attack_girl);
        mBmpJumpMan = BitmapFactory.decodeResource(getResources(), R.drawable.btn_jump_man);
        mBmpJumpGirl = BitmapFactory.decodeResource(getResources(), R.drawable.btn_jump_girl);
        mBmpBackdashMan = BitmapFactory.decodeResource(getResources(), R.drawable.btn_backdash_man);
        mBmpBackdashGirl = BitmapFactory.decodeResource(getResources(), R.drawable.btn_backdash_girl);
        mBmpAMan = BitmapFactory.decodeResource(getResources(), R.drawable.btn_a_man);
        mBmpAGirl = BitmapFactory.decodeResource(getResources(), R.drawable.btn_a_girl);
        mBmpStatus = BitmapFactory.decodeResource(getResources(), R.drawable.btn_status);
        mBmpStart = BitmapFactory.decodeResource(getResources(), R.drawable.btn_start);
        mBmpMenu = BitmapFactory.decodeResource(getResources(), R.drawable.btn_menu);
        mBmpSwitch = BitmapFactory.decodeResource(getResources(), R.drawable.btn_switch);

        // Paints setup
        mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        mJoyBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mJoyBasePaint.setStyle(Paint.Style.FILL);
        mJoyBasePaint.setColor(Color.argb(85, 10, 15, 25));

        mJoyRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mJoyRingPaint.setStyle(Paint.Style.STROKE);
        mJoyRingPaint.setStrokeWidth(2.5f * mDensity);
        mJoyRingPaint.setColor(Color.argb(140, 110, 200, 255));

        mJoyKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mJoyKnobPaint.setStyle(Paint.Style.FILL);

        mJoyAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mJoyAccentPaint.setStyle(Paint.Style.FILL);
        mJoyAccentPaint.setColor(Color.argb(200, 140, 220, 255));

        mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGlowPaint.setStyle(Paint.Style.STROKE);
        mGlowPaint.setStrokeWidth(3.0f * mDensity);

        // Edit Mode Paints
        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setColor(Color.argb(32, 56, 189, 248));
        mGridPaint.setStrokeWidth(1.0f);

        mEditBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditBoxPaint.setStyle(Paint.Style.STROKE);
        mEditBoxPaint.setStrokeWidth(2.0f * mDensity);
        mEditBoxPaint.setColor(Color.argb(200, 56, 189, 248));

        mEditTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditTextPaint.setColor(Color.WHITE);
        mEditTextPaint.setTextSize(10.5f * mDensity);
        mEditTextPaint.setTextAlign(Paint.Align.CENTER);
        mEditTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mEditTextPaint.setShadowLayer(3.0f * mDensity, 0, 0, Color.BLACK);

        mToolbarBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mToolbarBgPaint.setColor(Color.argb(235, 15, 23, 42));
        mToolbarBgPaint.setStyle(Paint.Style.FILL);

        mToolbarBtnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mToolbarBtnPaint.setStyle(Paint.Style.FILL);

        mToolbarTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mToolbarTextPaint.setTextAlign(Paint.Align.CENTER);
        mToolbarTextPaint.setTextSize(11.5f * mDensity);
        mToolbarTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        mCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCrosshairPaint.setColor(Color.argb(90, 56, 189, 248));
        mCrosshairPaint.setStrokeWidth(1.2f * mDensity);

        // Joystick base dimensions
        mBaseJoyMaxRadius = 66.0f * mDensity;
        mBaseJoyDeadzone = 16.0f * mDensity;
        mBaseJoyKnobRadius = 28.0f * mDensity;
        setJoyScale(1.0f);

        // Button initialization with ergonomic sizes
        mBtnAttack = new ActionButton("ATAQUE", KeyEvent.KEYCODE_A, 39.0f, 16.0f, mDensity);
        mBtnJump = new ActionButton("PULO", KeyEvent.KEYCODE_X, 39.0f, 16.0f, mDensity);
        mBtnA = new ActionButton("AÇÃO A", KeyEvent.KEYCODE_Z, 39.0f, 16.0f, mDensity);
        mBtnBackdash = new ActionButton("ESQUIVA L", KeyEvent.KEYCODE_Q, 35.0f, 14.0f, mDensity);
        mBtnSwitch = new ActionButton("TROCAR", KeyEvent.KEYCODE_S, 34.0f, 14.0f, mDensity);
        mBtnStatus = new ActionButton("STATUS", 0, 27.0f, 12.0f, mDensity);
        mBtnStart = new ActionButton("START", KeyEvent.KEYCODE_ENTER, 30.0f, 12.0f, mDensity);
        mBtnMenu = new ActionButton("MENU", 0, 30.0f, 12.0f, mDensity);

        // Load saved preferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mButtonOpacity = prefs.getInt("controls_opacity", 100) / 100.0f;
        mButtonScale = 1.0f;
        mSnapToGrid = prefs.getBoolean("controls_snap_grid", true);
        int aspect = prefs.getInt("video_aspect_mode", 0);
        int res = prefs.getInt("video_internal_resolution", 1);
        int filter = prefs.getInt("video_filter", 0);

        try {
            nativeSetScreenAspectMode(aspect);
            nativeSetInternalResolution(res);
            nativeSetVideoFilter(filter);
        } catch (UnsatisfiedLinkError | Exception ignored) {}
    }

    public void setControlsOpacity(float opacity) {
        mButtonOpacity = Math.max(0.2f, Math.min(opacity, 1.0f));
        invalidate();
    }

    public void setAspectMode(int mode) {
        try {
            nativeSetScreenAspectMode(mode);
        } catch (UnsatisfiedLinkError | Exception ignored) {}
    }

    public void setInternalResolution(int res) {
        try {
            nativeSetInternalResolution(res);
        } catch (UnsatisfiedLinkError | Exception ignored) {}
    }

    public void setVideoFilter(int filter) {
        try {
            nativeSetVideoFilter(filter);
        } catch (UnsatisfiedLinkError | Exception ignored) {}
    }

    public void startEditMode() {
        mIsEditMode = true;
        if (mSelectedButton == null && !mSelectedJoy) {
            mSelectedButton = mBtnAttack;
        }
        invalidate();
    }

    public void exitEditMode(boolean save) {
        if (save) {
            saveControlPositions();
        }
        mIsEditMode = false;
        mDraggingButton = null;
        mDraggingJoy = false;
        mEditPointerId = -1;
        invalidate();
    }

    private void applyScaleToButtons() {
        mBtnAttack.setScale(mBtnAttack.scale);
        mBtnJump.setScale(mBtnJump.scale);
        mBtnA.setScale(mBtnA.scale);
        mBtnBackdash.setScale(mBtnBackdash.scale);
        mBtnSwitch.setScale(mBtnSwitch.scale);
        mBtnStatus.setScale(mBtnStatus.scale);
        mBtnStart.setScale(mBtnStart.scale);
        mBtnMenu.setScale(mBtnMenu.scale);
        setJoyScale(mJoyScale);
    }

    public void saveControlPositions() {
        SharedPreferences.Editor editor = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putFloat("pos_joy_x", mJoyDefaultBaseX);
        editor.putFloat("pos_joy_y", mJoyDefaultBaseY);
        editor.putFloat("pos_jump_x", mBtnJump.centerX);
        editor.putFloat("pos_jump_y", mBtnJump.centerY);
        editor.putFloat("pos_attack_x", mBtnAttack.centerX);
        editor.putFloat("pos_attack_y", mBtnAttack.centerY);
        editor.putFloat("pos_a_x", mBtnA.centerX);
        editor.putFloat("pos_a_y", mBtnA.centerY);
        editor.putFloat("pos_switch_x", mBtnSwitch.centerX);
        editor.putFloat("pos_switch_y", mBtnSwitch.centerY);
        editor.putFloat("pos_backdash_x", mBtnBackdash.centerX);
        editor.putFloat("pos_backdash_y", mBtnBackdash.centerY);
        editor.putFloat("pos_status_x", mBtnStatus.centerX);
        editor.putFloat("pos_status_y", mBtnStatus.centerY);
        editor.putFloat("pos_menu_x", mBtnMenu.centerX);
        editor.putFloat("pos_menu_y", mBtnMenu.centerY);
        editor.putFloat("pos_start_x", mBtnStart.centerX);
        editor.putFloat("pos_start_y", mBtnStart.centerY);

        editor.putFloat("scale_joy", mJoyScale);
        editor.putFloat("scale_attack", mBtnAttack.scale);
        editor.putFloat("scale_jump", mBtnJump.scale);
        editor.putFloat("scale_a", mBtnA.scale);
        editor.putFloat("scale_switch", mBtnSwitch.scale);
        editor.putFloat("scale_backdash", mBtnBackdash.scale);
        editor.putFloat("scale_status", mBtnStatus.scale);
        editor.putFloat("scale_start", mBtnStart.scale);
        editor.putFloat("scale_menu", mBtnMenu.scale);

        editor.putBoolean("controls_snap_grid", mSnapToGrid);
        editor.apply();
    }

    public void resetControlPositions() {
        SharedPreferences.Editor editor = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.remove("pos_joy_x");
        editor.remove("pos_joy_y");
        editor.remove("pos_jump_x");
        editor.remove("pos_jump_y");
        editor.remove("pos_attack_x");
        editor.remove("pos_attack_y");
        editor.remove("pos_a_x");
        editor.remove("pos_a_y");
        editor.remove("pos_switch_x");
        editor.remove("pos_switch_y");
        editor.remove("pos_backdash_x");
        editor.remove("pos_backdash_y");
        editor.remove("pos_map_x");
        editor.remove("pos_map_y");
        editor.remove("pos_status_x");
        editor.remove("pos_status_y");
        editor.remove("pos_menu_x");
        editor.remove("pos_menu_y");
        editor.remove("pos_start_x");
        editor.remove("pos_start_y");

        editor.remove("scale_joy");
        editor.remove("scale_attack");
        editor.remove("scale_jump");
        editor.remove("scale_a");
        editor.remove("scale_switch");
        editor.remove("scale_backdash");
        editor.remove("scale_status");
        editor.remove("scale_map");
        editor.remove("scale_start");
        editor.remove("scale_menu");
        editor.remove("controls_scale");
        editor.apply();

        setJoyScale(1.0f);
        mBtnAttack.setScale(1.0f);
        mBtnJump.setScale(1.0f);
        mBtnA.setScale(1.0f);
        mBtnSwitch.setScale(1.0f);
        mBtnBackdash.setScale(1.0f);
        mBtnStatus.setScale(1.0f);
        mBtnStart.setScale(1.0f);
        mBtnMenu.setScale(1.0f);

        recalculatePositions(getWidth(), getHeight());
        invalidate();
    }

    private void recalculatePositions(int w, int h) {
        if (w <= 0 || h <= 0) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        float rightEdgeMargin = 22.0f * mDensity;
        float bottomEdgeMargin = 22.0f * mDensity;

        // Load individual scales
        setJoyScale(prefs.getFloat("scale_joy", 1.0f));
        mBtnAttack.setScale(prefs.getFloat("scale_attack", 1.0f));
        mBtnJump.setScale(prefs.getFloat("scale_jump", 1.0f));
        mBtnA.setScale(prefs.getFloat("scale_a", 1.0f));
        mBtnSwitch.setScale(prefs.getFloat("scale_switch", 1.0f));
        mBtnBackdash.setScale(prefs.getFloat("scale_backdash", 1.0f));
        mBtnStatus.setScale(prefs.getFloat("scale_status", 1.0f));
        mBtnStart.setScale(prefs.getFloat("scale_start", 1.0f));
        mBtnMenu.setScale(prefs.getFloat("scale_menu", 1.0f));

        // Default resting position for dynamic joystick (bottom left)
        float defJoyX = 100.0f * mDensity;
        float defJoyY = h - 95.0f * mDensity;
        mJoyDefaultBaseX = prefs.getFloat("pos_joy_x", defJoyX);
        mJoyDefaultBaseY = prefs.getFloat("pos_joy_y", defJoyY);
        if (!mJoyActive) {
            mJoyBaseX = mJoyDefaultBaseX;
            mJoyBaseY = mJoyDefaultBaseY;
            mJoyStickX = mJoyBaseX;
            mJoyStickY = mJoyBaseY;
        }

        // Jump (B)
        float defJumpX = w - rightEdgeMargin - 95.0f * mDensity;
        float defJumpY = h - bottomEdgeMargin - 45.0f * mDensity;
        mBtnJump.updatePosition(prefs.getFloat("pos_jump_x", defJumpX), prefs.getFloat("pos_jump_y", defJumpY));

        // Attack (Y)
        float defAttackX = defJumpX - 66.0f * mDensity;
        float defAttackY = defJumpY - 32.0f * mDensity;
        mBtnAttack.updatePosition(prefs.getFloat("pos_attack_x", defAttackX), prefs.getFloat("pos_attack_y", defAttackY));

        // Button A (A)
        float defAX = defJumpX + 64.0f * mDensity;
        float defAY = defJumpY - 32.0f * mDensity;
        mBtnA.updatePosition(prefs.getFloat("pos_a_x", defAX), prefs.getFloat("pos_a_y", defAY));

        // Switch (X)
        float defSwitchX = defJumpX;
        float defSwitchY = defJumpY - 76.0f * mDensity;
        mBtnSwitch.updatePosition(prefs.getFloat("pos_switch_x", defSwitchX), prefs.getFloat("pos_switch_y", defSwitchY));

        // Backdash (L)
        float defBackdashX = defAX;
        float defBackdashY = defAY - 74.0f * mDensity;
        mBtnBackdash.updatePosition(prefs.getFloat("pos_backdash_x", defBackdashX), prefs.getFloat("pos_backdash_y", defBackdashY));

        // Status
        float defStatusX = w - rightEdgeMargin - 42.0f * mDensity;
        float defStatusY = 42.0f * mDensity;
        mBtnStatus.updatePosition(prefs.getFloat("pos_status_x", defStatusX), prefs.getFloat("pos_status_y", defStatusY));

        // Center bottom: Menu on left, Start on right
        float centerBottomX = w * 0.5f;
        float centerBottomY = h - 30.0f * mDensity;
        float centerSpacing = 42.0f * mDensity;
        mBtnMenu.updatePosition(prefs.getFloat("pos_menu_x", centerBottomX - centerSpacing), prefs.getFloat("pos_menu_y", centerBottomY));
        mBtnStart.updatePosition(prefs.getFloat("pos_start_x", centerBottomX + centerSpacing), prefs.getFloat("pos_start_y", centerBottomY));

        applyScaleToButtons();

        // Calculate edit toolbar rects (5 buttons: Snap, Scale-, Scale+, Reset, Done)
        float tbWidth = Math.min(w * 0.85f, 480.0f * mDensity);
        float tbHeight = 42.0f * mDensity;
        float tbX = (w - tbWidth) * 0.5f;
        float tbY = 12.0f * mDensity;
        mToolbarRect.set(tbX, tbY, tbX + tbWidth, tbY + tbHeight);

        float pad = 4.0f * mDensity;
        float innerW = tbWidth - pad * 6.0f;
        float btnW = innerW / 5.0f;
        float bY1 = tbY + pad;
        float bY2 = tbY + tbHeight - pad;

        float curX = tbX + pad;
        mToolbarSnapRect.set(curX, bY1, curX + btnW, bY2); curX += btnW + pad;
        mToolbarScaleDownRect.set(curX, bY1, curX + btnW, bY2); curX += btnW + pad;
        mToolbarScaleUpRect.set(curX, bY1, curX + btnW, bY2); curX += btnW + pad;
        mToolbarResetRect.set(curX, bY1, curX + btnW, bY2); curX += btnW + pad;
        mToolbarDoneRect.set(curX, bY1, curX + btnW, bY2);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalculatePositions(w, h);
    }

    private void performHaptic() {
        if (mVibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mVibrator.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                mVibrator.vibrate(14);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsEditMode) {
            return handleEditTouch(event);
        }

        if (!mInGame) {
            // At the beginning, do NOT intercept touches; allow touchscreen interaction with intro/menus
            return false;
        }

        int actionMasked = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);
        float x = event.getX(actionIndex);
        float y = event.getY(actionIndex);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // Check action buttons first
                if (checkButtonPress(mBtnAttack, pointerId, x, y)) return true;
                if (checkButtonPress(mBtnJump, pointerId, x, y)) return true;
                if (checkButtonPress(mBtnA, pointerId, x, y)) return true;
                if (checkButtonPress(mBtnBackdash, pointerId, x, y)) return true;
                if (mCanSwitch && checkSwitchButtonPress(pointerId, x, y)) return true;
                if (checkStatusButtonPress(pointerId, x, y)) return true;
                if (checkButtonPress(mBtnStart, pointerId, x, y)) return true;
                if (checkMenuButtonPress(pointerId, x, y)) return true;

                // If on the left ~45% of the screen, engage dynamic joystick
                if (x < getWidth() * 0.45f && mJoyPointerId == -1) {
                    mJoyPointerId = pointerId;
                    mJoyActive = true;
                    // Position base at touch, clamped safely within screen bounds
                    mJoyBaseX = Math.max(mJoyMaxRadius + 10.0f * mDensity, Math.min(x, getWidth() * 0.45f - mJoyMaxRadius));
                    mJoyBaseY = Math.max(mJoyMaxRadius + 10.0f * mDensity, Math.min(y, getHeight() - mJoyMaxRadius - 10.0f * mDensity));
                    mJoyStickX = mJoyBaseX;
                    mJoyStickY = mJoyBaseY;
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                boolean handled = false;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pId = event.getPointerId(i);
                    float px = event.getX(i);
                    float py = event.getY(i);

                    if (pId == mJoyPointerId) {
                        updateJoystick(px, py);
                        handled = true;
                    } else {
                        updateButtonHeldState(mBtnAttack, pId, px, py);
                        updateButtonHeldState(mBtnJump, pId, px, py);
                        updateButtonHeldState(mBtnA, pId, px, py);
                        updateButtonHeldState(mBtnBackdash, pId, px, py);
                        updateButtonHeldState(mBtnSwitch, pId, px, py);
                        updateButtonHeldState(mBtnStatus, pId, px, py);
                        updateButtonHeldState(mBtnMenu, pId, px, py);
                        updateButtonHeldState(mBtnStart, pId, px, py);
                    }
                }
                if (handled) {
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean handled = false;
                if (pointerId == mJoyPointerId) {
                    releaseJoystick();
                    handled = true;
                }
                handled |= releaseButtonIfOwned(mBtnAttack, pointerId);
                handled |= releaseButtonIfOwned(mBtnJump, pointerId);
                handled |= releaseButtonIfOwned(mBtnA, pointerId);
                handled |= releaseButtonIfOwned(mBtnBackdash, pointerId);
                handled |= releaseButtonIfOwned(mBtnSwitch, pointerId);
                handled |= releaseButtonIfOwned(mBtnStatus, pointerId);
                handled |= releaseButtonIfOwned(mBtnStart, pointerId);
                handled |= releaseButtonIfOwned(mBtnMenu, pointerId);

                if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
                    if (mJoyPointerId != -1) releaseJoystick();
                    releaseButton(mBtnAttack);
                    releaseButton(mBtnJump);
                    releaseButton(mBtnA);
                    releaseButton(mBtnBackdash);
                    releaseButton(mBtnSwitch);
                    releaseButton(mBtnStatus);
                    releaseButton(mBtnStart);
                    releaseButton(mBtnMenu);
                    handled = true;
                }

                if (handled) {
                    invalidate();
                    return true;
                }
                break;
            }
        }

        return super.onTouchEvent(event);
    }

    private boolean handleEditTouch(MotionEvent event) {
        int actionMasked = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);
        float x = event.getX(actionIndex);
        float y = event.getY(actionIndex);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // Check toolbar buttons
                if (mToolbarSnapRect.contains(x, y)) {
                    mSnapToGrid = !mSnapToGrid;
                    performHaptic();
                    invalidate();
                    return true;
                }
                if (mToolbarScaleDownRect.contains(x, y)) {
                    if (mSelectedJoy) {
                        setJoyScale(mJoyScale - 0.05f);
                    } else if (mSelectedButton != null) {
                        mSelectedButton.setScale(mSelectedButton.scale - 0.05f);
                    }
                    performHaptic();
                    invalidate();
                    return true;
                }
                if (mToolbarScaleUpRect.contains(x, y)) {
                    if (mSelectedJoy) {
                        setJoyScale(mJoyScale + 0.05f);
                    } else if (mSelectedButton != null) {
                        mSelectedButton.setScale(mSelectedButton.scale + 0.05f);
                    }
                    performHaptic();
                    invalidate();
                    return true;
                }
                if (mToolbarResetRect.contains(x, y)) {
                    resetControlPositions();
                    performHaptic();
                    return true;
                }
                if (mToolbarDoneRect.contains(x, y)) {
                    exitEditMode(true);
                    performHaptic();
                    return true;
                }

                // Check buttons to drag (test all 8 action buttons)
                ActionButton[] buttons = new ActionButton[] {
                    mBtnAttack, mBtnJump, mBtnA, mBtnBackdash, mBtnSwitch,
                    mBtnStatus, mBtnStart, mBtnMenu
                };
                for (ActionButton btn : buttons) {
                    if (btn.hitTest(x, y)) {
                        mDraggingButton = btn;
                        mSelectedButton = btn;
                        mDraggingJoy = false;
                        mSelectedJoy = false;
                        mEditPointerId = pointerId;
                        mDragOffsetX = btn.centerX - x;
                        mDragOffsetY = btn.centerY - y;
                        performHaptic();
                        invalidate();
                        return true;
                    }
                }

                // Check joystick base
                float djx = x - mJoyDefaultBaseX;
                float djy = y - mJoyDefaultBaseY;
                if (djx * djx + djy * djy <= mJoyMaxRadius * mJoyMaxRadius * 1.5f) {
                    mDraggingJoy = true;
                    mSelectedJoy = true;
                    mDraggingButton = null;
                    mSelectedButton = null;
                    mEditPointerId = pointerId;
                    mDragOffsetX = mJoyDefaultBaseX - x;
                    mDragOffsetY = mJoyDefaultBaseY - y;
                    performHaptic();
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mEditPointerId != -1) {
                    int pIdx = event.findPointerIndex(mEditPointerId);
                    if (pIdx >= 0) {
                        float px = event.getX(pIdx) + mDragOffsetX;
                        float py = event.getY(pIdx) + mDragOffsetY;

                        if (mSnapToGrid) {
                            px = Math.round(px / mGridSize) * mGridSize;
                            py = Math.round(py / mGridSize) * mGridSize;
                        }

                        if (mDraggingJoy) {
                            px = Math.max(mJoyMaxRadius, Math.min(px, getWidth() - mJoyMaxRadius));
                            py = Math.max(mJoyMaxRadius, Math.min(py, getHeight() - mJoyMaxRadius));
                            mJoyDefaultBaseX = px;
                            mJoyDefaultBaseY = py;
                            mJoyBaseX = px;
                            mJoyBaseY = py;
                            mJoyStickX = px;
                            mJoyStickY = py;
                            invalidate();
                            return true;
                        } else if (mDraggingButton != null) {
                            px = Math.max(mDraggingButton.radius, Math.min(px, getWidth() - mDraggingButton.radius));
                            py = Math.max(mDraggingButton.radius, Math.min(py, getHeight() - mDraggingButton.radius));
                            mDraggingButton.updatePosition(px, py);
                            invalidate();
                            return true;
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (pointerId == mEditPointerId) {
                    mDraggingButton = null;
                    mDraggingJoy = false;
                    mEditPointerId = -1;
                    invalidate();
                    return true;
                }
                break;
            }
        }
        return true;
    }

    private boolean checkButtonPress(ActionButton btn, int pointerId, float x, float y) {
        if (btn.hitTest(x, y)) {
            btn.pointerId = pointerId;
            btn.pressed = true;
            if (btn.keyCode != 0) {
                SDLActivity.onNativeKeyDown(btn.keyCode);
            }
            performHaptic();
            invalidate();
            return true;
        }
        return false;
    }

    private boolean checkMenuButtonPress(int pointerId, float x, float y) {
        if (mBtnMenu.hitTest(x, y)) {
            mBtnMenu.pointerId = pointerId;
            mBtnMenu.pressed = true;
            performHaptic();
            invalidate();
            post(() -> {
                if (getContext() instanceof Activity) {
                    SettingsDialog.show((Activity) getContext(), VirtualControlsOverlay.this);
                }
            });
            return true;
        }
        return false;
    }

    private boolean checkSwitchButtonPress(int pointerId, float x, float y) {
        if (!mCanSwitch) return false;
        if (mBtnSwitch.hitTest(x, y)) {
            mBtnSwitch.pointerId = pointerId;
            mBtnSwitch.pressed = true;
            SDLActivity.onNativeKeyDown(mBtnSwitch.keyCode);
            performHaptic();
            invalidate();
            return true;
        }
        return false;
    }

    private void pulseSelectKey() {
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_DEL);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DEL);
            }
        }, 80);
    }

    private boolean checkStatusButtonPress(int pointerId, float x, float y) {
        if (mBtnStatus.hitTest(x, y)) {
            mBtnStatus.pointerId = pointerId;
            mBtnStatus.pressed = true;
            performHaptic();
            try {
                if (mCurScreen == 1) {
                    nativeSetSingleScreen(0);
                    mCurScreen = 0;
                    if (mInGame) {
                        int subMode = nativeGetSubscreenMode();
                        if (subMode != 1) {
                            pulseSelectKey();
                        }
                    }
                } else {
                    nativeSetSingleScreen(1);
                    mCurScreen = 1;
                    if (mInGame) {
                        int subMode = nativeGetSubscreenMode();
                        if (subMode != 1) {
                            pulseSelectKey();
                        }
                    }
                }
            } catch (UnsatisfiedLinkError | Exception ignored) {}
            invalidate();
            return true;
        }
        return false;
    }

    private void updateButtonHeldState(ActionButton btn, int pointerId, float x, float y) {
        if (btn.pointerId == pointerId && btn.pressed) {
            float dx = x - btn.centerX;
            float dy = y - btn.centerY;
            float maxDist = btn.touchRadius * 1.5f;
            if (dx * dx + dy * dy > maxDist * maxDist) {
                releaseButton(btn);
            }
        }
    }

    private boolean releaseButtonIfOwned(ActionButton btn, int pointerId) {
        if (btn.pointerId == pointerId && btn.pressed) {
            releaseButton(btn);
            return true;
        }
        return false;
    }

    private void releaseButton(ActionButton btn) {
        if (btn.pressed) {
            btn.pressed = false;
            btn.pointerId = -1;
            if (btn.keyCode != 0) {
                SDLActivity.onNativeKeyUp(btn.keyCode);
            }
        }
    }

    private void updateJoystick(float x, float y) {
        float dx = x - mJoyBaseX;
        float dy = y - mJoyBaseY;
        float dist = (float) Math.hypot(dx, dy);

        if (dist > mJoyMaxRadius) {
            float scale = mJoyMaxRadius / dist;
            mJoyStickX = mJoyBaseX + dx * scale;
            mJoyStickY = mJoyBaseY + dy * scale;
        } else {
            mJoyStickX = x;
            mJoyStickY = y;
        }

        boolean wantUp = false;
        boolean wantDown = false;
        boolean wantLeft = false;
        boolean wantRight = false;

        if (dist > mJoyDeadzone) {
            float normX = dx / dist;
            float normY = dy / dist;

            if (normY < -0.38f) wantUp = true;
            if (normY > 0.38f) wantDown = true;
            if (normX < -0.38f) wantLeft = true;
            if (normX > 0.38f) wantRight = true;
        }

        if (wantUp != mDpadUp) {
            mDpadUp = wantUp;
            if (mDpadUp) SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_DPAD_UP);
            else SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DPAD_UP);
        }
        if (wantDown != mDpadDown) {
            mDpadDown = wantDown;
            if (mDpadDown) SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_DPAD_DOWN);
            else SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        if (wantLeft != mDpadLeft) {
            mDpadLeft = wantLeft;
            if (mDpadLeft) SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_DPAD_LEFT);
            else SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DPAD_LEFT);
        }
        if (wantRight != mDpadRight) {
            mDpadRight = wantRight;
            if (mDpadRight) SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT);
            else SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT);
        }
    }

    private void releaseJoystick() {
        mJoyActive = false;
        mJoyPointerId = -1;
        mJoyBaseX = mJoyDefaultBaseX;
        mJoyBaseY = mJoyDefaultBaseY;
        mJoyStickX = mJoyBaseX;
        mJoyStickY = mJoyBaseY;

        if (mDpadUp) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DPAD_UP); mDpadUp = false; }
        if (mDpadDown) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DPAD_DOWN); mDpadDown = false; }
        if (mDpadLeft) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DPAD_LEFT); mDpadLeft = false; }
        if (mDpadRight) { SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT); mDpadRight = false; }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mInGame && !mIsEditMode) {
            return;
        }

        // In edit mode, draw background grid
        if (mIsEditMode) {
            drawEditGrid(canvas);
        }

        // 1. Draw Dynamic Joystick
        drawJoystick(canvas);

        // 2. Draw Action Buttons
        drawButtons(canvas);

        // In edit mode, draw overlays & toolbar on top
        if (mIsEditMode) {
            drawEditOverlays(canvas);
            drawEditToolbar(canvas);
        }
    }

    private void drawEditGrid(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // Draw vertical grid lines
        for (float x = 0; x <= w; x += mGridSize) {
            canvas.drawLine(x, 0, x, h, mGridPaint);
        }

        // Draw horizontal grid lines
        for (float y = 0; y <= h; y += mGridSize) {
            canvas.drawLine(0, y, w, y, mGridPaint);
        }
    }

    private void drawEditOverlays(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // Crosshairs for currently dragged element
        float crossX = -1;
        float crossY = -1;
        if (mDraggingJoy) {
            crossX = mJoyDefaultBaseX;
            crossY = mJoyDefaultBaseY;
        } else if (mDraggingButton != null) {
            crossX = mDraggingButton.centerX;
            crossY = mDraggingButton.centerY;
        }
        if (crossX >= 0 && crossY >= 0) {
            canvas.drawLine(0, crossY, w, crossY, mCrosshairPaint);
            canvas.drawLine(crossX, 0, crossX, h, mCrosshairPaint);
        }

        ActionButton[] buttons = new ActionButton[] {
            mBtnAttack, mBtnJump, mBtnA, mBtnBackdash, mBtnSwitch,
            mBtnStatus, mBtnStart, mBtnMenu
        };

        for (ActionButton btn : buttons) {
            boolean isSelected = (btn == mSelectedButton);
            boolean isDragging = (btn == mDraggingButton);

            if (isSelected) {
                mEditBoxPaint.setColor(isDragging ? Color.parseColor("#f59e0b") : Color.parseColor("#fbbf24"));
                mEditBoxPaint.setStrokeWidth(2.5f * mDensity);
            } else {
                mEditBoxPaint.setColor(Color.argb(175, 56, 189, 248));
                mEditBoxPaint.setStrokeWidth(1.5f * mDensity);
            }

            canvas.drawRoundRect(btn.drawRect, 8.0f * mDensity, 8.0f * mDensity, mEditBoxPaint);

            String label = isSelected ? (btn.label + " (" + Math.round(btn.scale * 100) + "%)") : btn.label;
            float pillW = mEditTextPaint.measureText(label) + 12.0f * mDensity;
            float pillH = 16.0f * mDensity;
            float pillX = btn.centerX - pillW * 0.5f;
            float pillY = btn.drawRect.top - pillH - 4.0f * mDensity;

            mToolbarBgPaint.setColor(isSelected ? Color.argb(240, 30, 41, 59) : Color.argb(200, 15, 23, 42));
            canvas.drawRoundRect(new RectF(pillX, pillY, pillX + pillW, pillY + pillH), 4.0f * mDensity, 4.0f * mDensity, mToolbarBgPaint);
            if (isSelected) {
                mEditBoxPaint.setStrokeWidth(1.0f * mDensity);
                mEditBoxPaint.setColor(Color.parseColor("#fbbf24"));
                canvas.drawRoundRect(new RectF(pillX, pillY, pillX + pillW, pillY + pillH), 4.0f * mDensity, 4.0f * mDensity, mEditBoxPaint);
            }
            canvas.drawText(label, btn.centerX, pillY + pillH - 4.0f * mDensity, mEditTextPaint);
        }

        // Joystick overlay
        boolean isJoySelected = mSelectedJoy;
        boolean isJoyDragging = mDraggingJoy;
        if (isJoySelected) {
            mEditBoxPaint.setColor(isJoyDragging ? Color.parseColor("#f59e0b") : Color.parseColor("#fbbf24"));
            mEditBoxPaint.setStrokeWidth(2.5f * mDensity);
        } else {
            mEditBoxPaint.setColor(Color.argb(175, 56, 189, 248));
            mEditBoxPaint.setStrokeWidth(1.5f * mDensity);
        }
        canvas.drawCircle(mJoyDefaultBaseX, mJoyDefaultBaseY, mJoyMaxRadius, mEditBoxPaint);

        String jlabel = isJoySelected ? ("ANALÓGICO (" + Math.round(mJoyScale * 100) + "%)") : "ANALÓGICO";
        float jPillW = mEditTextPaint.measureText(jlabel) + 12.0f * mDensity;
        float jPillH = 16.0f * mDensity;
        float jPillX = mJoyDefaultBaseX - jPillW * 0.5f;
        float jPillY = mJoyDefaultBaseY - mJoyMaxRadius - jPillH - 4.0f * mDensity;
        mToolbarBgPaint.setColor(isJoySelected ? Color.argb(240, 30, 41, 59) : Color.argb(200, 15, 23, 42));
        canvas.drawRoundRect(new RectF(jPillX, jPillY, jPillX + jPillW, jPillY + jPillH), 4.0f * mDensity, 4.0f * mDensity, mToolbarBgPaint);
        if (isJoySelected) {
            mEditBoxPaint.setStrokeWidth(1.0f * mDensity);
            mEditBoxPaint.setColor(Color.parseColor("#fbbf24"));
            canvas.drawRoundRect(new RectF(jPillX, jPillY, jPillX + jPillW, jPillY + jPillH), 4.0f * mDensity, 4.0f * mDensity, mEditBoxPaint);
        }
        canvas.drawText(jlabel, mJoyDefaultBaseX, jPillY + jPillH - 4.0f * mDensity, mEditTextPaint);
    }

    private void drawEditToolbar(Canvas canvas) {
        mToolbarBgPaint.setColor(Color.argb(235, 15, 23, 42));
        canvas.drawRoundRect(mToolbarRect, 12.0f * mDensity, 12.0f * mDensity, mToolbarBgPaint);

        mEditBoxPaint.setColor(Color.parseColor("#38bdf8"));
        mEditBoxPaint.setStrokeWidth(1.5f * mDensity);
        canvas.drawRoundRect(mToolbarRect, 12.0f * mDensity, 12.0f * mDensity, mEditBoxPaint);

        // 1. Snap Grid button
        mToolbarBtnPaint.setColor(mSnapToGrid ? Color.parseColor("#0284c7") : Color.parseColor("#334155"));
        canvas.drawRoundRect(mToolbarSnapRect, 6.0f * mDensity, 6.0f * mDensity, mToolbarBtnPaint);
        mToolbarTextPaint.setColor(Color.WHITE);
        canvas.drawText(mSnapToGrid ? "🧲 Grade ON" : "🧲 Grade OFF", mToolbarSnapRect.centerX(), mToolbarSnapRect.centerY() + 4.0f * mDensity, mToolbarTextPaint);

        // 2. Scale -
        mToolbarBtnPaint.setColor(Color.parseColor("#1e293b"));
        canvas.drawRoundRect(mToolbarScaleDownRect, 6.0f * mDensity, 6.0f * mDensity, mToolbarBtnPaint);
        canvas.drawText("➖ Escala", mToolbarScaleDownRect.centerX(), mToolbarScaleDownRect.centerY() + 4.0f * mDensity, mToolbarTextPaint);

        // 3. Scale +
        canvas.drawRoundRect(mToolbarScaleUpRect, 6.0f * mDensity, 6.0f * mDensity, mToolbarBtnPaint);
        canvas.drawText("➕ Escala", mToolbarScaleUpRect.centerX(), mToolbarScaleUpRect.centerY() + 4.0f * mDensity, mToolbarTextPaint);

        // 4. Reset button
        mToolbarBtnPaint.setColor(Color.parseColor("#334155"));
        canvas.drawRoundRect(mToolbarResetRect, 6.0f * mDensity, 6.0f * mDensity, mToolbarBtnPaint);
        canvas.drawText("↺ Padrão", mToolbarResetRect.centerX(), mToolbarResetRect.centerY() + 4.0f * mDensity, mToolbarTextPaint);

        // 5. Done button
        mToolbarBtnPaint.setColor(Color.parseColor("#16a34a"));
        canvas.drawRoundRect(mToolbarDoneRect, 6.0f * mDensity, 6.0f * mDensity, mToolbarBtnPaint);
        canvas.drawText("✓ Salvar", mToolbarDoneRect.centerX(), mToolbarDoneRect.centerY() + 4.0f * mDensity, mToolbarTextPaint);

        // Selected element badge beneath toolbar
        String selInfo;
        if (mSelectedJoy) {
            selInfo = "🎯 Selecionado: ANALÓGICO (" + Math.round(mJoyScale * 100) + "%) — Use [➕] ou [➖] para dimensionar";
        } else if (mSelectedButton != null) {
            selInfo = "🎯 Selecionado: " + mSelectedButton.label + " (" + Math.round(mSelectedButton.scale * 100) + "%) — Use [➕] ou [➖] para dimensionar";
        } else {
            selInfo = "👆 Toque em qualquer botão ou no analógico para selecioná-lo";
        }

        float selW = mEditTextPaint.measureText(selInfo) + 16.0f * mDensity;
        float selH = 20.0f * mDensity;
        float selX = mToolbarRect.centerX() - selW * 0.5f;
        float selY = mToolbarRect.bottom + 4.0f * mDensity;

        mToolbarBgPaint.setColor(Color.argb(230, 15, 23, 42));
        canvas.drawRoundRect(new RectF(selX, selY, selX + selW, selY + selH), 6.0f * mDensity, 6.0f * mDensity, mToolbarBgPaint);
        mEditBoxPaint.setColor(Color.parseColor("#38bdf8"));
        mEditBoxPaint.setStrokeWidth(1.0f * mDensity);
        canvas.drawRoundRect(new RectF(selX, selY, selX + selW, selY + selH), 6.0f * mDensity, 6.0f * mDensity, mEditBoxPaint);
        canvas.drawText(selInfo, mToolbarRect.centerX(), selY + selH - 5.5f * mDensity, mEditTextPaint);
    }

    private void drawJoystick(Canvas canvas) {
        float alphaFactor = (mJoyActive ? 1.0f : 0.42f) * mButtonOpacity;

        mJoyBasePaint.setAlpha((int) (90 * alphaFactor));
        canvas.drawCircle(mJoyBaseX, mJoyBaseY, mJoyMaxRadius, mJoyBasePaint);

        mJoyRingPaint.setAlpha((int) (180 * alphaFactor));
        canvas.drawCircle(mJoyBaseX, mJoyBaseY, mJoyMaxRadius, mJoyRingPaint);

        float tickLen = 7.0f * mDensity;
        mJoyRingPaint.setStrokeWidth(2.0f * mDensity);
        canvas.drawLine(mJoyBaseX, mJoyBaseY - mJoyMaxRadius, mJoyBaseX, mJoyBaseY - mJoyMaxRadius + tickLen, mJoyRingPaint);
        canvas.drawLine(mJoyBaseX, mJoyBaseY + mJoyMaxRadius, mJoyBaseX, mJoyBaseY + mJoyMaxRadius - tickLen, mJoyRingPaint);
        canvas.drawLine(mJoyBaseX - mJoyMaxRadius, mJoyBaseY, mJoyBaseX - mJoyMaxRadius + tickLen, mJoyBaseY, mJoyRingPaint);
        canvas.drawLine(mJoyBaseX + mJoyMaxRadius, mJoyBaseY, mJoyBaseX + mJoyMaxRadius - tickLen, mJoyBaseY, mJoyRingPaint);

        RadialGradient knobShader = new RadialGradient(
            mJoyStickX - mJoyKnobRadius * 0.25f,
            mJoyStickY - mJoyKnobRadius * 0.25f,
            mJoyKnobRadius,
            new int[] {
                Color.argb((int) (220 * alphaFactor), 80, 160, 240),
                Color.argb((int) (240 * alphaFactor), 25, 45, 80),
                Color.argb((int) (255 * alphaFactor), 15, 25, 45)
            },
            new float[] { 0.0f, 0.7f, 1.0f },
            Shader.TileMode.CLAMP
        );
        mJoyKnobPaint.setShader(knobShader);
        canvas.drawCircle(mJoyStickX, mJoyStickY, mJoyKnobRadius, mJoyKnobPaint);

        mJoyRingPaint.setStrokeWidth(1.8f * mDensity);
        mJoyRingPaint.setAlpha((int) (220 * alphaFactor));
        canvas.drawCircle(mJoyStickX, mJoyStickY, mJoyKnobRadius, mJoyRingPaint);

        mJoyAccentPaint.setAlpha((int) (240 * alphaFactor));
        canvas.drawCircle(mJoyStickX, mJoyStickY, 4.0f * mDensity, mJoyAccentPaint);
    }

    private void drawButtons(Canvas canvas) {
        float switchPulseScale = 1.0f;
        long elapsed = System.currentTimeMillis() - mSwitchAnimStartTime;
        if (elapsed < SWITCH_ANIM_DURATION) {
            float progress = (float) elapsed / SWITCH_ANIM_DURATION;
            switchPulseScale = 1.0f + 0.15f * (float) Math.sin(progress * Math.PI);
            invalidate();
        }

        Bitmap bmpAttack = mIsCharlotte ? mBmpAttackGirl : mBmpAttackMan;
        Bitmap bmpJump = mIsCharlotte ? mBmpJumpGirl : mBmpJumpMan;
        Bitmap bmpA = mIsCharlotte ? mBmpAGirl : mBmpAMan;
        Bitmap bmpBackdash = mIsCharlotte ? mBmpBackdashGirl : mBmpBackdashMan;

        int charGlowColor = mIsCharlotte ? Color.rgb(230, 80, 200) : Color.rgb(75, 170, 255);

        drawSingleButton(canvas, mBtnAttack, bmpAttack, switchPulseScale, charGlowColor, true);
        drawSingleButton(canvas, mBtnJump, bmpJump, switchPulseScale, charGlowColor, true);
        drawSingleButton(canvas, mBtnA, bmpA, switchPulseScale, charGlowColor, true);
        drawSingleButton(canvas, mBtnBackdash, bmpBackdash, switchPulseScale, charGlowColor, true);
        if (mCanSwitch || mIsEditMode) {
            drawSingleButton(canvas, mBtnSwitch, mBmpSwitch, 1.0f, 0, false);
        }
        drawSingleButton(canvas, mBtnStatus, mBmpStatus, 1.0f, 0, false);
        drawSingleButton(canvas, mBtnMenu, mBmpMenu, 1.0f, 0, false);
        drawSingleButton(canvas, mBtnStart, mBmpStart, 1.0f, 0, false);
    }

    private void drawSingleButton(Canvas canvas, ActionButton btn, Bitmap bmp, float extraScale, int glowColor, boolean showGlow) {
        if (bmp == null) return;

        canvas.save();

        float scale = extraScale;
        if (btn.pressed) {
            scale *= 0.90f;
        }
        canvas.scale(scale, scale, btn.centerX, btn.centerY);

        if (showGlow && btn.pressed) {
            mGlowPaint.setColor(glowColor);
            mGlowPaint.setAlpha(200);
            canvas.drawCircle(btn.centerX, btn.centerY, btn.radius + 5.0f * mDensity, mGlowPaint);
        }

        int alpha = (int) ((btn.pressed ? 255 : 224) * mButtonOpacity);
        mBitmapPaint.setAlpha(alpha);
        canvas.drawBitmap(bmp, null, btn.drawRect, mBitmapPaint);

        canvas.restore();
    }
}
