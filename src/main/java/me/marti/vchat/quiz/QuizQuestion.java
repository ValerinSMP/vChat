package me.marti.vchat.quiz;

import java.util.List;

public record QuizQuestion(
        String question,
        String answer,
        List<String> options,
        String difficulty
) {

    public String buildHint() {
        if (answer.length() <= 2) return answer;
        StringBuilder sb = new StringBuilder();
        sb.append(answer.charAt(0));
        for (int i = 1; i < answer.length() - 1; i++) {
            sb.append(answer.charAt(i) == ' ' ? ' ' : '_');
        }
        sb.append(answer.charAt(answer.length() - 1));
        return sb.toString();
    }

    public boolean isCorrect(String input) {
        return normalize(answer).equals(normalize(input));
    }

    private static String normalize(String s) {
        String lower = s.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        // Strip diacritics (á→a, é→e, etc.)
        String nfd = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }

    public static QuizQuestion parse(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 6) return null;
        String question = parts[0].trim();
        String answer = parts[1].trim();
        List<String> options = List.of(parts[2].trim(), parts[3].trim(), parts[4].trim());
        String difficulty = parts[5].trim().toLowerCase(java.util.Locale.ROOT);
        return new QuizQuestion(question, answer, options, difficulty);
    }
}
