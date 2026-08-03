package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.VideoView;

/** Plays the branded launch video before handing control to the main dashboard. */
public class SplashActivity extends Activity
{
    private static final long MAX_SPLASH_MS = 12_000L;
    private static final int SPLASH_BACKGROUND = Color.rgb(7, 9, 11);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureFullscreenWindow();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(SPLASH_BACKGROUND);
        root.setClipChildren(true);
        root.setClipToPadding(true);

        CenterCropVideoView video = new CenterCropVideoView(this);
        video.setBackgroundColor(SPLASH_BACKGROUND);
        FrameLayout.LayoutParams videoLayout = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        root.addView(video, videoLayout);
        setContentView(root);
        hideSystemBars();

        root.setOnClickListener(view -> openDashboard());
        video.setOnPreparedListener(player -> {
            player.setLooping(false);
            player.setVolume(0f, 0f);
            video.setVideoDimensions(player.getVideoWidth(), player.getVideoHeight());
            video.setBackgroundColor(Color.TRANSPARENT);
            video.start();
        });
        video.setOnCompletionListener(player -> openDashboard());
        video.setOnErrorListener((player, what, extra) -> {
            openDashboard();
            return true;
        });

        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.splash);
        video.setVideoURI(videoUri);
        handler.postDelayed(this::openDashboard, MAX_SPLASH_MS);
    }

    private void configureFullscreenWindow()
    {
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void hideSystemBars()
    {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null)
            {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            return;
        }

        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !finished) hideSystemBars();
    }

    private void openDashboard()
    {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        Intent source = getIntent();
        Intent dashboard = new Intent(this, MainActivity.class);
        dashboard.setAction(source.getAction());
        dashboard.setDataAndType(source.getData(), source.getType());
        if (source.getExtras() != null) dashboard.putExtras(source.getExtras());
        startActivity(dashboard);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onDestroy()
    {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * Measures the video larger than its viewport when needed, preserving its
     * aspect ratio and cropping equal amounts from opposite edges.
     */
    private static final class CenterCropVideoView extends VideoView
    {
        private int videoWidth;
        private int videoHeight;

        CenterCropVideoView(Context context)
        {
            super(context);
        }

        @SuppressWarnings("unused")
        CenterCropVideoView(Context context, AttributeSet attrs)
        {
            super(context, attrs);
        }

        void setVideoDimensions(int width, int height)
        {
            videoWidth = width;
            videoHeight = height;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
        {
            int viewportWidth = MeasureSpec.getSize(widthMeasureSpec);
            int viewportHeight = MeasureSpec.getSize(heightMeasureSpec);
            if (videoWidth <= 0 || videoHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0)
            {
                setMeasuredDimension(viewportWidth, viewportHeight);
                return;
            }

            float videoAspect = (float) videoWidth / (float) videoHeight;
            float viewportAspect = (float) viewportWidth / (float) viewportHeight;
            int measuredWidth;
            int measuredHeight;
            if (videoAspect > viewportAspect)
            {
                measuredHeight = viewportHeight;
                measuredWidth = Math.round(viewportHeight * videoAspect);
            }
            else
            {
                measuredWidth = viewportWidth;
                measuredHeight = Math.round(viewportWidth / videoAspect);
            }
            setMeasuredDimension(measuredWidth, measuredHeight);
        }
    }
}
