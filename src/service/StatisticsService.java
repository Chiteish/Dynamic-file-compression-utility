package service;

import java.io.File;

/**
 * Service for computing and presenting compression statistics.
 */
public class StatisticsService {

    /**
     * Prints the comparison statistics between original and compressed files.
     *
     * @param originalPath   path to the original file
     * @param compressedPath path to the compressed file
     */
    public void printStatistics(String originalPath, String compressedPath) {
        File originalFile = new File(originalPath);
        File compressedFile = new File(compressedPath);

        if (!originalFile.exists() || !compressedFile.exists()) {
            System.err.println("Error: One or both files do not exist to calculate statistics.");
            return;
        }

        long originalSize = originalFile.length();
        long compressedSize = compressedFile.length();

        // Edge case: Handle empty original file to avoid division by zero
        if (originalSize == 0) {
            System.out.println("=========================================");
            System.out.println("          COMPRESSION STATISTICS         ");
            System.out.println("=========================================");
            System.out.println("Original File Size:   0 bytes");
            System.out.println("Compressed File Size: " + compressedSize + " bytes");
            System.out.println("Compression Ratio:    N/A (Original is empty)");
            System.out.println("Space Savings:        0.00%");
            System.out.println("=========================================");
            return;
        }

        double ratio = ((double) compressedSize / originalSize) * 100;
        double savings = 100.0 - ratio;

        System.out.println("=========================================");
        System.out.println("          COMPRESSION STATISTICS         ");
        System.out.println("=========================================");
        System.out.printf("Original File Size:   %,d bytes\n", originalSize);
        System.out.printf("Compressed File Size: %,d bytes\n", compressedSize);
        System.out.printf("Compression Ratio:    %.2f%%\n", ratio);
        System.out.printf("Space Savings:        %.2f%%\n", savings);
        System.out.println("=========================================");
    }
}
