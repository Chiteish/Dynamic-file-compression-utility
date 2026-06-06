package service;

/**
 * Configuration detailing the codec, compression level, chunk size, and execution thread count.
 */
public class StrategyConfig {
    private final String codec;
    private final int level;
    private final int chunkSize;
    private final int threads;

    public StrategyConfig(String codec, int level, int chunkSize, int threads) {
        this.codec = codec;
        this.level = level;
        this.chunkSize = chunkSize;
        this.threads = threads;
    }

    public String getCodec() {
        return codec;
    }

    public int getLevel() {
        return level;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getThreads() {
        return threads;
    }

    @Override
    public String toString() {
        return String.format(
            "Strategy Configuration:\n" +
            "  Codec:      %s\n" +
            "  Level:      %d (0-9)\n" +
            "  Chunk Size: %,d bytes\n" +
            "  Threads:    %d",
            codec, level, chunkSize, threads
        );
    }
}
