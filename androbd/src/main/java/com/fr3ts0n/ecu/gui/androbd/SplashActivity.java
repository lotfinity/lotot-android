package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.VideoView;

/** Plays the branded launch video before handing control to the main dashboard. */
public class SplashActivity extends Activity {
    private static final long MAX_SPLASH_MS = 12_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 9, 11));

        VideoView video = new VideoView(this);
        video.setBackgroundColor(Color.rgb(7, 9, 11));
        root.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        root.setOnClickListener(view -> openDashboard());
        video.setOnPreparedListener(player -> {
            player.setLooping(false);
            player.setVolume(0f, 0f);
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

    private void openDashboard() {
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
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
