package com.stmicroelectronics.stvideo.adapter;

import android.content.Context;
import android.content.UriPermission;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.stmicroelectronics.stvideo.R;
import com.stmicroelectronics.stvideo.data.VideoDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import timber.log.Timber;

/**
 * Video adapter
 */

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.ViewHolder>{

    private final VideoAdapter.OnClickListener mListener;
    private final ArrayList<VideoDetails> arrayList = new ArrayList<>();

    public VideoAdapter(OnClickListener listener){
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoDetails video = arrayList.get(position);

        String videoThumb = video.getVideoThumb();
        if (videoThumb != null) {
           holder.videoThumb.setImageDrawable(Drawable.createFromPath(videoThumb));
        }

        holder.videoTitle.setText(video.getVideoName());
    }

    @Override
    public int getItemCount() {
        return arrayList.size();
    }

    public ArrayList<VideoDetails> getList() {
        return arrayList;
    }

    public boolean isPermissionGranted (List<UriPermission> permissionList) {
        for (VideoDetails details : arrayList) {
            details.setPermissionGranted(false);
            for (UriPermission permission:permissionList) {
                if ((permission.getUri() == null) || (permission.getUri().equals(details.getAudioUriPermission()))) {
                    details.setPermissionGranted(true);
                    break;
                }
            }
            if (! details.isPermissionGranted()) {
                return false;
            }
        }
        return true;
    }

    public void addItem(VideoDetails video) {
        int prevSize = arrayList.size();
        arrayList.add(video);
        notifyItemRangeInserted(prevSize,1);
    }

    public void addItems(ArrayList<VideoDetails> list) {
        int prevSize = arrayList.size();
        arrayList.addAll(list);
        notifyItemRangeInserted(prevSize,arrayList.size() - prevSize);
    }

    public void removeAllItems() {
        int prevSize = arrayList.size();
        arrayList.clear();
        notifyItemRangeRemoved(0, prevSize);
    }

    public void removeAllPrimaryItems() {
        int position = 0;
        int primarySize = arrayList.size();

        // found position of first item with primary
        for (VideoDetails item:arrayList) {
            if (item.isVolumePrimary()) {
                break;
            }
            position++;
        }

        // create remove condition (case volume primary)
        Predicate<VideoDetails> condition = VideoDetails::isVolumePrimary;

        if (arrayList.removeIf(condition)) {
            primarySize -= arrayList.size();
            Timber.d("Remove External items:%d (%d)", position, primarySize);
            if (primarySize > 0) {
                notifyItemRangeRemoved(position, primarySize);
            }
        }
    }

    public void removeAllExternalItems() {
        int position = 0;
        int externalSize = arrayList.size();

        // found position of first item with external
        for (VideoDetails item:arrayList) {
            if (item.isVolumeExternal()) {
                break;
            }
            position++;
        }

        // create remove condition (case volume external)
        Predicate<VideoDetails> condition = VideoDetails::isVolumeExternal;

        if (arrayList.removeIf(condition)) {
            externalSize -= arrayList.size();
            Timber.d("Remove External items:%d (%d)", position, externalSize);
            if (externalSize > 0) {
                notifyItemRangeRemoved(position, externalSize);
            }
        }
    }

    public interface OnClickListener {
        void onClick(VideoDetails video);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{

        ImageView videoThumb;
        TextView videoTitle;

        ViewHolder(View itemView) {
            super(itemView);
            videoThumb = itemView.findViewById(R.id.video_thumb);
            videoTitle = itemView.findViewById(R.id.video_title);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int adapterPosition = getAbsoluteAdapterPosition();

            if (adapterPosition != RecyclerView.NO_POSITION) {
                VideoDetails video = arrayList.get(adapterPosition);
                mListener.onClick(video);
            } else {
                Timber.d("position is not yet available");
            }
        }
    }
}
