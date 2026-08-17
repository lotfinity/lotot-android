package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;

/** Plays the branded launch video before handing control to the main dashboard. */
public class SplashActivity extends Activity
{
    private static final String TAG = "LoToTiSplash";
    private static final long MAX_SPLASH_MS = 12_000L;
    private static final int SPLASH_BACKGROUND = Color.rgb(7, 9, 11);

    /**
     * Same intro at several resolutions. Selection considers both the physical
     * screen and the AVC decoder, because some low/mid-range phones cannot
     * decode a tall 1080x2400 H.264 stream even when their screen is portrait.
     */
    private static final SplashVariant[] SPLASH_VARIANTS = {
            new SplashVariant(R.raw.splash_1080x2400, 1080, 2400, 900),
            new SplashVariant(R.raw.splash_720x1600, 720, 1600, 600),
            new SplashVariant(R.raw.splash_486x1080, 486, 1080, 420),
            new SplashVariant(R.raw.splash_360x800, 360, 800, 0)
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished;
    private CenterCropVideoView video;
    private List<SplashVariant> splashQueue;
    private int splashQueueIndex = -1;

    @Override
    protected void attachBaseContext(Context newBase)
    {
        super.attachBaseContext(SettingsActivity.wrapLocale(newBase));
    }

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

        video = new CenterCropVideoView(this);
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
            SplashVariant failed = currentVariant();
            Log.w(TAG, "Splash decoder failed for " + describe(failed)
                    + " what=" + what + " extra=" + extra + "; trying lower tier");
            video.setBackgroundColor(SPLASH_BACKGROUND);
            video.post(() -> {
                if (!playNextSplash()) openDashboard();
            });
            return true;
        });

        splashQueue = buildSplashQueue();
        if (!playNextSplash()) openDashboard();
        handler.postDelayed(this::openDashboard, MAX_SPLASH_MS);
    }

    private List<SplashVariant> buildSplashQueue()
    {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int shortEdgePx = Math.min(metrics.widthPixels, metrics.heightPixels);
        List<SplashVariant> compatible = new ArrayList<>();
        List<SplashVariant> screenEligible = new ArrayList<>();

        for (SplashVariant variant : SPLASH_VARIANTS)
        {
            if (shortEdgePx < variant.minimumScreenWidthPx) continue;
            screenEligible.add(variant);
            if (canDecodeAvcSize(variant.width, variant.height)) compatible.add(variant);
        }

        // Most phones report decoder limits correctly. If a vendor reports no
        // usable decoder at all, fall back to trying the screen-appropriate
        // tiers from largest to smallest and let MediaPlayer decide.
        if (compatible.isEmpty()) compatible.addAll(screenEligible);
        if (compatible.isEmpty()) compatible.add(SPLASH_VARIANTS[SPLASH_VARIANTS.length - 1]);
        return compatible;
    }

    private boolean playNextSplash()
    {
        if (finished || splashQueue == null) return false;
        splashQueueIndex++;
        if (splashQueueIndex >= splashQueue.size()) return false;

        SplashVariant variant = splashQueue.get(splashQueueIndex);
        Log.i(TAG, "Playing splash " + describe(variant));
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + variant.resourceId);
        video.setVideoURI(videoUri);
        return true;
    }

    private SplashVariant currentVariant()
    {
        if (splashQueue == null || splashQueueIndex < 0 || splashQueueIndex >= splashQueue.size())
            return null;
        return splashQueue.get(splashQueueIndex);
    }

    private static String describe(SplashVariant variant)
    {
        return variant == null ? "unknown" : variant.width + "x" + variant.height;
    }

    private static boolean canDecodeAvcSize(int width, int height)
    {
        try
        {
            MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
            for (MediaCodecInfo codec : codecs)
            {
                if (codec.isEncoder()) continue;
                for (String type : codec.getSupportedTypes())
                {
                    if (!"video/avc".equalsIgnoreCase(type)) continue;
                    try
                    {
                        MediaCodecInfo.VideoCapabilities caps =
                                codec.getCapabilitiesForType(type).getVideoCapabilities();
                        // Some vendor codecs publish landscape limits plus a
                        // width/height-swap capability, so check both axes.
                        if (caps != null && (caps.isSizeSupported(width, height)
                                || caps.isSizeSupported(height, width)))
                        {
                            return true;
                        }
                    }
                    catch (RuntimeException ignored)
                    {
                        // Try the next decoder.
                    }
                }
            }
        }
        catch (RuntimeException error)
        {
            Log.w(TAG, "Unable to query AVC decoder capabilities", error);
        }
        return false;
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

    private static final class SplashVariant
    {
        final int resourceId;
        final int width;
        final int height;
        final int minimumScreenWidthPx;

        SplashVariant(int resourceId, int width, int height, int minimumScreenWidthPx)
        {
            this.resourceId = resourceId;
            this.width = width;
            this.height = height;
            this.minimumScreenWidthPx = minimumScreenWidthPx;
        }
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
