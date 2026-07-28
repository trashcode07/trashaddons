package dev.trashcode07.trashaddons.media.providers;

import dev.trashcode07.trashaddons.media.MediaInfo;

public interface MediaProvider {
    MediaInfo pollCurrentMedia();
    void togglePlayPause(MediaInfo info);
    void skipNext(MediaInfo info);
    void skipPrevious(MediaInfo info);
}
