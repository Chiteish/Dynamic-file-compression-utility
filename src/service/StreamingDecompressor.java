package service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Handles decompression of .dfc packages, parsing manifest details,
 * decompressing payloads, and verifying SHA-256 integrity.
 */
public class StreamingDecompressor {

    /**
     * Decompresses a .dfc package and validates its SHA-256 checksum.
     *
     * @param sourcePath path to the .dfc file
     * @param destPath   path to write the decompressed file to
     * @throws IOException if any I/O error or verification failure occurs
     */
    public void decompress(String sourcePath, String destPath) throws IOException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Compressed .dfc file not found: " + sourcePath);
        }

        System.out.println("Reading DFC package: " + sourcePath);
        String manifestJson;
        byte[] compressedPayload;

        try (FileInputStream fis = new FileInputStream(sourceFile);
             DataInputStream dis = new DataInputStream(new BufferedInputStream(fis))) {
            
            // 1. Read and verify DFC magic signature
            byte[] magic = new byte[4];
            dis.readFully(magic);
            String magicStr = new String(magic, StandardCharsets.UTF_8);
            if (!magicStr.equals("DFC1")) {
                throw new IOException("Invalid DFC package: magic signature mismatch (expected 'DFC1')");
            }

            // 2. Read JSON Manifest size
            int manifestSize = dis.readInt();

            // 3. Read JSON Manifest string content
            byte[] manifestBytes = new byte[manifestSize];
            dis.readFully(manifestBytes);
            manifestJson = new String(manifestBytes, StandardCharsets.UTF_8);

            // 4. Read remaining compressed payload
            long payloadSize = sourceFile.length() - 4 - 4 - manifestSize;
            compressedPayload = new byte[(int) payloadSize];
            dis.readFully(compressedPayload);
        }

        // Parse Manifest values using light regex patterns
        System.out.println("Parsing Manifest Metadata...");
        String originalName = extractJsonField(manifestJson, "name");
        long expectedSize = Long.parseLong(extractJsonField(manifestJson, "size_bytes"));
        String expectedSha256 = extractJsonField(manifestJson, "checksum_sha256");
        String strategy = extractJsonField(manifestJson, "strategy_applied");

        System.out.println("Manifest Details:");
        System.out.println("  Original Name: " + originalName);
        System.out.println("  Expected Size: " + expectedSize + " bytes");
        System.out.println("  Expected Hash: " + expectedSha256);
        System.out.println("  Codec Applied: " + strategy);

        // Decompress compressed payload to output file
        System.out.println("Initiating streaming decompressor for codec: " + strategy);
        File tempOutFile = File.createTempFile("dfc_decomp_", ".tmp");
        tempOutFile.deleteOnExit();

        try {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedPayload);
                 InputStream decompressorStream = getDecompressorStream(strategy, bais);
                 OutputStream fos = new BufferedOutputStream(new FileOutputStream(tempOutFile))) {
                
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = decompressorStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            // Verify Integrity (SHA-256 validation)
            System.out.println("Verifying SHA-256 integrity of decompressed file...");
            String actualSha256 = calculateSHA256(tempOutFile);
            long actualSize = tempOutFile.length();

            if (actualSize != expectedSize) {
                throw new IOException(String.format("Integrity Failure: Size mismatch! Expected %d bytes, got %d bytes.", expectedSize, actualSize));
            }

            if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                throw new IOException(String.format("Integrity Failure: SHA-256 mismatch!\nExpected: %s\nActual:   %s", expectedSha256, actualSha256));
            }

            // Success: Move temporary file to final destination
            System.out.println("Verification SUCCESS! Checksums match original file.");
            System.out.println("Writing restored file to: " + destPath);
            if (new File(destPath).exists()) {
                new File(destPath).delete();
            }
            Files.move(tempOutFile.toPath(), Paths.get(destPath));

        } catch (IOException e) {
            tempOutFile.delete();
            throw new IOException("Decompression / Verification failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the integrity of a .dfc package without writing to a final destination file.
     *
     * @param sourcePath path to the .dfc file
     * @throws IOException if validation fails or file reading errors occur
     */
    public void verify(String sourcePath) throws IOException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Compressed .dfc file not found: " + sourcePath);
        }

        System.out.println("Verifying DFC package: " + sourcePath);
        String manifestJson;
        byte[] compressedPayload;

        try (FileInputStream fis = new FileInputStream(sourceFile);
             DataInputStream dis = new DataInputStream(new BufferedInputStream(fis))) {
            
            byte[] magic = new byte[4];
            dis.readFully(magic);
            String magicStr = new String(magic, StandardCharsets.UTF_8);
            if (!magicStr.equals("DFC1")) {
                throw new IOException("Invalid DFC package: magic signature mismatch (expected 'DFC1')");
            }

            int manifestSize = dis.readInt();
            byte[] manifestBytes = new byte[manifestSize];
            dis.readFully(manifestBytes);
            manifestJson = new String(manifestBytes, StandardCharsets.UTF_8);

            long payloadSize = sourceFile.length() - 4 - 4 - manifestSize;
            compressedPayload = new byte[(int) payloadSize];
            dis.readFully(compressedPayload);
        }

        String originalName = extractJsonField(manifestJson, "name");
        long expectedSize = Long.parseLong(extractJsonField(manifestJson, "size_bytes"));
        String expectedSha256 = extractJsonField(manifestJson, "checksum_sha256");
        String strategy = extractJsonField(manifestJson, "strategy_applied");

        System.out.println("Manifest Details:");
        System.out.println("  Original Name: " + originalName);
        System.out.println("  Expected Size: " + expectedSize + " bytes");
        System.out.println("  Expected Hash: " + expectedSha256);
        System.out.println("  Codec Applied: " + strategy);

        File tempOutFile = File.createTempFile("dfc_verify_", ".tmp");
        tempOutFile.deleteOnExit();

        try {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedPayload);
                 InputStream decompressorStream = getDecompressorStream(strategy, bais);
                 OutputStream fos = new BufferedOutputStream(new FileOutputStream(tempOutFile))) {
                
                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = decompressorStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            String actualSha256 = calculateSHA256(tempOutFile);
            long actualSize = tempOutFile.length();

            if (actualSize != expectedSize) {
                System.err.println("Verification FAILED: File size mismatch! Expected " + expectedSize + ", got " + actualSize);
                return;
            }

            if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                System.err.println("Verification FAILED: SHA-256 hash mismatch!");
                System.err.println("  Expected: " + expectedSha256);
                System.err.println("  Actual:   " + actualSha256);
                return;
            }

            System.out.println("Verification SUCCESS: Checksums and file size validate perfectly!");

        } finally {
            tempOutFile.delete();
        }
    }

    /**
     * Resolves the correct decompression stream filter.
     */
    private InputStream getDecompressorStream(String codec, InputStream sourceStream) throws IOException {
        String cleanCodec = codec.toUpperCase();
        switch (cleanCodec) {
            case "GZIP":
                return new GZIPInputStream(sourceStream);
            case "ZSTD":
                return runCLIDecompressor(new String[]{"zstd", "-d", "-c", "-q"}, sourceStream);
            case "BROTLI":
                return runCLIDecompressor(new String[]{"brotli", "-d", "-c", "-q"}, sourceStream);
            case "BZ2":
                return runCLIDecompressor(new String[]{"bzip2", "-d", "-c", "-q"}, sourceStream);
            case "LZMA":
                return runCLIDecompressor(new String[]{"lzma", "-d", "-c", "-q"}, sourceStream);
            case "STORE":
                return sourceStream;
            default:
                throw new IllegalArgumentException("Unsupported codec for decompression: " + codec);
        }
    }

    /**
     * Spawns a CLI tool to decompress input stream dynamically.
     */
    private InputStream runCLIDecompressor(String[] command, InputStream sourceStream) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException("Failed to start CLI decompressor '" + command[0] + "'. Ensure it is in your PATH. Details: " + e.getMessage(), e);
        }

        // Thread to read from sourceStream and write directly into CLI stdin
        Thread stdinWriter = new Thread(() -> {
            try (OutputStream os = process.getOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = sourceStream.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
            } catch (IOException e) {
                System.err.println("Error writing to CLI stdin: " + e.getMessage());
            }
        });
        stdinWriter.start();

        // Wrap stdout of process as our readable decompressor input stream
        return new InputStream() {
            private final InputStream is = process.getInputStream();

            @Override
            public int read() throws IOException {
                return is.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return is.read(b, off, len);
            }

            @Override
            public void close() throws IOException {
                is.close();
                try {
                    stdinWriter.join();
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new IOException("CLI tool '" + command[0] + "' exited with error code: " + exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Decompression process wait interrupted", e);
                }
            }
        };
    }

    /**
     * Helper to extract field values from JSON string block.
     */
    private String extractJsonField(String json, String fieldName) {
        String patternStr = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"";
        Pattern p = Pattern.compile(patternStr);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        // Try number formatting matching
        patternStr = "\"" + fieldName + "\"\\s*:\\s*([0-9]+)";
        p = Pattern.compile(patternStr);
        m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    /**
     * Calculates SHA-256 hash representation of a file.
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
