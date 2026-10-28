package com.swiftdrop.operations.courier.execution;

/**
 * Validates image content types by magic byte signatures rather than relying on untrusted client headers.
 */
public final class ContentTypeDetector {

    private ContentTypeDetector() {
        // Utility class
    }

    public static String detectImageMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        // JPEG: FF D8 FF
        if (bytes.length >= 3 &&
            (bytes[0] & 0xFF) == 0xFF &&
            (bytes[1] & 0xFF) == 0xD8 &&
            (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8 &&
            (bytes[0] & 0xFF) == 0x89 &&
            (bytes[1] & 0xFF) == 0x50 &&
            (bytes[2] & 0xFF) == 0x4E &&
            (bytes[3] & 0xFF) == 0x47 &&
            (bytes[4] & 0xFF) == 0x0D &&
            (bytes[5] & 0xFF) == 0x0A &&
            (bytes[6] & 0xFF) == 0x1A &&
            (bytes[7] & 0xFF) == 0x0A) {
            return "image/png";
        }

        // WEBP: RIFF (bytes 0..3) ... WEBP (bytes 8..11)
        if (bytes.length >= 12 &&
            bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
            bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }

        return null;
    }

    public static String getExtensionForMimeType(String mimeType) {
        if ("image/png".equalsIgnoreCase(mimeType)) {
            return "png";
        }
        if ("image/webp".equalsIgnoreCase(mimeType)) {
            return "webp";
        }
        return "jpg";
    }
}
