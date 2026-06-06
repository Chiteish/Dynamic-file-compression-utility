package service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Packs a directory structure into a POSIX tar stream and compresses it
 * dynamically using Zstandard, packaging it inside a .dfc archive.
 */
public class TarZstdCompressor {

    /**
     * Compresses a folder to a .dfc package using a tar-to-zstd streaming pipeline.
     *
     * @param folderPath path to the directory to compress
     * @param destPath   path to the output .dfc file
     * @param level      Zstd compression level (1-19)
     * @throws IOException if any I/O or CLI utility error occurs
     */
    public void compressFolder(String folderPath, String destPath, int level) throws IOException {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory path: " + folderPath);
        }

        String folderName = folder.getName();
        File parentDir = folder.getParentFile();
        String parentPath = (parentDir == null) ? "." : parentDir.getAbsolutePath();

        System.out.println("Analyzing directory contents: " + folderPath);
        long totalSize = 0;
        List<String> fileList = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(Paths.get(folderPath))) {
            List<Path> pathList = paths.filter(Files::isRegularFile).toList();
            for (Path p : pathList) {
                totalSize += p.toFile().length();
                // Add relative path inside the tar archive
                fileList.add(Paths.get(folderPath).getParent().relativize(p).toString().replace('\\', '/'));
            }
        }

        System.out.println("  Total files:  " + fileList.size());
        System.out.println("  Total size:   " + totalSize + " bytes");

        // Temporary file to hold the compressed tar.zst payload
        File tempFile = File.createTempFile("dfc_tarzst_", ".tmp");
        tempFile.deleteOnExit();

        System.out.println("Spawning tar-to-zstd pipeline (tar | zstd)...");

        // Command for tar: output raw tar stream to stdout (-cf -) relative to parent (-C)
        ProcessBuilder tarBuilder = new ProcessBuilder("tar", "-cf", "-", "-C", parentPath, folderName);
        
        // Command for zstd: read from stdin, output compressed stream to stdout (-c)
        ProcessBuilder zstdBuilder = new ProcessBuilder("zstd", "-c", "-q", "-" + level);

        Process tarProcess;
        Process zstdProcess;

        try {
            tarProcess = tarBuilder.start();
        } catch (IOException e) {
            tempFile.delete();
            throw new IOException("Failed to start 'tar' command. Ensure it is in your PATH. Details: " + e.getMessage(), e);
        }

        try {
            zstdProcess = zstdBuilder.start();
        } catch (IOException e) {
            tarProcess.destroy();
            tempFile.delete();
            throw new IOException("Failed to start 'zstd' command. Ensure it is in your PATH. Details: " + e.getMessage(), e);
        }

        // Pipe tar process standard output to zstd process standard input
        Thread pipeThread = new Thread(() -> {
            try (InputStream tarStdout = tarProcess.getInputStream();
                 OutputStream zstdStdin = zstdProcess.getOutputStream()) {
                
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = tarStdout.read(buffer)) != -1) {
                    zstdStdin.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                System.err.println("Pipeline piping error (tar -> zstd): " + e.getMessage());
            }
        });
        pipeThread.start();

        // Write zstd standard output to the temporary payload file
        try (InputStream zstdStdout = zstdProcess.getInputStream();
             OutputStream fos = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            
            byte[] buffer = new byte[16384];
            int bytesRead;
            while ((bytesRead = zstdStdout.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

        try {
            // Wait for both subprocesses to complete
            int tarExit = tarProcess.waitFor();
            int zstdExit = zstdProcess.waitFor();
            pipeThread.join();

            if (tarExit != 0) {
                throw new IOException("tar process failed with exit code: " + tarExit);
            }
            if (zstdExit != 0) {
                throw new IOException("zstd process failed with exit code: " + zstdExit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tempFile.delete();
            throw new IOException("Compression pipeline interrupted", e);
        }

        long compressedSize = tempFile.length();
        System.out.println("Pipeline completed. Compressed archive size: " + compressedSize + " bytes.");

        // Generate JSON Manifest Metadata
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n")
                   .append("  \"magic_number\": \"DFC_V1\",\n")
                   .append("  \"original_folder\": {\n")
                   .append("    \"name\": \"").append(folderName).append("\",\n")
                   .append("    \"total_size_bytes\": ").append(totalSize).append(",\n")
                   .append("    \"files_count\": ").append(fileList.size()).append("\n")
                   .append("  },\n")
                   .append("  \"compression\": {\n")
                   .append("    \"strategy_applied\": \"TAR-ZSTD\",\n")
                   .append("    \"level\": ").append(level).append(",\n")
                   .append("    \"compressed_size_bytes\": ").append(compressedSize).append("\n")
                   .append("  }\n")
                   .append("}");

        byte[] manifestBytes = jsonBuilder.toString().getBytes(StandardCharsets.UTF_8);

        // Package manifest and payload into final DFC archive
        System.out.println("Writing final DFC package: " + destPath);
        try (FileOutputStream fos = new FileOutputStream(destPath);
             DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(fos))) {
            
            dos.writeBytes("DFC1");
            dos.writeInt(manifestBytes.length);
            dos.write(manifestBytes);

            try (InputStream tempFis = new BufferedInputStream(new FileInputStream(tempFile))) {
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = tempFis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            tempFile.delete();
        }

        System.out.println("Directory compression package created successfully.");
    }
}
