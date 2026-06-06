import service.Compressor;
import service.Decompressor;
import service.StatisticsService;
import service.StrategyChooser;
import service.StrategyConfig;
import service.StreamingCompressor;
import service.StreamingDecompressor;
import service.ZstdDictionaryTrainer;
import util.Detector;
import util.DetectionResult;

import java.io.IOException;

/**
 * Main entry point for the Dynamic File Compression Utility CLI.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("   Dynamic File Compression Utility CLI  ");
        System.out.println("=========================================");

        if (args.length < 2) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase();

        try {
            switch (mode) {
                case "detect":
                case "-t":
                    DetectorResultOutput(args[1]);
                    break;
                case "choose":
                case "-o":
                    if (args.length < 4) {
                        System.err.println("Error: Missing parameters. Usage: java Main choose <mime> <entropy> <mode>");
                        printUsage();
                        return;
                    }
                    String mimeParam = args[1];
                    double entropyParam = Double.parseDouble(args[2]);
                    String modeParam = args[3];
                    StrategyConfig config = StrategyChooser.choose(mimeParam, entropyParam, modeParam);
                    System.out.println(config);
                    break;
                case "compress":
                case "-c":
                    if (args.length < 3) {
                        System.err.println("Error: Missing destination file path.");
                        printUsage();
                        return;
                    }
                    new Compressor().compress(args[1], args[2]);
                    break;
                case "decompress":
                case "-d":
                    if (args.length < 3) {
                        System.err.println("Error: Missing destination file path.");
                        printUsage();
                        return;
                    }
                    new Decompressor().decompress(args[1], args[2]);
                    break;
                case "scompress":
                case "-sc":
                    if (args.length < 5) {
                        System.err.println("Error: Missing parameters. Usage: java Main scompress <source_file> <dest_file> <codec> <level>");
                        printUsage();
                        return;
                    }
                    String scSource = args[1];
                    String scDest = args[2];
                    String scCodec = args[3];
                    int scLevel = Integer.parseInt(args[4]);
                    new StreamingCompressor().compress(scSource, scDest, scCodec, scLevel);
                    break;
                case "sdecompress":
                case "-sd":
                    if (args.length < 3) {
                        System.err.println("Error: Missing destination file path. Usage: java Main sdecompress <source_file> <dest_file>");
                        printUsage();
                        return;
                    }
                    String sdSource = args[1];
                    String sdDest = args[2];
                    new StreamingDecompressor().decompress(sdSource, sdDest);
                    break;
                case "train":
                case "-tr":
                    if (args.length < 5) {
                        System.err.println("Error: Missing parameters. Usage: java Main train <source_dataset> <dict_output_path> <chunk_size> <max_samples>");
                        printUsage();
                        return;
                    }
                    String trSource = args[1];
                    String trDest = args[2];
                    int trChunkSize = Integer.parseInt(args[3]);
                    int trMaxSamples = Integer.parseInt(args[4]);
                    new ZstdDictionaryTrainer().train(trSource, trDest, trChunkSize, trMaxSamples);
                    break;
                case "stats":
                case "-s":
                    if (args.length < 3) {
                        System.err.println("Error: Missing compressed file path.");
                        printUsage();
                        return;
                    }
                    new StatisticsService().printStatistics(args[1], args[2]);
                    break;
                default:
                    System.err.println("Error: Unknown mode: " + mode);
                    printUsage();
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid numeric value parameter: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Operation failed with IOException: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void DetectorResultOutput(String filePath) throws IOException {
        DetectionResult result = Detector.detect(filePath);
        System.out.println(result);
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java Main <mode> <parameters...>");
        System.out.println("\nModes:");
        System.out.println("  detect, -t     Inspects file magic type, MIME, entropy & text density");
        System.out.println("                 Usage: java Main detect <file_path>");
        System.out.println("  choose, -o     Elector strategy chooser based on characteristics");
        System.out.println("                 Usage: java Main choose <mime> <entropy> <mode>");
        System.out.println("  compress, -c   Compresses a file using static Huffman");
        System.out.println("                 Usage: java Main compress <source_file> <dest_file>");
        System.out.println("  decompress, -d Decompresses a file");
        System.out.println("                 Usage: java Main decompress <source_file> <dest_file>");
        System.out.println("  scompress, -sc Compresses a file using streaming codecs with .dfc manifest");
        System.out.println("                 Usage: java Main scompress <source_file> <dest_file> <codec> <level>");
        System.out.println("  sdecompress, -sd Decompresses a file using streaming codecs and checks SHA-256");
        System.out.println("                 Usage: java Main sdecompress <source_file> <dest_file>");
        System.out.println("  train, -tr     Trains a Zstandard dictionary using dataset samples");
        System.out.println("                 Usage: java Main train <source_dataset> <dict_output_path> <chunk_size> <max_samples>");
        System.out.println("  stats, -s      Displays size differences and savings percentage");
        System.out.println("                 Usage: java Main stats <source_file> <dest_file>");
    }
}
