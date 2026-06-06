package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Detector utility to inspect file headers, guess MIME type, and compute entropy/text ratio.
 */
public class Detector {

    /**
     * Inspects a file and calculates magic signature, MIME types, Shannon entropy, and text density.
     *
     * @param filePath path to the file to scan
     * @return a DetectionResult object containing file metrics
     * @throws IOException if the file cannot be read
     */
    public static DetectionResult detect(String filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        if (bytes.length == 0) {
            return new DetectionResult("EMPTY", "application/x-empty", 0.0, 0.0);
        }

        // 1. Detect magic signature and guess MIME
        String magicType = "UNKNOWN";
        String mimeGuess = "application/octet-stream";

        if (bytes.length >= 4) {
            // Read first 4 bytes as unsigned integers
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            int b2 = bytes[2] & 0xFF;
            int b3 = bytes[3] & 0xFF;

            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
                magicType = "PNG";
                mimeGuess = "image/png";
            } else if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
                magicType = "JPEG";
                mimeGuess = "image/jpeg";
            } else if (b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46) {
                magicType = "PDF";
                mimeGuess = "application/pdf";
            } else if (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04) {
                magicType = "ZIP";
                mimeGuess = "application/zip";
            } else if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) {
                magicType = "GIF";
                mimeGuess = "image/gif";
            } else if (b0 == 0x7F && b1 == 0x45 && b2 == 0x4C && b3 == 0x46) {
                magicType = "ELF";
                mimeGuess = "application/x-elf";
            }
        }

        // 2. Calculate Shannon Entropy
        double entropy = calculateEntropy(bytes);

        // 3. Calculate Text/Printable Ratio
        double textRatio = calculateTextRatio(bytes);

        // If unknown file signature but has a high text/printable density, classify as plain text
        if (magicType.equals("UNKNOWN") && textRatio > 0.90) {
            magicType = "TEXT";
            mimeGuess = "text/plain";
        }

        return new DetectionResult(magicType, mimeGuess, entropy, textRatio);
    }

    /**
     * Calculates Shannon entropy of a byte array (returns a value between 0.0 and 8.0).
     */
    private static double calculateEntropy(byte[] bytes) {
        int[] frequencies = new int[256];
        for (byte b : bytes) {
            frequencies[b & 0xFF]++;
        }

        double entropy = 0.0;
        double total = bytes.length;

        for (int freq : frequencies) {
            if (freq > 0) {
                double probability = freq / total;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }

        return entropy;
    }

    /**
     * Calculates the ratio of printable characters (whitespace + standard ASCII) to total bytes.
     */
    private static double calculateTextRatio(byte[] bytes) {
        int printableCount = 0;
        for (byte b : bytes) {
            int val = b & 0xFF;
            // Printable ASCII range: 32 to 126
            // Common white space characters: LF (\n = 10), CR (\r = 13), TAB (\t = 9)
            if ((val >= 32 && val <= 126) || val == 10 || val == 13 || val == 9) {
                printableCount++;
            }
        }
        return (double) printableCount / bytes.length;
    }
}
