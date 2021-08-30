package com.stmicroelectronics.stvideo;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;

import timber.log.Timber;

public class ExoPlayerActivity extends AppCompatActivity {

    public static final String VIDEO_URI_EXTRA = "VideoUriExtra";
    public static final String VIDEO_ORIENTATION_EXTRA = "VideoOrientationExtra";
    public static final String VIDEO_FULLSCREEN_EXTRA = "VideoFullScreenExtra";

    private static final String TAG = ExoPlayerActivity.class.getSimpleName();
    private static MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mStateBuilder;
    private String mUserAgent;

    private SimpleExoPlayer mPlayer;
    private final boolean mPlayWhenReady = true;
    private boolean mVideoState = false;
    private boolean mFullScreen = false;

    PlayerView mPlayerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo_player);

        mPlayerView = findViewById(R.id.player_view);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
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
        } else {
            initSystemUiVisibility();
        }
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
                    mPlayer = initPlayer();
                }

                mPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        switch (playbackState) {
                            case Player.STATE_ENDED:
                                Timber.d("Video Playback END");
                                mVideoState = false;
                                break;
                            case Player.STATE_READY:
                                if (mPlayWhenReady) {
                                    Timber.d("Video Playback START/RESUME");
                                    mStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                                            mPlayer.getCurrentPosition(), 1f);
                                } else {
                                    Timber.d("Video Playback PAUSE");
                                    mStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                                            mPlayer.getCurrentPosition(), 1f);
                                }
                                mMediaSession.setPlaybackState(mStateBuilder.build());
                            case Player.STATE_BUFFERING:
                            case Player.STATE_IDLE:
                                break;
                        }
                    }

                    @Override
                    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
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

                createMediaSession();

                mUserAgent = Util.getUserAgent(this, "stmicroelectronics.stvideo");

                mVideoState = true;
                mMediaSession.setActive(true);

                mPlayer.setPlayWhenReady(mPlayWhenReady);
                mPlayer.setMediaSource(buildMediaSource(videoUri));
                mPlayer.prepare();
            } else {
                Timber.e("No Parameter");
                if (mMediaSession != null) {
                    mMediaSession.setActive(false);
                }
                mPlayerView.setVisibility(View.GONE);
                mVideoState = false;
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
        if (mMediaSession.isActive()) {
            mMediaSession.setActive(false);
        }

        if (mPlayer != null) {
            mPlayerView.setPlayer(null);
            mPlayer.release();
        }
        super.onDestroy();
    }

    private SimpleExoPlayer initPlayer() {
        return new SimpleExoPlayer.Builder(this).build();
    }

    private MediaSource buildMediaSource(Uri uri) {
        DefaultDataSourceFactory factory = new DefaultDataSourceFactory(this, mUserAgent);
        return new ProgressiveMediaSource.Factory(factory)
                .createMediaSource(new MediaItem.Builder().setUri(uri).build());
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
        mMediaSession.setCallback(new MediaSessionCallback());
    }

    /**
     * Media Session Callbacks, where all external clients control the player.
     */
    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            if (mVideoState) {
                mPlayer.setPlayWhenReady(true);
            }
        }

        @Override
        public void onPause() {
            if (mVideoState) {
                mPlayer.setPlayWhenReady(false);
            }
        }

        @Override
        public void onSkipToPrevious() {
            if (mVideoState) {
                mPlayer.seekTo(0);
            }
        }
    }
}
