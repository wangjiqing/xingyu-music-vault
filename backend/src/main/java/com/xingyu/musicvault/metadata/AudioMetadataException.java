package com.xingyu.musicvault.metadata;

public class AudioMetadataException extends RuntimeException {
    public AudioMetadataException(String message) {
        super(message);
    }

    public AudioMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
