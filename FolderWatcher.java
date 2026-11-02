package com.campus.result.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

// Uses the NIO WatchService to react when a new CSV file is dropped into the input folder
public class FolderWatcher implements Runnable {
    private final Path dir;
    private final ResultService service;
    private final int seconds;

    public FolderWatcher(Path dir, ResultService service, int seconds) {
        this.dir = dir;
        this.service = service;
        this.seconds = seconds;
    }

    @Override
    public void run() {
        long end = System.currentTimeMillis() + seconds * 1000L;
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE);
            System.out.println("Watching " + dir + " for " + seconds + " seconds...");
            while (System.currentTimeMillis() < end) {
                WatchKey key = ws.poll(500, TimeUnit.MILLISECONDS);
                if (key == null) continue;
                for (WatchEvent<?> ev : key.pollEvents()) {
                    Path name = (Path) ev.context();
                    if (name.toString().endsWith(".csv")) {
                        System.out.println("New file detected: " + name);
                        Thread.sleep(300);              // let the copy finish
                        service.processOne(dir.resolve(name));
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Watcher stopped: " + e.getMessage());
        }
    }
}
