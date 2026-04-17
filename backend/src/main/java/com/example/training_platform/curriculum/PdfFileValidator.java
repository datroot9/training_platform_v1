package com.example.training_platform.curriculum;

/**
 * Validates PDF by magic bytes (starts with %PDF).
 */
public final class PdfFileValidator {

    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F'};

    private PdfFileValidator() {
    }

    public static boolean isPdf(byte[] data) {
        if (data == null || data.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (data[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
