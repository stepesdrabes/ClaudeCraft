package dev.claudecraft.agent;

public final class ImageData {
    private final String mediaType;
    private final byte[] data;

    public ImageData(String mediaType, byte[] data) {
        this.mediaType = mediaType;
        this.data = data;
    }

    public String mediaType() {
        return mediaType;
    }

    public byte[] data() {
        return data;
    }
}
