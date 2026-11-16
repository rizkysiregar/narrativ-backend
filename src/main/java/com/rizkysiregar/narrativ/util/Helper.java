package com.rizkysiregar.narrativ.util;

public class Helper {


    /**
     * Using for detect content type base on file name extension
     *
     */
    public static String detectContentTypeFromFilename(String filename) {
        String lower = filename.toLowerCase();

        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "image/tiff";

        return null; // let caller fallback to octet-stream
    }

}
