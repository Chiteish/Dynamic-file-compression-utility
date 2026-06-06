package service;

import model.HuffmanTree;
import util.FileManager;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the logic for compressing files using Huffman coding.
 */
public class Compressor {

    /**
     * Builds a frequency table of characters in the given input text.
     *
     * @param text the input string to analyze
     * @return a map of characters to their frequency counts
     */
    public Map<Character, Integer> buildFrequencyTable(String text) {
        Map<Character, Integer> frequencyMap = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return frequencyMap;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            frequencyMap.put(c, frequencyMap.getOrDefault(c, 0) + 1);
        }
        return frequencyMap;
    }

    /**
     * Compresses the specified source file and writes the compressed data to the destination file.
     *
     * @param sourcePath the path to the file to be compressed
     * @param destPath   the path to write the compressed file to
     * @throws IOException if an I/O error occurs during compression
     */
    public void compress(String sourcePath, String destPath) throws IOException {
        System.out.println("Reading source file: " + sourcePath);
        String content = FileManager.readFile(sourcePath);

        System.out.println("Building frequency table...");
        Map<Character, Integer> frequencies = buildFrequencyTable(content);

        System.out.println("Building Huffman Tree and generating prefix codes...");
        HuffmanTree tree = new HuffmanTree();
        tree.build(frequencies);
        Map<Character, String> prefixCodes = tree.getPrefixCodes();

        // Convert the input text into a bit sequence representation
        System.out.println("Encoding content to bit sequence...");
        StringBuilder bitStream = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            String code = prefixCodes.get(c);
            if (code == null) {
                throw new IllegalStateException("No prefix code found for character: " + c);
            }
            bitStream.append(code);
        }

        String bits = bitStream.toString();
        int bitLength = bits.length();

        // Pack the bit string into raw byte array
        System.out.println("Packing bits into bytes (total bits: " + bitLength + ")...");
        int byteLength = (int) Math.ceil((double) bitLength / 8);
        byte[] packedBytes = new byte[byteLength];
        
        for (int i = 0; i < bitLength; i++) {
            if (bits.charAt(i) == '1') {
                int byteIndex = i / 8;
                int bitOffset = 7 - (i % 8); // Store MSB first
                packedBytes[byteIndex] |= (1 << bitOffset);
            }
        }

        // Write the custom structured binary file
        System.out.println("Writing compressed binary output to: " + destPath);
        try (FileOutputStream fos = new FileOutputStream(destPath);
             DataOutputStream dos = new DataOutputStream(fos)) {
            
            // 1. Header: Write number of entries in the frequency map
            dos.writeInt(frequencies.size());

            // 2. Header: Write the frequency table
            for (Map.Entry<Character, Integer> entry : frequencies.entrySet()) {
                dos.writeChar(entry.getKey());
                dos.writeInt(entry.getValue());
            }

            // 3. Header: Write the total bit length of the payload (assists in padding extraction)
            dos.writeInt(bitLength);

            // 4. Payload: Write packed bytes
            dos.write(packedBytes);
        }
        
        System.out.println("Compression completed successfully.");
    }
}
