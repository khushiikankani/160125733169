package com.campus.result.service;

import com.campus.result.model.Student;
import java.util.List;

public record ProcessingResult(String fileName, String threadName,
                               List<Student> students, List<String> errors) { }
