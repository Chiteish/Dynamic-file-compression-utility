package model;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Manages the construction of the Huffman Tree and prefix code generation.
 */
public class HuffmanTree {
    private Node root;
    private final Map<Character, String> prefixCodes;

    public HuffmanTree() {
        this.prefixCodes = new HashMap<>();
    }

    /**
     * Builds the Huffman Tree from the given character frequencies and generates prefix codes.
     *
     * @param frequencies a map of characters to their frequencies
     */
    public void build(Map<Character, Integer> frequencies) {
        prefixCodes.clear();
        if (frequencies == null || frequencies.isEmpty()) {
            this.root = null;
            return;
        }

        PriorityQueue<Node> pq = new PriorityQueue<>();

        // Create leaf nodes and add them to the priority queue
        for (Map.Entry<Character, Integer> entry : frequencies.entrySet()) {
            pq.add(new Node(entry.getKey(), entry.getValue()));
        }

        // Special boundary case: only one unique character in the input
        if (pq.size() == 1) {
            Node singleNode = pq.poll();
            // Create a dummy internal root node so traversal functions correctly
            this.root = new Node(singleNode.getFrequency(), singleNode, null);
        } else {
            // Merge nodes step-by-step
            while (pq.size() > 1) {
                Node left = pq.poll();
                Node right = pq.poll();
                
                // Create a parent internal node with combined frequencies
                Node parent = new Node(left.getFrequency() + right.getFrequency(), left, right);
                pq.add(parent);
            }
            this.root = pq.poll();
        }

        // Generate the prefix codes from the built tree
        generatePrefixCodes();
    }

    /**
     * Helper to start recursive code generation traversal.
     */
    private void generatePrefixCodes() {
        generateCodesRecursive(root, "");
    }

    /**
     * Recurse down the tree. Left adds "0", right adds "1".
     */
    private void generateCodesRecursive(Node node, String code) {
        if (node == null) {
            return;
        }

        // Leaf node reached
        if (node.isLeaf()) {
            // If the tree is built with a single character, code might be empty.
            // But since we created a dummy parent node, it traverses left and gets "0"
            prefixCodes.put(node.getCharacter(), code);
            return;
        }

        generateCodesRecursive(node.getLeft(), code + "0");
        generateCodesRecursive(node.getRight(), code + "1");
    }

    /**
     * Gets the root of the constructed Huffman Tree.
     *
     * @return the root Node, or null if the tree is not built
     */
    public Node getRoot() {
        return root;
    }

    /**
     * Gets the generated prefix codes for each character.
     *
     * @return the prefix codes map
     */
    public Map<Character, String> getPrefixCodes() {
        return prefixCodes;
    }
}
