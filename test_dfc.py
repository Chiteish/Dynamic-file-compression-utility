import os
import subprocess
import hashlib
import random
import pytest

# Paths to the compiled Java CLI classes
CP_PATH = "out"
CLI_CLASS = "DfcCLI"
JAVA_CMD = ["java", "-cp", CP_PATH, CLI_CLASS]

@pytest.fixture(scope="session", autouse=True)
def compile_java_project():
    """Ensure Java classes are compiled before running tests."""
    print("\nCompiling Java project...")
    compile_cmd = [
        "javac", "-d", "out",
        "src/model/Node.java",
        "src/model/HuffmanTree.java",
        "src/util/FileManager.java",
        "src/util/DetectionResult.java",
        "src/util/Detector.java",
        "src/service/Compressor.java",
        "src/service/Decompressor.java",
        "src/service/StatisticsService.java",
        "src/service/StrategyConfig.java",
        "src/service/StrategyChooser.java",
        "src/service/StreamingCompressor.java",
        "src/service/StreamingDecompressor.java",
        "src/service/ZstdDictionaryTrainer.java",
        "src/service/TarZstdCompressor.java",
        "src/Main.java",
        "src/DfcCLI.java"
    ]
    result = subprocess.run(compile_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"Compilation failed: {result.stderr}"
    
    # Create test directories if they do not exist
    os.makedirs("test_input", exist_ok=True)
    os.makedirs("test_output", exist_ok=True)
    yield
    # Clean up test directories after session
    for folder in ["test_input", "test_output"]:
        if os.path.exists(folder):
            for file in os.listdir(folder):
                os.remove(os.path.join(folder, file))
            os.rmdir(folder)

def sha256_hash(filepath):
    """Calculates the SHA-256 hash of a file."""
    sha256 = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            sha256.update(chunk)
    return sha256.hexdigest()

def generate_synthetic_text(filepath, size_bytes):
    """Generates text file with highly redundant, repeating sequences."""
    phrases = [
        "huffman coding is a prefix-free entropy encoding algorithm. ",
        "dynamic file compression dynamically adapts codecs. ",
        "lossless data compression guarantees matching checksums. "
    ]
    with open(filepath, "w", encoding="utf-8") as f:
        current_size = 0
        while current_size < size_bytes:
            phrase = random.choice(phrases)
            f.write(phrase)
            current_size += len(phrase)

def generate_random_binary(filepath, size_bytes):
    """Generates random bytes file representing high-entropy binary payloads."""
    with open(filepath, "wb") as f:
        f.write(bytearray(random.getrandbits(8) for _ in range(size_bytes)))

# --- TEST CASES ---

def test_static_huffman_roundtrip_text():
    """Verify static Huffman compression/decompression on synthetic text."""
    input_file = "test_input/text_static.txt"
    compressed_file = "test_output/text_static.huf"
    restored_file = "test_output/text_static_restored.txt"

    # 1. Create text file (approx 10 KB)
    generate_synthetic_text(input_file, 10000)
    original_hash = sha256_hash(input_file)

    # 2. Compress using the Main class
    compress_cmd = ["java", "-cp", CP_PATH, "Main", "compress", input_file, compressed_file]
    result = subprocess.run(compress_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"Huffman compression failed: {result.stderr}"

    # 3. Decompress
    decompress_cmd = ["java", "-cp", CP_PATH, "Main", "decompress", compressed_file, restored_file]
    result = subprocess.run(decompress_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"Huffman decompression failed: {result.stderr}"

    # 4. Compare hash values
    assert sha256_hash(restored_file) == original_hash, "Static Huffman restored file mismatch!"

def test_dfc_streaming_roundtrip_text():
    """Verify DfcCLI compress/verify/decompress pipeline on synthetic text (uses GZIP)."""
    input_file = "test_input/text_streaming.txt"
    compressed_file = "test_output/text_streaming.dfc"
    restored_file = "test_output/text_streaming_restored.txt"

    # 1. Create text file (approx 15 KB)
    generate_synthetic_text(input_file, 15000)
    original_hash = sha256_hash(input_file)

    # 2. Compress via DfcCLI (selects GZIP/LZMA based on mode, --mode FAST uses GZIP)
    compress_cmd = JAVA_CMD + ["compress", input_file, compressed_file, "--mode", "FAST"]
    result = subprocess.run(compress_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"DFC compress failed: {result.stderr}"
    assert "Elected Codec:   GZIP" in result.stdout

    # 3. Verify DFC package integrity
    verify_cmd = JAVA_CMD + ["verify", compressed_file]
    result = subprocess.run(verify_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"DFC verify failed: {result.stderr}"
    assert "Verification SUCCESS" in result.stdout

    # 4. Decompress via DfcCLI
    decompress_cmd = JAVA_CMD + ["decompress", compressed_file, restored_file]
    result = subprocess.run(decompress_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"DFC decompress failed: {result.stderr}"
    assert "Verification SUCCESS" in result.stdout

    # 5. Compare original and restored files
    assert sha256_hash(restored_file) == original_hash, "DFC streaming restored file mismatch!"

def test_dfc_streaming_roundtrip_binary():
    """Verify DfcCLI compress/verify/decompress pipeline on binary files."""
    input_file = "test_input/binary_streaming.bin"
    compressed_file = "test_output/binary_streaming.dfc"
    restored_file = "test_output/binary_streaming_restored.bin"

    # 1. Create random binary file (approx 8 KB)
    generate_random_binary(input_file, 8000)
    original_hash = sha256_hash(input_file)

    # 2. Compress via DfcCLI
    compress_cmd = JAVA_CMD + ["compress", input_file, compressed_file, "--mode", "FAST"]
    result = subprocess.run(compress_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"DFC binary compress failed: {result.stderr}"

    # 3. Verify package integrity
    verify_cmd = JAVA_CMD + ["verify", compressed_file]
    result = subprocess.run(verify_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"DFC verify failed: {result.stderr}"
    assert "Verification SUCCESS" in result.stdout

    # 4. Decompress package
    decompress_cmd = JAVA_CMD + ["decompress", compressed_file, restored_file]
    result = subprocess.run(decompress_cmd, capture_output=True, text=True)
    assert result.returncode == 0, f"DFC decompress failed: {result.stderr}"
    assert "Verification SUCCESS" in result.stdout

    # 5. Compare hash values
    assert sha256_hash(restored_file) == original_hash, "DFC binary restored file mismatch!"
