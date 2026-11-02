package com.campus.result.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

// Appends audit lines using NIO FileChannel and ByteBuffer
public class AuditLogger {
    private final Path logFile;

    public AuditLogger(Path logFile) throws IOException {
        this.logFile = logFile;
        Files.createDirectories(logFile.toAbsolutePath().getParent());
    }

    public synchronized void log(String message) {
        String line = LocalTime.now().truncatedTo(ChronoUnit.SECONDS) + " ["
                + Thread.currentThread().getName() + "] " + message + System.lineSeparator();
        ByteBuffer buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
        try (FileChannel ch = FileChannel.open(logFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            while (buffer.hasRemaining()) {
                ch.write(buffer);
            }
        } catch (IOException e) {
            System.err.println("Could not write audit log: " + e.getMessage());
        }
    }
}
