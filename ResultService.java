package com.campus.result.service;

import com.campus.result.model.Student;
import com.campus.result.report.CsvReportWriter;
import com.campus.result.report.ReportWriter;
import com.campus.result.report.TextReportWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ResultService {
    private final Path inputDir;
    private final Path outputDir;
    private final AuditLogger audit;
    private final Set<String> seenIds = ConcurrentHashMap.newKeySet();
    private final List<ProcessingResult> results = Collections.synchronizedList(new ArrayList<>());
    private final List<ReportWriter> writers = List.of(new TextReportWriter(), new CsvReportWriter());
    private final ExecutorService pool;

    public ResultService(Path inputDir, Path outputDir, AuditLogger audit) throws IOException {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.audit = audit;
        Files.createDirectories(outputDir);
        AtomicInteger counter = new AtomicInteger(1);
        this.pool = Executors.newFixedThreadPool(3, r -> new Thread(r, "Worker-" + counter.getAndIncrement()));
    }

    // Finds every CSV in the input folder and processes them in parallel
    public void processAll() throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(inputDir, "*.csv")) {
            for (Path p : ds) files.add(p);
        }
        Collections.sort(files);
        System.out.println("Found " + files.size() + " input file(s) in " + inputDir);

        Map<Path, Future<ProcessingResult>> futures = new LinkedHashMap<>();
        for (Path f : files) {
            futures.put(f, pool.submit(new FileProcessor(f, seenIds, audit)));
        }
        for (Map.Entry<Path, Future<ProcessingResult>> e : futures.entrySet()) {
            collect(e.getKey(), e.getValue());
        }
    }

    // Used by the watch mode for a single new file
    public void processOne(Path file) {
        collect(file, pool.submit(new FileProcessor(file, seenIds, audit)));
    }

    private void collect(Path file, Future<ProcessingResult> future) {
        try {
            ProcessingResult r = future.get();
            results.add(r);
            System.out.printf("[%s] %s -> %d valid, %d rejected%n",
                    r.threadName(), r.fileName(), r.students().size(), r.errors().size());
        } catch (ExecutionException e) {
            System.out.println("Failed to process " + file.getFileName() + ": " + e.getCause());
            audit.log("FAILED " + file.getFileName() + " : " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void generateReports() throws IOException {
        List<Student> all = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        synchronized (results) {
            for (ProcessingResult r : results) {
                all.addAll(r.students());
                errors.addAll(r.errors());
            }
        }
        all.sort(Comparator.comparingDouble(Student::getAverage).reversed()
                .thenComparing(Student::getName));

        for (ReportWriter w : writers) {
            Path target = outputDir.resolve("result_report" + w.getExtension());
            w.write(target, all);
            System.out.println("Written " + target);
        }
        Files.write(outputDir.resolve("errors.txt"), errors);
        System.out.println("Written " + outputDir.resolve("errors.txt"));
        printSummary(all, errors.size());
    }

    private void printSummary(List<Student> all, int errorCount) {
        System.out.println();
        System.out.println("========== SUMMARY ==========");
        System.out.println("Valid students : " + all.size());
        System.out.println("Rejected lines : " + errorCount);
        System.out.println("Passed         : " + all.stream().filter(Student::isPass).count());
        System.out.println("Failed         : " + all.stream().filter(s -> !s.isPass()).count());
        all.stream().findFirst().ifPresent(s ->
                System.out.println("Topper         : " + s.getName() + " (" + String.format("%.2f", s.getAverage()) + ")"));
        Map<String, Double> byDept = all.stream().collect(
                Collectors.groupingBy(Student::getDepartment, TreeMap::new,
                        Collectors.averagingDouble(Student::getAverage)));
        byDept.forEach((d, avg) -> System.out.printf("Dept %-6s avg : %.2f%n", d, avg));
    }

    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
