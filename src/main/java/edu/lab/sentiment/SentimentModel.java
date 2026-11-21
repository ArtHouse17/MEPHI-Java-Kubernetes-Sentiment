package edu.lab.sentiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class SentimentModel {
    private enum Label { NEUTRAL, NEGATIVE, POSITIVE }

    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private final Map<Label, WordCounts> classes = new EnumMap<>(Label.class);
    private final Set<String> vocabulary = new HashSet<>();
    private int documentCount;

    private SentimentModel() {
        for (Label label : Label.values()) {
            classes.put(label, new WordCounts());
        }
    }

    static SentimentModel train(InputStream input) throws IOException {
        var model = new SentimentModel();
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split("\\t", 2);
                Label label = Label.valueOf(fields[0].toUpperCase(Locale.ROOT));
                WordCounts counts = model.classes.get(label);
                counts.documents++;
                model.documentCount++;
                words(fields[1]).forEach(word -> {
                    model.vocabulary.add(word);
                    counts.frequencies.merge(word, 1, Integer::sum);
                    counts.tokens++;
                });
            }
        }
        return model;
    }

    String predict(String text) {
        var knownWords = words(text).filter(vocabulary::contains).toList();
        if (knownWords.isEmpty()) {
            return "neutral";
        }

        Label bestLabel = Label.NEUTRAL;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (var entry : classes.entrySet()) {
            WordCounts counts = entry.getValue();
            double score = Math.log((double) counts.documents / documentCount);
            double denominator = (double) counts.tokens + vocabulary.size();
            for (String word : knownWords) {
                score += Math.log((1.0 + counts.frequencies.getOrDefault(word, 0)) / denominator);
            }
            if (score > bestScore) {
                bestLabel = entry.getKey();
                bestScore = score;
            }
        }
        return bestLabel.name().toLowerCase(Locale.ROOT);
    }

    private static Stream<String> words(String text) {
        return WORD.matcher(text.toLowerCase(Locale.ROOT)).results().map(MatchResult::group);
    }

    private static final class WordCounts {
        private final Map<String, Integer> frequencies = new HashMap<>();
        private int documents;
        private int tokens;
    }
}
