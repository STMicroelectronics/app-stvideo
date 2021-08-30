package com.stmicroelectronics.stvideo;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.VideoView;

import timber.log.Timber;

public class MediaPlayerActivity extends AppCompatActivity {

    public static final String VIDEO_URI_EXTRA = "VideoUriExtra";
    public static final String VIDEO_ORIENTATION_EXTRA = "VideoOrientationExtra";
    public static final String VIDEO_FULLSCREEN_EXTRA = "VideoFullScreenExtra";

    private static final String TAG = MediaPlayerActivity.class.getSimpleName();
    private static MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mStateBuilder;

    VideoView mPlayerView;
    FrameLayout mHolder;

    private boolean mVideoState = false;
    private boolean mFullScreen = false;

    private MediaController mController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);

        mPlayerView = findViewById(R.id.media_player_view);
        mHolder = findViewById(R.id.holder_video);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            getWindow().setDecorFitsSystemWindows(false);
            mHolder.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
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
        } else {
            initSystemUiVisibility();
        }

        Intent intent = getIntent();
        if (intent != null) {
            String videoOrientationStr = intent.getStringExtra(VIDEO_ORIENTATION_EXTRA);
            if ((videoOrientationStr != null) && (videoOrientationStr.length() > 0)){
                if ((videoOrientationStr.equals(getString(R.string.landscape_value)))) {
                    Timber.d("SCREEN ORIENTATION LANDSCAPE");
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else if (videoOrientationStr.equals(getString(R.string.reverseLandscape_value))){
                    Timber.d("SCREEN ORIENTATION REVERSE LANDSCAPE");
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                }
            }

            mFullScreen = intent.getBooleanExtra(VIDEO_FULLSCREEN_EXTRA,false);
            Timber.d("FULL SCREEN %s", mFullScreen ? "ON" : "OFF");
            ViewGroup.LayoutParams params = mPlayerView.getLayoutParams();
            if (mFullScreen) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            mPlayerView.setLayoutParams(params);

            Uri videoUri = intent.getParcelableExtra(VIDEO_URI_EXTRA);
            if (videoUri != null) {
                Timber.d("VIDEO URI = %s", videoUri.toString());

                if (mController == null) {
                    mController = new MediaController(this);
                }

                mController.setAnchorView(mPlayerView);
                mPlayerView.setMediaController(mController);

                createMediaSession();

                mMediaSession.setActive(true);

                mPlayerView.setVideoURI(videoUri);
                mPlayerView.requestFocus();

                mPlayerView.setOnPreparedListener(mp -> {
                    Timber.d("Video Playback START");
                    mVideoState = true;
                    mPlayerView.start();
                    mStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                            mPlayerView.getCurrentPosition(), 1f);
                });

                mPlayerView.setOnCompletionListener(mp -> {
                    Timber.d("Video Playback END");
                    mVideoState = false;
                });
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void initSystemUiVisibility() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mMediaSession.isActive()) mMediaSession.setActive(false);
        if (mPlayerView != null) mPlayerView.stopPlayback();
    }

    /**
     * Initializes the Media Session to be enabled with media buttons, transport controls, callbacks
     * and media controller.
     */
    private void createMediaSession() {

        // Create a MediaSessionCompat.
        mMediaSession = new MediaSessionCompat(this, TAG);

        // Do not let MediaButtons restart the player when the app is not visible.
        mMediaSession.setMediaButtonReceiver(null);

        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player.
        mStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE);

        mMediaSession.setPlaybackState(mStateBuilder.build());

        // MySessionCallback has methods that handle callbacks from a media controller.
        mMediaSession.setCallback(new MediaPlayerActivity.MediaSessionCallback());
    }

    /**
     * Media Session Callbacks, where all external clients control the player.
     */
    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            if (mVideoState) {
                mPlayerView.start();
            }
        }

        @Override
        public void onPause() {
            if (mVideoState) {
                Timber.d("Video Playback PAUSE");
                mPlayerView.pause();
            }
        }

        @Override
        public void onSkipToPrevious() {
            if (mVideoState) {
                mPlayerView.seekTo(0);
            }
        }
    }
}
