import service.Compressor;
import service.StreamingCompressor;
import service.StreamingDecompressor;
import service.ZstdDictionaryTrainer;
import service.TarZstdCompressor;
import service.StrategyChooser;
import service.StrategyConfig;
import model.HuffmanTree;
import util.Detector;
import util.DetectionResult;
import util.FileManager;

import java.io.IOException;
import java.util.Map;

/**
 * Unified CLI entry point for the Dynamic File Compression (DFC) utility.
 */
public class DfcCLI {
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            return;
        }

        String subcommand = args[0].toLowerCase();

        try {
            switch (subcommand) {
                case "codes":
                    handleCodes(args);
                    break;
                case "compress":
                    handleCompress(args);
                    break;
                case "decompress":
                    handleDecompress(args);
                    break;
                case "verify":
                    handleVerify(args);
                    break;
                case "train-dict":
                    handleTrainDict(args);
                    break;
                case "tar-zstd":
                    handleTarZstd(args);
                    break;
                default:
                    System.err.println("Error: Unknown subcommand: " + subcommand);
                    printUsage();
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid numeric parameter: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleCodes(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Error: Missing parameters. Usage: codes <file_path>");
            return;
        }
        String filePath = args[1];
        System.out.println("Reading file for Huffman analysis: " + filePath);
        String content = FileManager.readFile(filePath);

        Compressor comp = new Compressor();
        Map<Character, Integer> frequencies = comp.buildFrequencyTable(content);

        System.out.println("\n1. Character Frequencies Table:");
        System.out.println("-----------------------------------------");
        frequencies.forEach((c, freq) -> {
            System.out.printf("Character: %-6s | Frequency: %d\n", escapeChar(c), freq);
        });
        System.out.println("-----------------------------------------");

        System.out.println("\n2. Building Huffman Tree...");
        HuffmanTree tree = new HuffmanTree();
        tree.build(frequencies);

        System.out.println("\n3. Generated Huffman Codes:");
        System.out.println("-----------------------------------------");
        Map<Character, String> prefixCodes = tree.getPrefixCodes();
        prefixCodes.forEach((c, code) -> {
            System.out.printf("Character: %-6s | Huffman Code: %s\n", escapeChar(c), code);
        });
        System.out.println("-----------------------------------------");
    }

    private static void handleCompress(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Error: Missing parameters. Usage: compress <source_file> <dest_file> [--mode FAST|BALANCED|MAX_COMPRESSION]");
            return;
        }

        String source = args[1];
        String dest = args[2];
        String mode = "BALANCED";

        // Parse optional --mode parameter
        for (int i = 3; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--mode") && i + 1 < args.length) {
                mode = args[i + 1].toUpperCase();
                break;
            }
        }

        System.out.println("Running DFC Analysis on: " + source);
        DetectionResult detection = Detector.detect(source);
        System.out.println("  MIME Detected: " + detection.getMimeGuess());
        System.out.println("  Entropy Score: " + String.format("%.4f", detection.getEntropy()));

        System.out.println("Selecting optimal strategy using mode: " + mode);
        StrategyConfig strategy = StrategyChooser.choose(detection.getMimeGuess(), detection.getEntropy(), mode);
        System.out.println("  Elected Codec:   " + strategy.getCodec());
        System.out.println("  Elected Level:   " + strategy.getLevel());
        System.out.println("  Elected Threads: " + strategy.getThreads());

        System.out.println("\nExecuting compression streaming package...");
        new StreamingCompressor().compress(source, dest, strategy.getCodec(), strategy.getLevel());
        System.out.println("Compressed file saved at: " + dest);
    }

    private static void handleDecompress(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Error: Missing parameters. Usage: decompress <dfc_file> <dest_file>");
            return;
        }
        String source = args[1];
        String dest = args[2];
        new StreamingDecompressor().decompress(source, dest);
    }

    private static void handleVerify(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Error: Missing parameters. Usage: verify <dfc_file>");
            return;
        }
        String source = args[1];
        new StreamingDecompressor().verify(source);
    }

    private static void handleTrainDict(String[] args) throws IOException {
        if (args.length < 5) {
            System.err.println("Error: Missing parameters. Usage: train-dict <source_dataset> <dict_output_path> <chunk_size> <max_samples>");
            return;
        }
        String source = args[1];
        String dest = args[2];
        int chunkSize = Integer.parseInt(args[3]);
        int maxSamples = Integer.parseInt(args[4]);
        new ZstdDictionaryTrainer().train(source, dest, chunkSize, maxSamples);
    }

    private static void handleTarZstd(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Error: Missing parameters. Usage: tar-zstd <folder_path> <dest_file> [--level 1-19]");
            return;
        }
        String sourceFolder = args[1];
        String destFile = args[2];
        int level = 3;

        for (int i = 3; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--level") && i + 1 < args.length) {
                level = Integer.parseInt(args[i + 1]);
                break;
            }
        }

        new TarZstdCompressor().compressFolder(sourceFolder, destFile, level);
    }

    private static String escapeChar(char c) {
        switch (c) {
            case '\n': return "\\n";
            case '\r': return "\\r";
            case '\t': return "\\t";
            case ' ':  return "' '";
            default:   return String.valueOf(c);
        }
    }

    private static void printUsage() {
        System.out.println("=========================================");
        System.out.println("     Dynamic File Compression (DFC)      ");
        System.out.println("=========================================");
        System.out.println("Usage:");
        System.out.println("  java DfcCLI <subcommand> [arguments...]");
        System.out.println("\nSubcommands:");
        System.out.println("  codes        <file_path>");
        System.out.println("               Prints the file character frequencies and Huffman codes.");
        System.out.println("  compress     <source_file> <dest_file> [--mode FAST|BALANCED|MAX_COMPRESSION]");
        System.out.println("               Runs detection, chooses strategy, compresses dynamically.");
        System.out.println("  decompress   <dfc_file> <dest_file>");
        System.out.println("               Decompresses package back to original.");
        System.out.println("  verify       <dfc_file>");
        System.out.println("               Verifies checksum and size integrity without final write.");
        System.out.println("  train-dict   <source_dataset> <dict_output_path> <chunk_size> <max_samples>");
        System.out.println("               Samples chunks from dataset and trains a Zstd dictionary.");
        System.out.println("  tar-zstd     <folder_path> <dest_file> [--level 1-19]");
        System.out.println("               Pipes recursive tar archive to Zstd compression package.");
    }
}
