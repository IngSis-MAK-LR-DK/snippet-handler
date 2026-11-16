package edu.austral.ingsis.snippethandler.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class StreamRequestTest {

    @Test
    void withoutInputsTheListIsEmpty() {
        assertEquals(List.of(), new StreamRequest("c", "1.1", null, true, null).inputs());
    }

    @Test
    void theInputsCannotChangeAfterwards() {
        List<String> inputs = new ArrayList<>(List.of("a"));
        StreamRequest request = new StreamRequest("c", "1.1", inputs, false, "t");
        inputs.add("b");

        assertEquals(List.of("a"), request.inputs());
        assertThrows(UnsupportedOperationException.class, () -> request.inputs().add("c"));
    }
}
