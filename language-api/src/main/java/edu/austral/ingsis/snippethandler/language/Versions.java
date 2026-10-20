package edu.austral.ingsis.snippethandler.language;

import java.util.Comparator;

/** Ordering of language versions such as {@code 1.0}, {@code 1.1} or {@code 1.10}. */
public final class Versions {

    /**
     * Compares dot-separated segments one by one, numerically when both are numbers ({@code 1.10}
     * comes after {@code 1.9}) and as text otherwise. A missing segment counts as smaller ({@code
     * 1.0} comes before {@code 1.0.1}).
     */
    public static final Comparator<String> ORDER = Versions::compare;

    private Versions() {}

    private static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int result = compareSegment(a[i], b[i]);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private static int compareSegment(String a, String b) {
        if (isNumber(a) && isNumber(b)) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        }
        return a.compareTo(b);
    }

    private static boolean isNumber(String segment) {
        return !segment.isEmpty()
                && segment.length() < 19
                && segment.chars().allMatch(Character::isDigit);
    }
}
