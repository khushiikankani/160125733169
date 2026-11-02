package com.campus.result.service;

import com.campus.result.exception.InvalidRecordException;
import com.campus.result.model.Student;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

// One task = one CSV file. Many tasks run in parallel on the thread pool.
public class FileProcessor implements Callable<ProcessingResult> {
    private final Path file;
    private final Set<String> seenIds;
    private final AuditLogger audit;

    public FileProcessor(Path file, Set<String> seenIds, AuditLogger audit) {
        this.file = file;
        this.seenIds = seenIds;
        this.audit = audit;
    }

    @Override
    public ProcessingResult call() throws Exception {
        String fileName = file.getFileName().toString();
        audit.log("Started " + fileName);
        List<String> lines = Files.readAllLines(file);   // NIO read
        List<Student> students = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {         // index 0 is the header
            String line = lines.get(i);
            if (line.isBlank()) continue;
            try {
                Student s = RecordParser.parse(line, i + 1);
                if (!seenIds.add(s.getId())) {           // thread safe set
                    errors.add(fileName + " Line " + (i + 1) + ": duplicate id " + s.getId());
                } else {
                    students.add(s);
                }
            } catch (InvalidRecordException e) {
                errors.add(fileName + " " + e.getMessage());
            }
        }
        Thread.sleep(300);                               // simulates heavier work
        audit.log("Finished " + fileName + " valid=" + students.size() + " errors=" + errors.size());
        return new ProcessingResult(fileName, Thread.currentThread().getName(), students, errors);
    }
}
