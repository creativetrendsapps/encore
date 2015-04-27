/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package org.omnirom.music.service;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.os.Build;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.receivers.RemoteControlReceiver;
import org.omnirom.music.utils.AvrcpUtils;
import org.omnirom.music.utils.Utils;

/**
 * Remote Metadata manager for pre-Lollipop devices
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class RemoteMetadataManager implements IRemoteMetadataManager {

    private RemoteControlClient mClient;
    private final ComponentName mEventReceiver;
    private final AudioManager mAudioManager;
    private BitmapDrawable mPreviousAlbumArt;
    private PlaybackService mContext;

    public RemoteMetadataManager(PlaybackService service) {
        mEventReceiver = new ComponentName(service, RemoteControlReceiver.class);
        mContext = service;

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mEventReceiver);
        PendingIntent mediaPendingIntent
                = PendingIntent.getBroadcast(service.getApplicationContext(), 0, mediaButtonIntent, 0);
        mClient = new RemoteControlClient(mediaPendingIntent);

        mAudioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
    }

    private int getActionsFlags(final boolean hasNext) {
        int actions = 0;
        actions |= RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE;
        actions |= RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS;
        actions |= RemoteControlClient.FLAG_KEY_MEDIA_RATING;
        if (hasNext) {
            actions |= RemoteControlClient.FLAG_KEY_MEDIA_NEXT;
        }

        return actions;
    }

    @Override
    public void setup() {

    }

    @Override
    public void release() {

    }

    @Override
    public void setActive(boolean active) {
        if (active) {
            mAudioManager.registerMediaButtonEventReceiver(mEventReceiver);
            mAudioManager.registerRemoteControlClient(mClient);
        } else {
            mAudioManager.unregisterMediaButtonEventReceiver(mEventReceiver);
            mAudioManager.unregisterRemoteControlClient(mClient);
        }
    }

    @Override
    public void setAlbumArt(BitmapDrawable bmp) {
        if (mPreviousAlbumArt != bmp) {
            RemoteControlClient.MetadataEditor metadata = mClient.editMetadata(false);
            if (bmp != null) {
                Bitmap bmpSource = bmp.getBitmap();
                Bitmap bmpCopy = bmpSource.copy(bmpSource.getConfig(), false);

                mPreviousAlbumArt = bmp;
                metadata.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, bmpCopy);
            } else {
                metadata.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, null);
            }

            metadata.apply();
        }
    }

    @Override
    public void setCurrentSong(Song song, boolean hasNext) {
        final RemoteControlClient.MetadataEditor metadata = mClient.editMetadata(false);
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
        final Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());

        if (artist != null) {
            metadata.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist.getName());
        }
        if (album != null) {
            metadata.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album.getName());
        }
        metadata.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, song.getTitle());
        metadata.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, song.getDuration());

        // Ensure the defined bitmap is still valid
        if (Utils.hasKitKat()) {
            Bitmap check = metadata.getBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK, null);
            if (check != null && check.isRecycled()) {
                metadata.putBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK, null);
            }
        }

        metadata.apply();
        mClient.setTransportControlFlags(getActionsFlags(hasNext));

        AvrcpUtils.notifyMetaChanged(mContext, song.getRef().hashCode(),
                artist != null ? artist.getName() : null,
                album != null ? album.getName() : null,
                song.getTitle(), mContext.getQueue().size(),
                song.getDuration(), mContext.getCurrentTrackPositionImpl());
    }

    @Override
    public void notifyPlaying(long timeElapsed) {
        mClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
        AvrcpUtils.notifyPlayStateChanged(mContext, true, timeElapsed);
    }

    @Override
    public void notifyBuffering() {
        mClient.setPlaybackState(RemoteControlClient.PLAYSTATE_BUFFERING);
        AvrcpUtils.notifyPlayStateChanged(mContext, true, 0);
    }

    @Override
    public void notifyPaused(long timeElapsed) {
        mClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
        AvrcpUtils.notifyPlayStateChanged(mContext, true, 0);
    }

    @Override
    public void notifyStopped() {
        mClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
        AvrcpUtils.notifyPlayStateChanged(mContext, false, 0);
    }
}
