# Dynamic File Compression Utility

A command-line Java application that implements the Huffman coding algorithm for dynamic file compression and decompression.

## Project Structure

```
DynamicFileCompressionUtility/
├── input/             # Source files to be compressed
├── output/            # Compressed and decompressed output files
├── screenshots/       # Execution screenshots and demos
└── src/
    ├── model/         # Data structures (Node, HuffmanTree)
    ├── service/       # Business logic (Compressor, Decompressor, StatisticsService)
    ├── util/          # Helper utilities (FileManager)
    └── Main.java      # Application entry point
```

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 8 or higher.

### Compilation

Compile the Java source files from the project root:

```powershell
javac -d out src/model/*.java src/util/*.java src/service/*.java src/Main.java
```

### Usage

Run the compiled application using:

```powershell
java -cp out Main <mode> <source_file> <dest_file>
```

#### Compression:
```powershell
java -cp out Main compress input/sample.txt output/sample.huf
```

#### Decompression:
```powershell
java -cp out Main decompress output/sample.huf output/sample_restored.txt
```

#### View Statistics:
```powershell
java -cp out Main stats input/sample.txt output/sample.huf
```
