package edu.academic.integrity.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class LogReader {
    private LogReader() { }

    public static String tail(File file, int maximumLines) throws IOException {
        if (file == null || !file.exists()) return "(log is empty)";
        if (maximumLines < 1) throw new IllegalArgumentException("maximumLines must be positive");
        String[] ring = new String[maximumLines];
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) ring[count++ % maximumLines] = line;
        }
        int available = Math.min(count, maximumLines);
        int start = count <= maximumLines ? 0 : count % maximumLines;
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < available; i++) {
            output.append(ring[(start + i) % maximumLines]).append('\n');
        }
        return output.length() == 0 ? "(log is empty)" : output.toString();
    }
}
