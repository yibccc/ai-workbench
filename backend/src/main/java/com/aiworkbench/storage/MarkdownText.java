package com.aiworkbench.storage;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared inert text grammar. Limits belong to the consuming business feature. */
public final class MarkdownText {
    private MarkdownText() {}
    public static String decode(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) { return decode(input); }
    }
    public static String decode(java.io.InputStream input) throws IOException {
        String value=decodePreservingBom(input);
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
    public static String decodePreservingBom(java.io.InputStream input) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        StringBuilder text = new StringBuilder();
        try (Reader reader = new InputStreamReader(input, decoder)) {
            char[] buffer = new char[4096]; int count;
            while ((count = reader.read(buffer)) != -1) text.append(buffer, 0, count);
        }
        String value = text.toString();
        validate(value);
        return value;
    }
    public static void validate(String text) throws IOException {
        if (text == null) throw new IOException("Missing text");
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isHighSurrogate(value)) {
                if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IOException("Invalid text");
            } else if (Character.isLowSurrogate(value)
                    || (Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t')) {
                throw new IOException("Invalid text");
            }
        }
    }
}
