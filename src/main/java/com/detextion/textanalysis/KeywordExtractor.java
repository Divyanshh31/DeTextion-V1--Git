package com.detextion.textanalysis;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A smarter keyword extractor that filters out common words (stopwords)
 * and returns only the most meaningful keywords based on frequency.
 */
public class KeywordExtractor {

    // Common words to ignore (stopwords)
    private static final Set<String> STOPWORDS = Set.of(
            "the","a","an","and","or","but","if","while","with","to","of","in","on","at","by",
            "for","from","up","down","out","over","under","again","further","then","once",
            "here","there","when","where","why","how","all","any","both","each","few","more",
            "most","other","some","such","no","nor","not","only","own","same","so","than",
            "too","very","can","will","just","should","could","would","may","might","is","are",
            "was","were","be","been","being","have","has","had","do","does","did","done"
    );

    /**
     * Extracts the most meaningful keywords from a text.
     * @param text The full input text from which to extract keywords.
     * @return A map of top keywords with their frequency counts.
     */
    public static Map<String, Integer> extractImportantKeywords(String text) {
        if (text == null || text.isEmpty()) return Map.of();

        // Normalize text: lowercase and remove punctuation
        text = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ");

        // Split into words, remove stopwords and very short tokens
        List<String> words = Arrays.stream(text.split("\\s+"))
                .filter(w -> w.length() > 3 && !STOPWORDS.contains(w))
                .collect(Collectors.toList());

        // Count frequency
        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            freq.put(w, freq.getOrDefault(w, 0) + 1);
        }

        // Sort by frequency and keep only top 20
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}
