package edu.austral.ingsis.snippethandler.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class VersionsTest {

    @Test
    void comparesSegmentsNumerically() {
        assertTrue(Versions.ORDER.compare("1.10", "1.9") > 0);
        assertTrue(Versions.ORDER.compare("2.0", "1.99") > 0);
        assertEquals(0, Versions.ORDER.compare("1.1", "1.1"));
    }

    @Test
    void missingSegmentsAreSmaller() {
        assertTrue(Versions.ORDER.compare("1.0", "1.0.1") < 0);
    }

    @Test
    void nonNumericSegmentsCompareAsText() {
        assertTrue(Versions.ORDER.compare("1.0-beta", "1.0-alpha") > 0);
        assertTrue(Versions.ORDER.compare("1.x", "1.2") > 0);
    }

    @Test
    void sortsAListOfVersions() {
        assertEquals(
                List.of("1.0", "1.1", "1.9", "1.10"),
                Stream.of("1.10", "1.0", "1.9", "1.1").sorted(Versions.ORDER).toList());
    }
}
