package com.stmicroelectronics.stvideo;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatActivity;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import timber.log.Timber;

public class ExoPlayerActivity extends AppCompatActivity {

    public static final String VIDEO_URI_EXTRA = "VideoUriExtra";
    public static final String VIDEO_ORIENTATION_EXTRA = "VideoOrientationExtra";
    public static final String VIDEO_FULLSCREEN_EXTRA = "VideoFullScreenExtra";
    private ExoPlayer mPlayer;
    private boolean mFullScreen = false;
    PlayerView mPlayerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo_player);

        mPlayerView = findViewById(R.id.player_view);

        getWindow().setDecorFitsSystemWindows(false);
        mPlayerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                WindowInsetsController wic = v.getWindowInsetsController();
                wic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                wic.hide(WindowInsets.Type.statusBars());
                if (mFullScreen) {
                    wic.hide(WindowInsets.Type.navigationBars());
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                // nothing to do
            }
        });
        Intent intent = getIntent();
        if (intent != null) {
            String videoOrientationStr = intent.getStringExtra(VIDEO_ORIENTATION_EXTRA);
            if ((videoOrientationStr != null) && (videoOrientationStr.length() > 0)) {
                if (videoOrientationStr.equals(getString(R.string.landscape_value))) {
                    Timber.d("SCREEN ORIENTATION LANDSCAPE");
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else if (videoOrientationStr.equals(getString(R.string.reverseLandscape_value))) {
                    Timber.d("SCREEN ORIENTATION REVERSE LANDSCAPE");
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                }
            }

            mFullScreen = intent.getBooleanExtra(VIDEO_FULLSCREEN_EXTRA,false);
            Timber.d("FULL SCREEN %s", mFullScreen ? "ON" : "OFF");

            Uri videoUri = intent.getParcelableExtra(VIDEO_URI_EXTRA);
            if (videoUri != null) {
                Timber.d("VIDEO URI = %s", videoUri.toString());

                if (mPlayer == null) {
                    mPlayer = createPlayer();
                }

                mPlayer.addListener(new Player.Listener() {

                    @Override
                    public void onVideoSizeChanged(VideoSize videoSize) {
                        Player.Listener.super.onVideoSizeChanged(videoSize);
                        if (!mFullScreen) {
                            ViewGroup.LayoutParams p = mPlayerView.getLayoutParams();
                            if (Math.min(videoSize.width, mPlayerView.getMeasuredWidth()) == videoSize.width) {
                                p.width = videoSize.width;
                            }
                            if (Math.min(videoSize.height, mPlayerView.getMeasuredHeight()) == videoSize.height) {
                                p.height = videoSize.height;
                            }
                            mPlayerView.setLayoutParams(p);
                        }
                    }
                });

                mPlayerView.setUseController(true);
                mPlayerView.requestFocus();
                mPlayerView.setPlayer(mPlayer);

                mPlayer.setPlayWhenReady(true);
                mPlayer.setMediaItem(MediaItem.fromUri(videoUri));
                mPlayer.prepare();
            } else {
                Timber.e("No Parameter");
                mPlayerView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (mPlayer != null) {
            mPlayerView.setPlayer(null);
            mPlayer.release();
        }
        super.onDestroy();
    }

    private ExoPlayer createPlayer() {
        return new ExoPlayer.Builder(this).build();
    }
}
