package model;

/**
 * Represents a node in the Huffman Tree.
 */
public class Node implements Comparable<Node> {
    private final Character character;
    private final int frequency;
    private final Node left;
    private final Node right;

    /**
     * Constructor for a leaf node.
     *
     * @param character the character represented by this node
     * @param frequency the frequency of occurrence of the character
     */
    public Node(char character, int frequency) {
        this.character = character;
        this.frequency = frequency;
        this.left = null;
        this.right = null;
    }

    /**
     * Constructor for an internal/parent node.
     *
     * @param frequency the combined frequency of the children
     * @param left      the left child node
     * @param right     the right child node
     */
    public Node(int frequency, Node left, Node right) {
        this.character = null;
        this.frequency = frequency;
        this.left = left;
        this.right = right;
    }

    public Character getCharacter() {
        return character;
    }

    public int getFrequency() {
        return frequency;
    }

    public Node getLeft() {
        return left;
    }

    public Node getRight() {
        return right;
    }

    public boolean isLeaf() {
        return left == null && right == null;
    }

    @Override
    public int compareTo(Node other) {
        // Natural ordering based on frequency (ascending) for Min-Heap
        return Integer.compare(this.frequency, other.frequency);
    }
}
