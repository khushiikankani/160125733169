package com.campus.result.service;

import com.campus.result.exception.InvalidRecordException;
import com.campus.result.model.ScholarshipStudent;
import com.campus.result.model.Student;

public final class RecordParser {
    private RecordParser() { }

    // Line format: id,name,department,type,mark1,mark2,mark3
    public static Student parse(String line, int lineNo) throws InvalidRecordException {
        String[] p = line.split(",");
        if (p.length != 7) {
            throw new InvalidRecordException(lineNo, "expected 7 fields but found " + p.length);
        }
        String id = p[0].trim();
        String name = p[1].trim();
        String dept = p[2].trim().toUpperCase();
        String type = p[3].trim().toUpperCase();
        if (id.isEmpty() || name.isEmpty()) {
            throw new InvalidRecordException(lineNo, "id and name must not be empty");
        }
        int[] marks = new int[3];
        try {
            for (int i = 0; i < 3; i++) {
                marks[i] = Integer.parseInt(p[4 + i].trim());
                if (marks[i] < 0 || marks[i] > 100) {
                    throw new InvalidRecordException(lineNo, "mark out of range 0-100: " + marks[i]);
                }
            }
        } catch (NumberFormatException e) {
            throw new InvalidRecordException(lineNo, "marks must be whole numbers", e);
        }
        switch (type) {
            case "REGULAR":
                return new Student(id, name, dept, marks);
            case "SCHOLARSHIP":
                return new ScholarshipStudent(id, name, dept, marks);
            default:
                throw new InvalidRecordException(lineNo, "unknown student type: " + type);
        }
    }
}
