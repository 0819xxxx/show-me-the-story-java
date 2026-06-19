package com.showmethestory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Low-level filesystem operations with atomic-write support.
 * Atomic writes go to a .tmp file first, then rename to the target path.
 */
@Service
public class FileSystemService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemService.class);

    /**
     * Write data to the given path atomically: write to path.tmp first,
     * then rename to path. If rename fails the .tmp file is cleaned up.
     */
    public void writeFileAtomic(String path, byte[] data) throws IOException {
        String tmpPath = path + ".tmp";
        try {
            writeFile(tmpPath, data);
            renameFile(tmpPath, path);
        } catch (IOException e) {
            // Best-effort cleanup of the temp file
            try {
                deleteFile(tmpPath);
            } catch (IOException ignored) {
                // ignore cleanup failure
            }
            throw e;
        }
    }

    /**
     * Read the entire file at the given path into a byte array.
     * Returns null if the file does not exist.
     */
    public byte[] readFile(String path) throws IOException {
        Path p = Path.of(path);
        if (!Files.exists(p)) {
            return null;
        }
        return Files.readAllBytes(p);
    }

    /**
     * Delete the file at the given path.
     */
    public void deleteFile(String path) throws IOException {
        Files.deleteIfExists(Path.of(path));
    }

    /**
     * Rename (move) a file from oldPath to newPath, replacing the target if it exists.
     */
    public void renameFile(String oldPath, String newPath) throws IOException {
        Files.move(Path.of(oldPath), Path.of(newPath),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ---- internal helpers ----

    private void writeFile(String path, byte[] data) throws IOException {
        Path p = Path.of(path);
        // Ensure parent directories exist
        Path parent = p.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(p, data);
    }
}
