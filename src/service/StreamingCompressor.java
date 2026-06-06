package service;

import util.FileManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPOutputStream;

/**
 * Implements streaming compression for multiple codecs (GZIP, ZSTD, BROTLI, BZ2, LZMA)
 * and packages them with a .dfc manifest format.
 */
public class StreamingCompressor {

    /**
     * Compresses a source file to a .dfc package using the specified codec and level.
     *
     * @param sourcePath the file to compress
     * @param destPath   the output .dfc file
     * @param codec      the codec to use (GZIP, ZSTD, BROTLI, BZ2, LZMA, STORE)
     * @param level      the compression level (0-9)
     * @throws IOException if any I/O error occurs
     */
    public void compress(String sourcePath, String destPath, String codec, int level) throws IOException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source file not found: " + sourcePath);
        }

        System.out.println("Calculating SHA-256 checksum of source file...");
        String sha256 = calculateSHA256(sourceFile);

        // Create a temporary file to hold the compressed payload stream
        File tempFile = File.createTempFile("dfc_payload_", ".tmp");
        tempFile.deleteOnExit();

        System.out.println("Initiating streaming compressor: " + codec + " (Level " + level + ")");
        try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(tempFile));
             OutputStream compressorStream = getCompressorStream(codec, level, fos)) {
            
            // Stream content from source file into the compressor stream
            try (InputStream fis = new BufferedInputStream(new FileInputStream(sourceFile))) {
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    compressorStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            tempFile.delete();
            throw new IOException("Error during streaming compression: " + e.getMessage(), e);
        }

        long compressedSize = tempFile.length();
        System.out.println("Payload compression complete. Packed size: " + compressedSize + " bytes.");

        // Generate JSON Manifest metadata
        String manifestJson = String.format(
            "{\n" +
            "  \"magic_number\": \"DFC_V1\",\n" +
            "  \"original_file\": {\n" +
            "    \"name\": \"%s\",\n" +
            "    \"size_bytes\": %d,\n" +
            "    \"checksum_sha256\": \"%s\"\n" +
            "  },\n" +
            "  \"compression\": {\n" +
            "    \"strategy_applied\": \"%s\",\n" +
            "    \"level\": %d,\n" +
            "    \"compressed_size_bytes\": %d\n" +
            "  }\n" +
            "}",
            sourceFile.getName(), sourceFile.length(), sha256, codec.toUpperCase(), level, compressedSize
        );

        byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);

        // Pack manifest and compressed payload together into the final .dfc file
        System.out.println("Writing final .dfc package: " + destPath);
        try (FileOutputStream fos = new FileOutputStream(destPath);
             DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(fos))) {
            
            // 1. Write DFC magic bytes
            dos.writeBytes("DFC1");

            // 2. Write JSON Manifest size (4 bytes)
            dos.writeInt(manifestBytes.length);

            // 3. Write JSON Manifest string content
            dos.write(manifestBytes);

            // 4. Write compressed payload from temp file
            try (InputStream tempFis = new BufferedInputStream(new FileInputStream(tempFile))) {
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = tempFis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            // Clean up the temporary payload file
            tempFile.delete();
        }

        System.out.println("Package finalized successfully.");
    }

    /**
     * Resolves the correct OutputStream filter based on selected codec.
     */
    private OutputStream getCompressorStream(String codec, int level, OutputStream targetStream) throws IOException {
        String cleanCodec = codec.toUpperCase();
        switch (cleanCodec) {
            case "GZIP":
                return new GZIPOutputStream(targetStream);
            case "ZSTD":
                return runCLICompressor(new String[]{"zstd", "-c", "-q", "-" + level}, targetStream);
            case "BROTLI":
                return runCLICompressor(new String[]{"brotli", "-c", "-q", String.valueOf(level)}, targetStream);
            case "BZ2":
                return runCLICompressor(new String[]{"bzip2", "-c", "-q", "-" + level}, targetStream);
            case "LZMA":
                return runCLICompressor(new String[]{"lzma", "-c", "-q", "-" + level}, targetStream);
            case "STORE":
                return targetStream;
            default:
                throw new IllegalArgumentException("Unsupported codec for streaming: " + codec);
        }
    }

    /**
     * Spawns an external CLI command as a streaming pipe filter.
     */
    private OutputStream runCLICompressor(String[] command, OutputStream targetStream) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException("Failed to start CLI tool '" + command[0] + "'. Ensure it is installed and added to PATH. Details: " + e.getMessage(), e);
        }

        // Thread to pipe process standard output directly into target stream
        Thread stdoutReader = new Thread(() -> {
            try (InputStream is = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    targetStream.write(buf, 0, len);
                }
            } catch (IOException e) {
                System.err.println("Error reading CLI stdout: " + e.getMessage());
            }
        });
        stdoutReader.start();

        // Custom output stream to pipe inputs to process stdin
        return new OutputStream() {
            private final OutputStream os = process.getOutputStream();

            @Override
            public void write(int b) throws IOException {
                os.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                os.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                os.flush();
            }

            @Override
            public void close() throws IOException {
                os.close();
                try {
                    int exitCode = process.waitFor();
                    stdoutReader.join();
                    if (exitCode != 0) {
                        throw new IOException("CLI tool '" + command[0] + "' exited with error code: " + exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Piping process wait interrupted", e);
                }
            }
        };
    }

    /**
     * Computes the SHA-256 hash representation of a file.
     */
    private String calculateSHA256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 digest algorithm not found", e);
        }
    }
}
