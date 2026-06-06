package service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Trains a Zstandard (.dict) dictionary by sampling chunks of a source dataset
 * and invoking the zstd training utility.
 */
public class ZstdDictionaryTrainer {

    /**
     * Samples chunks from a file and runs the zstd dictionary trainer.
     *
     * @param sourcePath     the dataset file to sample from
     * @param dictOutputPath the path where the resulting .dict file will be saved
     * @param chunkSize      size of each sample chunk in bytes (e.g. 1024)
     * @param maxSamples     maximum number of samples to collect
     * @throws IOException if any I/O or CLI process error occurs
     */
    public void train(String sourcePath, String dictOutputPath, int chunkSize, int maxSamples) throws IOException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source dataset file not found: " + sourcePath);
        }

        System.out.println("Reading source dataset: " + sourcePath);
        byte[] allBytes = Files.readAllBytes(Paths.get(sourcePath));

        if (allBytes.length < chunkSize) {
            throw new IllegalArgumentException("Source file size is smaller than the requested chunk size.");
        }

        // Create a temporary directory to store sampled chunks
        File tempDir = Files.createTempDirectory("zstd_samples_").toFile();
        tempDir.deleteOnExit();

        List<File> sampleFiles = new ArrayList<>();
        int bytesPointer = 0;
        int sampleCount = 0;

        System.out.println("Sampling dataset into chunks of " + chunkSize + " bytes...");
        while (bytesPointer + chunkSize <= allBytes.length && sampleCount < maxSamples) {
            File sampleFile = new File(tempDir, "sample_" + sampleCount + ".dat");
            sampleFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(sampleFile)) {
                fos.write(allBytes, bytesPointer, chunkSize);
            }
            sampleFiles.add(sampleFile);

            bytesPointer += chunkSize;
            sampleCount++;
        }

        System.out.println("Successfully collected " + sampleCount + " samples.");

        // Build command to execute: zstd --train sample1 sample2 ... -o dict_output
        List<String> command = new ArrayList<>();
        command.add("zstd");
        command.add("--train");
        for (File sample : sampleFiles) {
            command.add(sample.getAbsolutePath());
        }
        command.add("-o");
        command.add(dictOutputPath);

        System.out.println("Invoking zstd CLI training command...");
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            cleanUp(tempDir, sampleFiles);
            throw new IOException("Failed to start 'zstd'. Ensure Zstandard CLI is installed and in your PATH. Details: " + e.getMessage(), e);
        }

        // Read error stream from process to print output logs
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                System.out.println("[zstd-train-log] " + line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("zstd dictionary training failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Training process wait interrupted", e);
        } finally {
            cleanUp(tempDir, sampleFiles);
        }

        System.out.println("Dictionary successfully trained and written to: " + dictOutputPath);
    }

    private void cleanUp(File tempDir, List<File> files) {
        for (File file : files) {
            file.delete();
        }
        tempDir.delete();
    }
}
