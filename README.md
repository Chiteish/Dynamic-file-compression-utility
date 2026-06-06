# Dynamic File Compression (DFC) Utility

An entropy-aware dynamic file compression and archiving utility implemented in Java. The tool features an adaptive rules engine that analyzes file profiles in real-time (MIME, Shannon entropy, file size) to select, stream, package, and verify optimal compression strategies.

---

## 1. Project Overview
The **Dynamic File Compression (DFC)** utility is a modular command-line tool designed to solve file size optimization challenges. It supports native Huffman coding, multi-codec streaming pipelines (GZIP, Bzip2, LZMA, ZStandard, Brotli), recursive directory tar-archiving, and ZStandard dictionary training. Every compressed archive is packaged with a `.dfc` JSON manifest header ensuring SHA-256 lossless data integrity validation.

---

## 2. Problem Statement
Traditional data compression utilities apply static algorithms regardless of the input profile. This leads to two critical inefficiencies:
1. **Metadata Bloat**: Compressing small files (e.g. < 500 bytes) using algorithms like Huffman or Gzip often inflates the final file size because the serialized codebook/frequency table overhead outweighs the bit savings.
2. **Entropy Expansion**: Attempting to compress high-entropy, pre-compressed files (like JPEG, PNG, ZIP, or encrypted packages) wastes valuable CPU cycles and inflates files.

**The DFC Solution:** 
By analyzing a file's MIME type and Shannon entropy before compression, DFC dynamically elects the optimal codec (such as skipping compression entirely for high-entropy payloads or using LZW/LZMA for text blocks), ensuring maximum efficiency.

---

## 3. DSA Concepts Used

* **Huffman Coding Tree**: A binary tree representing character prefix paths. Highly frequent characters sit closer to the root (shorter paths), and rare characters sit further down (longer paths).
* **Min-Heap (Priority Queue)**: Used during Huffman tree building to extract the two lowest-frequency nodes in $O(\log N)$ time.
* **Recursive Tree Traversals**: Used to traverse the Huffman tree branches (Left = `0`, Right = `1`) to generate prefix-free codebooks.
* **Bitwise Manipulation**: Bit packing operations (`<<`, `|`, `>>`, `&`) are used to compress binary strings into raw packed bytes (MSB first) and extract them during decompression.
* **Shannon Entropy**: Logarithmic frequency analysis computing file randomness:
  $$H(X) = -\sum P(x_i) \log_2 P(x_i)$$
* **Hashing (SHA-256)**: Cryptographic checksum computation validating lossless decompression.

---

## 4. Algorithm Explanation

### Huffman Coding
1. **Frequency Mapping**: Read the input and map each unique byte to its occurrence frequency count.
2. **Priority Queue Seeding**: Insert all unique character mappings as leaf nodes into a Min-Heap.
3. **Greedy Merging**: Poll the two smallest frequency nodes, wrap them under a parent internal node containing their summed frequency, and insert the parent back into the Min-Heap. Repeat until only one root node remains.
4. **Codebook Generation**: Recurse down the tree. Traversal paths compile the prefix-free codes (e.g., `'a' -> "01"`, `'b' -> "00"`, `'c' -> "1"`).
5. **Bit Packing**: Translate the character stream into bitstrings and pack them 8 bits at a time into raw byte arrays.

### Adaptive Strategy Chooser
* **Entropy > 7.5**: Elects `STORE` strategy (copy bytes directly with zero compression cycles).
* **Text Mime Types (MIME: text/*, json, xml)**:
  * *FAST Mode*: Elects `GZIP` (fast compression, low memory).
  * *BALANCED Mode*: Elects `BZ2` (Bzip2 block sorting, high efficiency).
  * *MAX Mode*: Elects `LZMA` (Lempel-Ziv-Markov chain, optimal ratio).
* **Image/Uncompressed Payloads**: Elects `BZ2` or `STORE` depending on the byte entropy bounds.

---

## 5. Features

- [x] **Static Huffman Compression**: Core Huffman tree builder, character codebook mapper, and bit-packing stream.
- [x] **Shannon Entropy & Signature Detector**: Inspects file headers for magic bytes (PNG, JPEG, PDF, ZIP, GIF, ELF) and measures entropy.
- [x] **Strategy Chooser**: Adaptive selector matching files to `FAST`, `BALANCED`, or `MAX_COMPRESSION` modes.
- [x] **Multi-Codec Streaming**: Integrates GZIP natively and connects external binaries (`zstd`, `brotli`, `bzip2`, `lzma`) via process pipes.
- [x] **Zstd Dictionary Trainer**: Samples dataset chunks to train customized `.dict` files.
- [x] **Tar-to-Zstd Directory Compressor**: Pipes tar stream files directly into Zstandard compression pipelines.
- [x] **Lossless Checksum Verification**: Automatically computes and checks SHA-256 hashes inside `.dfc` JSON packages.

---

## 6. Folder Structure

```
DynamicFileCompressionUtility/
├── input/                 # Source files to compress (test datasets)
├── output/                # Generated compressed (.huf, .dfc) & restored files
├── screenshots/           # Execution screenshots showing logs and metrics
├── src/
│   ├── model/
│   │   ├── Node.java             # Huffman tree node
│   │   └── HuffmanTree.java      # Huffman tree & code generator
│   │
│   ├── service/
│   │   ├── Compressor.java       # Static Huffman compressor
│   │   ├── Decompressor.java     # Static Huffman decompressor
│   │   ├── StatisticsService.java# Compression ratio analyst
│   │   ├── StrategyChooser.java  # MIME/entropy strategy elector
│   │   ├── StrategyConfig.java   # Strategy details data holder
│   │   ├── StreamingCompressor.java # DFC manifest packager & writer
│   │   ├── StreamingDecompressor.java # DFC verification & decoder
│   │   ├── TarZstdCompressor.java# Directory tar pipeline
│   │   └── ZstdDictionaryTrainer.java # Zstd dict sample trainer
│   │
│   ├── util/
│   │   ├── DetectionResult.java  # File properties scanner output
│   │   └── FileManager.java      # Text & Binary IO file utilities
│   │
│   ├── Main.java                 # Basic CLI entry route
│   └── DfcCLI.java               # Unified DFC Toolchain subcommands CLI
│
├── test_dfc.py            # Automated pytest verification suite
├── .gitignore             # Configured Git staging exclusions
└── README.md              # Project documentation
```

---

## 7. How to Run

### Compilation
Compile the entire source code tree:
```bash
javac -d out src/model/*.java src/util/*.java src/service/*.java src/Main.java src/DfcCLI.java
```

### Subcommands Execution

#### 1. Compress dynamically (`compress`)
Analyzes the file, elects the strategy, and creates a `.dfc` package containing a manifest header.
```bash
java -cp out DfcCLI compress input/large_test.txt output/large_test.dfc --mode BALANCED
```

#### 2. Decompress and verify (`decompress`)
Decodes `.dfc` payload, restores original content, and checks SHA-256 integrity.
```bash
java -cp out DfcCLI decompress output/large_test.dfc output/large_test_restored.txt
```

#### 3. Verify integrity only (`verify`)
Decodes and verifies SHA-256 hashes inside the DFC package without saving final output file buffers.
```bash
java -cp out DfcCLI verify output/large_test.dfc
```

#### 4. Train a Zstd Dictionary (`train-dict`)
```bash
java -cp out DfcCLI train-dict input/large_test.txt output/large_test.dict 1024 8
```

#### 5. Tar-to-Zstd Folder Compression (`tar-zstd`)
Pipes recursive tar archive directly to Zstandard.
```bash
java -cp out DfcCLI tar-zstd src/model output/model.dfc --level 3
```

---

## 8. Sample Output (Statistics)

### Small File Execution (Entropy Overhead Example)
```
=========================================
          COMPRESSION STATISTICS         
=========================================
Original File Size:   54 bytes
Compressed File Size: 162 bytes
Compression Ratio:    300.00%
Space Savings:        -200.00%
=========================================
```
*(Shows file expansion on small payloads due to the serialized frequency table header).*

### Large File Execution (True Compression)
```
=========================================
          COMPRESSION STATISTICS         
=========================================
Original File Size:   9,500 bytes
Compressed File Size: 455 bytes
Compression Ratio:    4.79%
Space Savings:        95.21%
=========================================
```
*(Demonstrates massive space savings on larger, redundant text datasets using streaming GZIP).*

---

## 9. Screenshots

Live execution runs are saved in the [screenshots/](screenshots/) folder:
- `screenshots/compress_run.png`: Terminal logs of DfcCLI compression and strategy election.
- `screenshots/decompress_run.png`: Checksum match verification log output.

---

## 10. Learning Outcomes

1. **Algorithm Implementation**: Deepened understanding of Huffman coding, greedy algorithms, tree building, and prefix-free representations.
2. **Low-Level Bit Operations**: Gained practical experience in bitwise shifting, masking, and byte boundaries padding.
3. **Data Integrity Assurance**: Learned how SHA-256 checksums and structured manifest descriptors protect files from corrupted round-trips.
4. **Subprocess Pipes Integration**: Architected concurrent input/output streams between Java runtime processes and CLI utilities (`tar`, `zstd`, `bzip2`).
5. **System Observability**: Mastered entropy analysis, MIME type classification, and compression analytics.
