package com.xingyu.musicvault.metadata;

import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class AudioMetadataService {
    public MetadataSnapshot read(Path path) {
        requireReadableFile(path);
        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());
            Tag tag = audioFile.getTag();
            Long duration = audioFile.getAudioHeader() == null ? null : (long) audioFile.getAudioHeader().getTrackLength();
            if (tag == null) {
                return new MetadataSnapshot(null, null, null, null, null, null, null, duration);
            }
            return new MetadataSnapshot(
                    clean(tag.getFirst(FieldKey.TITLE)),
                    clean(tag.getFirst(FieldKey.ARTIST)),
                    clean(tag.getFirst(FieldKey.ALBUM)),
                    clean(tag.getFirst(FieldKey.ALBUM_ARTIST)),
                    parseYear(clean(tag.getFirst(FieldKey.YEAR))),
                    clean(tag.getFirst(FieldKey.GENRE)),
                    parseTrackNumber(clean(tag.getFirst(FieldKey.TRACK))),
                    duration
            );
        } catch (AudioMetadataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AudioMetadataException("Unsupported or unreadable audio metadata: " + path, exception);
        }
    }

    public MetadataSnapshot write(Path path, MetadataSnapshot snapshot) {
        requireWritableFile(path);
        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            setOrDelete(tag, FieldKey.TITLE, snapshot.title());
            setOrDelete(tag, FieldKey.ARTIST, snapshot.artist());
            setOrDelete(tag, FieldKey.ALBUM, snapshot.album());
            audioFile.commit();
            return read(path);
        } catch (AudioMetadataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AudioMetadataException("Failed to write audio metadata: " + path, exception);
        }
    }

    private void requireReadableFile(Path path) {
        if (path == null) {
            throw new AudioMetadataException("Audio file path is required");
        }
        if (!Files.exists(path)) {
            throw new AudioMetadataException("Audio file does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new AudioMetadataException("Audio path is not a file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new AudioMetadataException("Audio file is not readable: " + path);
        }
    }

    private void requireWritableFile(Path path) {
        requireReadableFile(path);
        if (!Files.isWritable(path)) {
            throw new AudioMetadataException("Audio file is not writable: " + path);
        }
    }

    private void setOrDelete(Tag tag, FieldKey key, String value) throws Exception {
        String cleaned = clean(value);
        if (cleaned == null) {
            tag.deleteField(key);
        } else {
            tag.setField(key, cleaned);
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseYear(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.length() >= 4 ? value.substring(0, 4) : value;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseTrackNumber(String value) {
        if (value == null) {
            return null;
        }
        String first = value.split("/", 2)[0].trim();
        try {
            int trackNumber = Integer.parseInt(first);
            return trackNumber > 0 ? trackNumber : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
