package util;

/**
 * Data class representing the results of a file analysis/detection.
 */
public class DetectionResult {
    private final String magicType;
    private final String mimeGuess;
    private final double entropy;
    private final double textRatio;

    public DetectionResult(String magicType, String mimeGuess, double entropy, double textRatio) {
        this.magicType = magicType;
        this.mimeGuess = mimeGuess;
        this.entropy = entropy;
        this.textRatio = textRatio;
    }

    public String getMagicType() {
        return magicType;
    }

    public String getMimeGuess() {
        return mimeGuess;
    }

    public double getEntropy() {
        return entropy;
    }

    public double getTextRatio() {
        return textRatio;
    }

    @Override
    public String toString() {
        return String.format(
            "Detection Result:\n" +
            "  Magic Type: %s\n" +
            "  MIME Guess: %s\n" +
            "  Entropy:    %.4f (0 to 8)\n" +
            "  Text Ratio: %.2f%%",
            magicType, mimeGuess, entropy, textRatio * 100
        );
    }
}
