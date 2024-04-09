package com.stmicroelectronics.stvideo.data;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Video information details
 */

public class VideoDetails implements Parcelable {

    public final static int AUDIO_VOLUME_INTERNAL = 1;
    public final static int AUDIO_VOLUME_EXTERNAL = 2;
    public final static int AUDIO_VOLUME_EXTERNAL_PRIMARY = 3;

    private int mVolume;

    private String mVideoName;
    private String mVideoThumb;
    private Uri mVideoUri;

    private Uri mAudioUriPermission;
    private boolean mPermissionGranted;

    public VideoDetails() {

    }

    public VideoDetails(Parcel in) {
        mVolume = in.readInt();
        mVideoName = in.readString();
        mVideoUri = in.readParcelable(Uri.class.getClassLoader());
        mAudioUriPermission = in.readParcelable(Uri.class.getClassLoader());
        mPermissionGranted = in.readByte() != 0;
        mVideoThumb = in.readString();
        // Bitmap bitmap = in.readParcelable(getClass().getClassLoader());
        // mVideoThumb = new BitmapDrawable(Resources.getSystem(),bitmap);
    }

    public static final Creator<VideoDetails> CREATOR = new Creator<>() {
        @Override
        public VideoDetails createFromParcel(Parcel in) {
            return new VideoDetails(in);
        }

        @Override
        public VideoDetails[] newArray(int size) {
            return new VideoDetails[size];
        }
    };

    public void setVideoName(String mVideoName) {
        this.mVideoName = mVideoName;
    }
    public String getVideoName() {
        return mVideoName;
    }

    public void setVideoThumb(String mVideoThumb) {
        this.mVideoThumb = mVideoThumb;
    }
    public String getVideoThumb() {
        return mVideoThumb;
    }

    public void setVideoUri(Uri uri) {
        mVideoUri = uri;
    }
    public Uri getVideoUri() {
        return mVideoUri;
    }

    public Uri getAudioUriPermission() {
        return mAudioUriPermission;
    }
    public void setAudioUriPermission(Uri audioUri) {
        mAudioUriPermission = audioUri;
    }

    public boolean isPermissionGranted(){return mPermissionGranted;}
    public void setPermissionGranted(boolean granted){ mPermissionGranted = granted;}

    public void setVolume(int volume) {
        mVolume = volume;
    }

    public boolean isVolumePrimary() {
        return mVolume == AUDIO_VOLUME_EXTERNAL_PRIMARY;
    }
    public boolean isVolumeInternal() {
        return mVolume == AUDIO_VOLUME_INTERNAL;
    }
    public boolean isVolumeExternal() {
        return mVolume == AUDIO_VOLUME_EXTERNAL;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mVolume);
        dest.writeString(mVideoName);
        dest.writeParcelable(mVideoUri, flags);
        dest.writeParcelable(mAudioUriPermission, flags);
        dest.writeByte((byte) (mPermissionGranted ? 1 : 0));
        dest.writeString(mVideoThumb);
        // Bitmap bitmap = ((BitmapDrawable) mVideoThumb).getBitmap();
        // dest.writeParcelable(bitmap, flags);
    }
}
