package com.detextion.textanalysis;

import java.util.*;
import java.util.regex.*;

public class TextAnalyzer {
    public static List<String> extractKeywords(String text) {
        Set<String> stopWords = Set.of("the", "and", "to", "is", "in", "on", "at", "a");
        Map<String, Integer> freqMap = new HashMap<>();
        Pattern wordPattern = Pattern.compile("\\b\\w+\\b");
        Matcher matcher = wordPattern.matcher(text);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            if (!stopWords.contains(word)) {
                freqMap.put(word, freqMap.getOrDefault(word, 0) + 1);
            }
        }
        // Return top 10 frequent words
        return freqMap.entrySet()
                .stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();
    }
}
