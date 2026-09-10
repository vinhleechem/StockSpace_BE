package fu.stockspace.stockspace_be.chatbot.service;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Selects a small, relevant passage from a knowledge document. The old search
 * returned the whole document, which made long policies noisy and prevented a
 * UI from showing useful provenance. This selector keeps original offsets so a
 * client can later open the exact source span.
 */
public final class KnowledgePassageSelector {

    private static final int TARGET_CHARS = 900;
    private static final int MAX_CHARS = 1_400;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "do", "for", "how", "i", "is", "of", "or", "the", "to", "what",
            "ai", "ban", "cai", "can", "cho", "co", "cua", "duoc", "gi", "hay", "hoi", "khong",
            "la", "minh", "mot", "nao", "neu", "nhung", "thi", "toi", "va", "ve", "voi", "xin"
    );

    private KnowledgePassageSelector() {
    }

    public static Passage selectBest(String title, String content, String query) {
        List<Passage> passages = split(content);
        if (passages.isEmpty()) {
            return new Passage(0, 0, 0, "", 0.0);
        }

        Set<String> queryTerms = terms(query);
        String normalizedTitle = normalize(title);
        return passages.stream()
                .map(passage -> passage.withScore(score(passage.text(), queryTerms, normalizedTitle)))
                .max(Comparator.comparingDouble(Passage::score)
                        .thenComparingInt(Passage::startOffset))
                .orElse(passages.get(0));
    }

    static List<Passage> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<Sentence> sentences = sentences(content);
        List<Passage> result = new ArrayList<>();
        int passageStart = -1;
        int passageEnd = -1;
        StringBuilder text = new StringBuilder();
        int passageIndex = 0;

        for (Sentence sentence : sentences) {
            if (sentence.text().isBlank()) {
                continue;
            }
            boolean wouldExceed = text.length() > 0
                    && text.length() + 1 + sentence.text().length() > TARGET_CHARS;
            if (wouldExceed) {
                result.add(new Passage(
                        passageIndex++, passageStart, passageEnd,
                        text.toString().trim(), 0.0
                ));
                // A small overlap keeps a fact at a passage boundary searchable.
                String overlap = text.length() > 180
                        ? text.substring(text.length() - 180).trim()
                        : text.toString().trim();
                text.setLength(0);
                text.append(overlap);
                passageStart = Math.max(0, passageEnd - overlap.length());
            }
            if (passageStart < 0) {
                passageStart = sentence.startOffset();
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(sentence.text());
            passageEnd = sentence.endOffset();

            // A single huge sentence is split deterministically, rather than
            // returning an oversized prompt/tool payload.
            while (text.length() > MAX_CHARS) {
                int splitAt = text.lastIndexOf(" ", MAX_CHARS);
                if (splitAt < 1) {
                    splitAt = MAX_CHARS;
                }
                result.add(new Passage(
                        passageIndex++, passageStart, passageStart + splitAt,
                        text.substring(0, splitAt).trim(), 0.0
                ));
                String remainder = text.substring(splitAt).trim();
                text.setLength(0);
                text.append(remainder);
                passageStart = Math.max(0, passageEnd - remainder.length());
            }
        }

        if (text.length() > 0) {
            result.add(new Passage(
                    passageIndex, Math.max(0, passageStart), passageEnd,
                    text.toString().trim(), 0.0
            ));
        }
        return List.copyOf(result);
    }

    private static List<Sentence> sentences(String content) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(new Locale("vi", "VN"));
        iterator.setText(content);
        List<Sentence> result = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String raw = content.substring(start, end);
            int leftTrim = 0;
            while (leftTrim < raw.length() && Character.isWhitespace(raw.charAt(leftTrim))) {
                leftTrim++;
            }
            int rightTrim = raw.length();
            while (rightTrim > leftTrim && Character.isWhitespace(raw.charAt(rightTrim - 1))) {
                rightTrim--;
            }
            if (leftTrim < rightTrim) {
                result.add(new Sentence(
                        start + leftTrim,
                        start + rightTrim,
                        raw.substring(leftTrim, rightTrim)
                ));
            }
        }
        return result;
    }

    private static double score(String text, Set<String> queryTerms, String normalizedTitle) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> passageTerms = terms(text);
        long matches = queryTerms.stream().filter(passageTerms::contains).count();
        double coverage = (double) matches / queryTerms.size();
        double titleBoost = queryTerms.stream().anyMatch(normalizedTitle::contains) ? 0.10 : 0.0;
        return Math.min(1.0, coverage + titleBoost);
    }

    private static Set<String> terms(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return NON_WORD.matcher(
                        DIACRITICS.matcher(decomposed).replaceAll("")
                                .replace('đ', 'd')
                                .replace('Đ', 'D')
                                .toLowerCase(Locale.ROOT)
                )
                .replaceAll(" ")
                .trim();
    }

    public record Passage(
            int index,
            int startOffset,
            int endOffset,
            String text,
            double score
    ) {

        public Passage {
            text = text == null ? "" : text;
            score = Double.isFinite(score) ? Math.max(0.0, Math.min(1.0, score)) : 0.0;
        }

        private Passage withScore(double value) {
            return new Passage(index, startOffset, endOffset, text, value);
        }
    }

    private record Sentence(int startOffset, int endOffset, String text) {
    }
}
