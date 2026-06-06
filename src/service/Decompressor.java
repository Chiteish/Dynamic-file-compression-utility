package service;

import model.HuffmanTree;
import model.Node;
import util.FileManager;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the logic for decompressing files that were compressed using Huffman coding.
 */
public class Decompressor {

    /**
     * Decompresses the specified source file and writes the decompressed data to the destination file.
     *
     * @param sourcePath the path to the compressed file
     * @param destPath   the path to write the decompressed file to
     * @throws IOException if an I/O error occurs during decompression
     */
    public void decompress(String sourcePath, String destPath) throws IOException {
        System.out.println("Reading compressed binary file: " + sourcePath);
        
        Map<Character, Integer> frequencies = new HashMap<>();
        int bitLength = 0;
        byte[] packedBytes;

        try (FileInputStream fis = new FileInputStream(sourcePath);
             DataInputStream dis = new DataInputStream(fis)) {
            
            // 1. Read frequency table size
            int tableSize = dis.readInt();
            System.out.println("Reconstructing frequency table (unique characters: " + tableSize + ")...");

            // 2. Read frequency table entries
            for (int i = 0; i < tableSize; i++) {
                char c = dis.readChar();
                int freq = dis.readInt();
                frequencies.put(c, freq);
            }

            // 3. Read total bit length
            bitLength = dis.readInt();
            System.out.println("Payload bit length: " + bitLength);

            // 4. Read packed bytes
            int byteLength = (int) Math.ceil((double) bitLength / 8);
            packedBytes = new byte[byteLength];
            dis.readFully(packedBytes);
        }

        // Handle empty file case
        if (frequencies.isEmpty() || bitLength == 0) {
            System.out.println("File is empty. Writing empty file to: " + destPath);
            FileManager.writeFile(destPath, "");
            return;
        }

        System.out.println("Reconstructing Huffman Tree...");
        HuffmanTree tree = new HuffmanTree();
        tree.build(frequencies);
        Node root = tree.getRoot();

        if (root == null) {
            throw new IllegalStateException("Failed to reconstruct tree: Root node is null.");
        }

        System.out.println("Decoding bit sequence...");
        StringBuilder decodedText = new StringBuilder();
        Node current = root;

        for (int i = 0; i < bitLength; i++) {
            int byteIndex = i / 8;
            int bitOffset = 7 - (i % 8);
            int bit = (packedBytes[byteIndex] >> bitOffset) & 1;

            // Traverse the tree: 0 goes left, 1 goes right
            if (bit == 0) {
                current = current.getLeft();
            } else {
                current = current.getRight();
            }

            if (current == null) {
                throw new IllegalStateException("Invalid compression stream: Traversal reached a null node.");
            }

            // Leaf node represents a decoded character
            if (current.isLeaf()) {
                decodedText.append(current.getCharacter());
                current = root; // Reset traversal to root for the next character
            }
        }

        System.out.println("Writing decompressed file to: " + destPath);
        FileManager.writeFile(destPath, decodedText.toString());
        System.out.println("Decompression completed successfully.");
    }
}
