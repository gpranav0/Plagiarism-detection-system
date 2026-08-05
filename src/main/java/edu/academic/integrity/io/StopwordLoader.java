package edu.academic.integrity.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class StopwordLoader {
    private StopwordLoader() { }

    public static String[] load(File file, ActivityLogger logger) {
        if (file == null || !file.exists()) return new String[0];
        String[] words = new String[32];
        int size = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toLowerCase();
                if (word.isEmpty() || word.startsWith("#")) continue;
                if (size == words.length) {
                    String[] replacement = new String[words.length * 2];
                    System.arraycopy(words, 0, replacement, 0, words.length);
                    words = replacement;
                }
                words[size++] = word;
            }
        } catch (IOException exception) {
            logger.error("Unable to load stop words", exception);
        }
        String[] result = new String[size];
        System.arraycopy(words, 0, result, 0, size);
        return result;
    }
}

