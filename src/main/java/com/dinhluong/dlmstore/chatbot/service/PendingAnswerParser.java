package com.dinhluong.dlmstore.chatbot.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic parsing of bounded clarification answers; never searches outside persisted candidates. */
public final class PendingAnswerParser {
    public enum Classification { ANSWER_PENDING, NEW_REQUEST, CANCEL, UNRELATED }
    public static Classification classify(String message, boolean recognizedAnswer) {
        String text = name(message);
        if (text.matches("^(huy|huy yeu cau|thoi|cancel|bo qua)( .*)?$")) return Classification.CANCEL;
        if (text.matches("^(yeu cau moi|chuyen sang|doi chu de|bat dau lai)( .*)?$")) return Classification.NEW_REQUEST;
        return recognizedAnswer ? Classification.ANSWER_PENDING : Classification.UNRELATED;
    }
    private PendingAnswerParser() { }
    private static final Pattern MONEY = Pattern.compile("(?:(\\d+(?:[.,]\\d+)?)\\s*[-–]\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(trieu|cu)\\b");
    public static Map<String, BigDecimal> budget(String message) {
        var match = MONEY.matcher(TopicResolutionService.normalize(message));
        if (!match.find()) return Map.of();
        var result = new LinkedHashMap<String, BigDecimal>();
        if (match.group(1) != null) result.put("minPrice", amount(match.group(1)));
        result.put("maxPrice", amount(match.group(2)));
        return result;
    }
    private static BigDecimal amount(String number) {
        return new BigDecimal(number.replace(',', '.')).multiply(BigDecimal.valueOf(1_000_000));
    }
    public static String name(String text) {
        return TopicResolutionService.normalize(text).replaceAll("[\\p{Punct}]", " ")
                .replaceAll("\\s+", " ").replaceAll("\\s+(nhe|nha|a|di)$", "").trim();
    }
    public static Long select(String message, Map<Long, String> candidates) {
        String normalized = name(message);
        var position = Pattern.compile("^(?:(?:chon |san pham |mau |cai |so |thu )*)(\\d+)$").matcher(normalized);
        if (position.matches()) {
            try {
                int index = Integer.parseInt(position.group(1)) - 1;
                return index >= 0 && index < candidates.size() ? new ArrayList<>(candidates.keySet()).get(index) : null;
            } catch (NumberFormatException ignored) { return null; }
        }
        var exact = candidates.entrySet().stream().filter(e -> name(e.getValue()).equals(normalized)).toList();
        if (exact.size() == 1) return exact.get(0).getKey();
        // Full comparison sentences may include the already resolved counterpart. Prefer longest exact bounded name.
        var contained = candidates.entrySet().stream().filter(e -> (" " + normalized + " ")
                .contains(" " + name(e.getValue()) + " ")).toList();
        if (!contained.isEmpty()) {
            int longest = contained.stream().mapToInt(e -> name(e.getValue()).length()).max().orElse(0);
            var best = contained.stream().filter(e -> name(e.getValue()).length() == longest).toList();
            if (best.size() == 1) return best.get(0).getKey();
        }
        if (normalized.length() >= 4) {
            var aliases = candidates.entrySet().stream().filter(e -> (" " + name(e.getValue()) + " ")
                    .contains(" " + normalized + " ")).toList();
            if (aliases.size() == 1) return aliases.get(0).getKey();
        }
        var scores = candidates.entrySet().stream().map(e -> Map.entry(e.getKey(), distance(normalized,name(e.getValue()))))
                .sorted(Map.Entry.comparingByValue()).toList();
        if (!scores.isEmpty() && normalized.length() >= 8 && scores.get(0).getValue() <= 0.12
                && (scores.size() == 1 || scores.get(1).getValue() - scores.get(0).getValue() >= 0.08))
            return scores.get(0).getKey();
        return null;
    }
    private static double distance(String a, String b) {
        int[] row = new int[b.length()+1];
        for(int j=0;j<row.length;j++) row[j]=j;
        for(int i=1;i<=a.length();i++) {
            int previous=row[0];row[0]=i;
            for(int j=1;j<=b.length();j++) {
                int old=row[j];row[j]=Math.min(Math.min(row[j]+1,row[j-1]+1),previous+(a.charAt(i-1)==b.charAt(j-1)?0:1));previous=old;
            }
        }
        return (double)row[b.length()]/Math.max(1,Math.max(a.length(),b.length()));
    }
}
