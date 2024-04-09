package com.stmicroelectronics.stvideo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.stmicroelectronics.stvideo.adapter.VideoAdapter;
import com.stmicroelectronics.stvideo.data.VideoDetails;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements VideoAdapter.OnClickListener {

    private final static String EXTERNAL_MOVIES_DIR_NAME = "Movies";

    private final static String STATE_LIST = "com.stmicroelectronics.stvideo.list";

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;
    private final AtomicBoolean mFullScreen = new AtomicBoolean();
    private String mVideoScreenOrientation;

    SwipeRefreshLayout mSwipeRefreshView;
    RecyclerView mVideoListView;
    TextView mVideoMsgView;

    int mSpanCount;

    private VideoAdapter mVideoAdapter;

    private final ArrayList<String> mVideoThumbList = new ArrayList<>();
    private boolean mExternalAccessGranted = false;
    private boolean mPrimaryStorageReadGranted = false;

    private int mNbUSBDevices = 0;
    private boolean mNewUSBDeviceAttached = false;

    ImageButton mScanUsbButton;

    private final ActivityResultLauncher<Intent> mActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    mVideoAdapter.removeAllExternalItems();
                    List<UriPermission> list1 = getContentResolver().getPersistedUriPermissions();
                    if (!list1.isEmpty()) {
                        for (UriPermission permission : list1) {
                            parseExternal(permission.getUri());
                        }
                    }
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri treeUri = data.getData();
                            if (treeUri != null) {
                                getContentResolver().takePersistableUriPermission(treeUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                parseExternal(treeUri);
                                mExternalAccessGranted = true;
                            }
                        }
                        mNewUSBDeviceAttached = false;
                    }

                    if (mVideoAdapter.getItemCount() > 0) {
                        mVideoMsgView.setVisibility(View.GONE);
                    } else {
                        mVideoMsgView.setVisibility(View.VISIBLE);
                    }
                    mSwipeRefreshView.setRefreshing(false);
                }
            }
    );

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                synchronized (this) {
                    for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                        if (usbDevice.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                            mNbUSBDevices--;
                            if (mNbUSBDevices < 0) {
                                mNbUSBDevices = 0;
                            }
                            Timber.d("USB device (USB_CLASS_MASS_STORAGE) detached");
                        }
                    }
                }
                if (mNbUSBDevices == 0) {
                    mScanUsbButton.setVisibility(View.INVISIBLE);
                    mVideoAdapter.removeAllExternalItems();
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                synchronized (this) {
                    for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                        if (usbDevice.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                            mNbUSBDevices++;
                            mNewUSBDeviceAttached = true;
                            Timber.d("USB device (USB_CLASS_MASS_STORAGE) attached");
                        }
                    }
                }
                if (mNbUSBDevices > 0) {
                    mScanUsbButton.setVisibility(View.VISIBLE);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSwipeRefreshView = findViewById(R.id.swipe_refresh_video);
        mVideoListView = findViewById(R.id.video_list);
        mVideoMsgView = findViewById(R.id.video_msg);
        mScanUsbButton = findViewById(R.id.button_usb);

        mSpanCount = getResources().getInteger(R.integer.video_span_count);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        // Register an intent filter so we can get device attached/removed messages
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        UsbManager usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        if (usbManager != null) {
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            for (UsbDevice usbDevice : deviceList.values()) {
                for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                    if (usbDevice.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        mNbUSBDevices++;
                    }
                }
            }
        }

        if (mNbUSBDevices > 0) {
            mScanUsbButton.setVisibility(View.VISIBLE);
        } else {
            mScanUsbButton.setVisibility(View.INVISIBLE);
        }

        initPreferences();

        // initialize the refresh listener
        mSwipeRefreshView.setOnRefreshListener(() -> {
            if (mVideoAdapter != null) {
                mSwipeRefreshView.setRefreshing(true);
                mVideoAdapter.removeAllItems();
                parsePrimary();
                // just update from granted URI
                if (mExternalAccessGranted) {
                    List<UriPermission> list = getContentResolver().getPersistedUriPermissions();
                    for (UriPermission permission : list) {
                        parseExternal(permission.getUri());
                    }
                }
                cleanThumbnails();
                if (mVideoAdapter.getItemCount() > 0) {
                    mVideoMsgView.setVisibility(View.GONE);
                } else {
                    mVideoMsgView.setVisibility(View.VISIBLE);
                }
                mSwipeRefreshView.setRefreshing(false);
            }
        });
        mSwipeRefreshView.setColorSchemeResources(R.color.colorPrimary, R.color.colorPrimaryDark,
                R.color.colorAccent);

        if (!mExternalAccessGranted) {
            // check that there is no granted URI already
            List<UriPermission> list = getContentResolver().getPersistedUriPermissions();
            if (!list.isEmpty()) {
                mExternalAccessGranted = true;
            }
        }

        // initialize the RecyclerView for video list
        mVideoAdapter = new VideoAdapter(this);
        if (savedInstanceState != null) {
            ArrayList<VideoDetails> list = savedInstanceState.getParcelableArrayList(STATE_LIST);
            mVideoAdapter.addItems(list);
        }
        mVideoListView.setAdapter(mVideoAdapter);
        mVideoListView.setLayoutManager(new GridLayoutManager(this, mSpanCount));

        // check available permissions and parse granted storage
        if (mVideoAdapter.getItemCount() == 0) {
            checkPermission();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(STATE_LIST, mVideoAdapter.getList());
    }

    private void initPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (mPrefListener == null) {
            mPrefListener = (sharedPreferences, key) -> {
                if (key.equals(getString(R.string.pref_orientation_label))) {
                    mVideoScreenOrientation = sharedPreferences.getString(getString(R.string.pref_orientation_label),getString(R.string.portrait_value));
                }
                if (key.equals(getString(R.string.pref_rescale_label))){
                    mFullScreen.set(sharedPreferences.getBoolean(key, false));
                }
            };
        }

        mFullScreen.set(preferences.getBoolean(getString(R.string.pref_rescale_label), false));
        mVideoScreenOrientation = preferences.getString(getString(R.string.pref_orientation_label),getString(R.string.portrait_value));

        preferences.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
    }

    private final int REQUEST_PERMISSION_READ_EXTERNAL_STORAGE_STATE=1;

    private void checkPermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Timber.d("Permission READ_EXTERNAL_STORAGE denied : request it !");
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION_READ_EXTERNAL_STORAGE_STATE);
        } else {
            Timber.d("Permission READ_EXTERNAL_STORAGE (already) Granted!");
            mPrimaryStorageReadGranted = true;
            mSwipeRefreshView.setRefreshing(true);
            mVideoAdapter.removeAllItems();
            parsePrimary();
            if (mExternalAccessGranted) {
                mVideoAdapter.removeAllExternalItems();
                List<UriPermission> list = getContentResolver().getPersistedUriPermissions();
                for (UriPermission permission : list) {
                    parseExternal(permission.getUri());
                }
            }
            if (mVideoAdapter.getItemCount() > 0) {
                mVideoMsgView.setVisibility(View.GONE);
            } else {
                mVideoMsgView.setVisibility(View.VISIBLE);
            }
            mSwipeRefreshView.setRefreshing(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_READ_EXTERNAL_STORAGE_STATE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.d("Permission READ_EXTERNAL_STORAGE Granted! Parse Video");
                mPrimaryStorageReadGranted = true;
                mSwipeRefreshView.setRefreshing(true);
                mVideoAdapter.removeAllItems();
                parsePrimary();
                if (mExternalAccessGranted) {
                    mVideoAdapter.removeAllExternalItems();
                    List<UriPermission> list = getContentResolver().getPersistedUriPermissions();
                    for (UriPermission permission : list) {
                        parseExternal(permission.getUri());
                    }
                }
                cleanThumbnails();
                if (mVideoAdapter.getItemCount() > 0) {
                    mVideoMsgView.setVisibility(View.GONE);
                } else {
                    mVideoMsgView.setVisibility(View.VISIBLE);
                }
                mSwipeRefreshView.setRefreshing(false);
            } else {
                Timber.d("Permission READ_EXTERNAL_STORAGE Denied!");
            }
        }
    }

    private void parsePrimary(){

        if (mPrimaryStorageReadGranted && isPrimaryAvailable()) {

            String[] projection = new String[]{
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.TITLE,
                    MediaStore.Video.VideoColumns.DURATION
            };

            ContentResolver contentResolver = getContentResolver();
            Uri volumeUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Cursor cursor = contentResolver.query(volumeUri,
                    projection, null, null,null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    ArrayList<VideoDetails> videoList = new ArrayList<>();
                    do {
                        VideoDetails video = new VideoDetails();

                        int id = cursor.getInt(0);
                        video.setVideoUri(Uri.withAppendedPath(volumeUri,Integer.toString(id)));
                        video.setVideoName(cursor.getString(1));

                        DocumentFile pickedFile = DocumentFile.fromSingleUri(this, video.getVideoUri());
                        if (pickedFile != null) {
                            video.setVideoThumb(getThumbnailDrawable(pickedFile));
                            if (pickedFile.getName() != null) {
                                mVideoThumbList.add(getThumbnailName(pickedFile.getName()));
                            }
                        }

                        // no associated permission URI for primary (generic external storage access used)
                        video.setAudioUriPermission(null);
                        video.setVolume(VideoDetails.AUDIO_VOLUME_EXTERNAL_PRIMARY);

                        videoList.add(video);
                    } while (cursor.moveToNext());

                    if(! videoList.isEmpty()) {
                        mVideoAdapter.addItems(videoList);
                    }
                }
                cursor.close();
            }
        }
    }

    private boolean isPrimaryAvailable() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED);
    }

    public void scanExternal(View view) {
        mSwipeRefreshView.setRefreshing(true);
        List<UriPermission> list = getContentResolver().getPersistedUriPermissions();
        if (list.isEmpty() || ! mVideoAdapter.isPermissionGranted(list) || mNewUSBDeviceAttached) {
            // At least one element in list not granted (or list empty)
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            mActivityResultLauncher.launch(intent);
        } else {
            mVideoAdapter.removeAllExternalItems();
            for (UriPermission permission:list){
                parseExternal(permission.getUri());
            }

            if (mVideoAdapter.getItemCount() > 0) {
                mVideoMsgView.setVisibility(View.GONE);
            } else {
                mVideoMsgView.setVisibility(View.VISIBLE);
            }
            mSwipeRefreshView.setRefreshing(false);
        }
    }

    public void openSettings(View view) {
        Intent intent = new Intent(getApplicationContext(),SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    private void parseExternal(Uri treeUri){
        if (checkExternalStorageExists(treeUri)) {
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
            // List all existing files inside picked directory
            if (pickedDir != null && pickedDir.exists()) {
                Toast.makeText(this, "Parse USB root directory and Movies sub-directory",
                        Toast.LENGTH_SHORT).show();
                ArrayList<VideoDetails> videoList = new ArrayList<>();
                for (DocumentFile file : pickedDir.listFiles()) {
                    if (!file.isDirectory()) {
                        if (isVideoFile(file)) {
                            if (file.getName() != null) {
                                Timber.d("File available %s", file.getName());
                                VideoDetails video = new VideoDetails();

                                video.setVideoName(file.getName());
                                video.setVideoThumb(getThumbnailDrawable(file));
                                video.setVideoUri(file.getUri());
                                video.setAudioUriPermission(treeUri);
                                video.setVolume(VideoDetails.AUDIO_VOLUME_EXTERNAL);
                                mVideoThumbList.add(getThumbnailName(file.getName()));
                                videoList.add(video);
                            }
                        }
                    } else {
                        if (file.getName() != null && file.getName().equals(EXTERNAL_MOVIES_DIR_NAME) && file.isDirectory()) {
                            Toast.makeText(this, "Parse USB " + EXTERNAL_MOVIES_DIR_NAME
                                    + " directory if exists", Toast.LENGTH_SHORT).show();
                            for (DocumentFile movie : file.listFiles()) {
                                if (!movie.isDirectory()) {
                                    if (isVideoFile(movie)) {
                                        if (movie.getName() != null) {
                                            Timber.d("File available %s", movie.getName());
                                            VideoDetails video = new VideoDetails();
                                            video.setVideoName(movie.getName());
                                            video.setVideoThumb(getThumbnailDrawable(movie));
                                            video.setVideoUri(movie.getUri());
                                            video.setAudioUriPermission(treeUri);
                                            video.setVolume(VideoDetails.AUDIO_VOLUME_EXTERNAL);
                                            mVideoThumbList.add(getThumbnailName(movie.getName()));
                                            videoList.add(video);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!videoList.isEmpty()) {
                    mVideoAdapter.addItems(videoList);
                }
            }
        }
    }

    private boolean checkExternalStorageExists(Uri permission) {
        StorageManager storageManager = (StorageManager) getSystemService(STORAGE_SERVICE);
        if (storageManager != null) {
            List<StorageVolume> volumes = storageManager.getStorageVolumes();
            for (StorageVolume v:volumes){
                if (!v.isPrimary()) {
                    String desc = v.getUuid();
                    String volume = permission.getLastPathSegment();
                    if (desc != null && volume != null && volume.startsWith(desc)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isVideoFile(DocumentFile file) {
        ContentResolver cr = getContentResolver();
        String type = cr.getType(file.getUri());
        return type != null && type.startsWith("video");
    }

    private String getThumbnailDrawable(DocumentFile video) {
        if (video.getName() != null) {
            String thumbName = getThumbnailName(video.getName());
            File thumb = new File(getFilesDir(), thumbName);

            if (! thumb.exists()) {
                generateThumbnail(video, thumb);
            }
            return thumb.getAbsolutePath();
        }
        return null;
    }

    private void generateThumbnail(DocumentFile video, File thumb) {

        Bitmap bitmap = null;
        ParcelFileDescriptor pfd;

        try {
            pfd = getContentResolver().openFileDescriptor(video.getUri(), "r");
            if (pfd != null) {
                FileDescriptor fd = pfd.getFileDescriptor();
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(fd);
                bitmap = retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST,
                        getResources().getDimensionPixelSize(R.dimen.video_width),
                        getResources().getDimensionPixelSize(R.dimen.video_height));
                pfd.close();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (bitmap != null) {
            try {
                FileOutputStream stream = new FileOutputStream(thumb);
                bitmap.compress(Bitmap.CompressFormat.JPEG,100,stream);
                stream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getThumbnailName(String video) {
        int last = video.lastIndexOf(".");
        return last >= 1 ? video.substring(0, last) + ".jpg" : video + ".jpg";
    }

    private void cleanThumbnails() {
        new Thread(() -> {
            File directory = getFilesDir();
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file: files) {
                    if ((! mVideoThumbList.isEmpty()) && (! mVideoThumbList.contains(file.getName()))) {
                        if (! file.delete()) {
                            Timber.e("No possible to delete the file %s", file.getName());
                        }
                    }
                }
            }
        }).start();
    }

    private boolean isStorageAvailable(VideoDetails video) {
        if (video.isVolumePrimary()) {
            if(! isPrimaryAvailable()) {
                Toast.makeText(this, "External primary storage no more available (SD card)," +
                        " remove all associated files from list", Toast.LENGTH_SHORT).show();
                mVideoAdapter.removeAllPrimaryItems();
                return false;
            }
            return true;
        }
        if (video.isVolumeExternal()) {
            if (! checkExternalStorageExists(video.getAudioUriPermission())) {
                Toast.makeText(this, "External storage no more available (USB key)," +
                        " remove all associated files from list", Toast.LENGTH_SHORT).show();
                mVideoAdapter.removeAllExternalItems();
                getContentResolver().releasePersistableUriPermission(video.getAudioUriPermission(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                mExternalAccessGranted = false;
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    @Override
    public void onClick(VideoDetails video) {
        if (video != null) {
            if (isStorageAvailable(video)) {
                Uri videoUri = video.getVideoUri();
                Intent intent;

                Timber.d("START EXO PLAYER with %s", video.getVideoName());
                intent = new Intent(this, ExoPlayerActivity.class);
                intent.putExtra(ExoPlayerActivity.VIDEO_URI_EXTRA, videoUri);
                intent.putExtra(ExoPlayerActivity.VIDEO_ORIENTATION_EXTRA, mVideoScreenOrientation);
                intent.putExtra(ExoPlayerActivity.VIDEO_FULLSCREEN_EXTRA, mFullScreen.get());
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            }
        }
    }
}
