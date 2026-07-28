package dev.trashcode07.trashaddons.media.providers;

import dev.trashcode07.trashaddons.media.MediaInfo;

public final class MacProvider implements MediaProvider {
    //Not implemented yet.
    @Override public MediaInfo pollCurrentMedia()                        { return MediaInfo.EMPTY; }
    @Override public void togglePlayPause(MediaInfo info)                {}
    @Override public void skipNext(MediaInfo info)                       {}
    @Override public void skipPrevious(MediaInfo info)                   {}
}
