package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility class for reading and writing files.
 */
public class FileManager {

    /**
     * Reads the entire content of a file as a String.
     * Useful for text file analysis.
     *
     * @param filePath the path to the file
     * @return the file content as a String
     * @throws IOException if an I/O error occurs
     */
    public static String readFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    /**
     * Reads the entire content of a file as a byte array.
     * Essential for binary files (images, audio, compressed files).
     *
     * @param filePath the path to the file
     * @return the file content as a byte array
     * @throws IOException if an I/O error occurs
     */
    public static byte[] readFileAsBytes(String filePath) throws IOException {
        return Files.readAllBytes(Paths.get(filePath));
    }

    /**
     * Writes String content to a file.
     *
     * @param filePath the path to the file
     * @param content  the string content to write
     * @throws IOException if an I/O error occurs
     */
    public static void writeFile(String filePath, String content) throws IOException {
        Files.write(Paths.get(filePath), content.getBytes());
    }

    /**
     * Writes raw bytes to a file.
     *
     * @param filePath the path to the file
     * @param data     the byte array to write
     * @throws IOException if an I/O error occurs
     */
    public static void writeFileAsBytes(String filePath, byte[] data) throws IOException {
        Files.write(Paths.get(filePath), data);
    }
}
