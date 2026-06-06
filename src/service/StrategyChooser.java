package service;

/**
 * Rules engine to select compression specifications based on file profile and mode requirements.
 * Aligned with the streaming codecs supported by StreamingCompressor.
 */
public class StrategyChooser {

    /**
     * Elects the best compression strategy configuration.
     *
     * @param mime    the MIME type (e.g. "text/plain")
     * @param entropy the Shannon entropy score (0.0 to 8.0)
     * @param mode    the optimization mode ("FAST", "BALANCED", "MAX_COMPRESSION")
     * @return the StrategyConfig selection
     */
    public static StrategyConfig choose(String mime, double entropy, String mode) {
        String cleanMode = (mode == null) ? "BALANCED" : mode.toUpperCase();
        String cleanMime = (mime == null) ? "application/octet-stream" : mime.toLowerCase();

        // Rule 1: High entropy files (already compressed or encrypted, e.g. PNG, ZIP, JPEG)
        // Re-compressing will only inflate file size due to header overhead.
        if (entropy > 7.5) {
            return new StrategyConfig("STORE", 0, 65536, 1);
        }

        // Determine thread capability (max cores config)
        int maxThreads = Runtime.getRuntime().availableProcessors();
        int activeThreads = 1;
        int level = 5;
        int chunkSize = 32768; // Default 32 KB chunk
        String codec = "GZIP";

        // Rule 2: Text contents (highly compressible)
        if (cleanMime.startsWith("text/") || cleanMime.contains("json") || cleanMime.contains("xml")) {
            switch (cleanMode) {
                case "FAST":
                    codec = "GZIP";
                    level = 3;
                    chunkSize = 16384; // 16 KB chunks
                    activeThreads = Math.min(2, maxThreads);
                    break;
                case "MAX_COMPRESSION":
                    codec = "LZMA";
                    level = 9;
                    chunkSize = 65536; // 64 KB chunks
                    activeThreads = Math.max(2, maxThreads);
                    break;
                case "BALANCED":
                default:
                    codec = "BZ2";
                    level = 6;
                    chunkSize = 32768; // 32 KB chunks
                    activeThreads = Math.min(4, maxThreads);
                    break;
            }
        } 
        // Rule 3: Bitmap or simple uncompressed images (compressible via BZ2/ZSTD)
        else if (cleanMime.startsWith("image/bmp") || (cleanMime.startsWith("image/") && entropy < 4.0)) {
            codec = "BZ2";
            level = 5;
            chunkSize = 16384;
            activeThreads = 1;
        } 
        // Rule 4: Binary payloads
        else {
            switch (cleanMode) {
                case "FAST":
                    codec = "GZIP";
                    level = 4;
                    chunkSize = 32768;
                    activeThreads = Math.min(2, maxThreads);
                    break;
                case "MAX_COMPRESSION":
                    codec = "LZMA";
                    level = 8;
                    chunkSize = 65536;
                    activeThreads = Math.max(2, maxThreads);
                    break;
                case "BALANCED":
                default:
                    codec = "GZIP";
                    level = 6;
                    chunkSize = 32768;
                    activeThreads = Math.min(4, maxThreads);
                    break;
            }
        }

        return new StrategyConfig(codec, level, chunkSize, activeThreads);
    }
}
