package com.jupiter.transcript;

import com.jupiter.transcript.utils.MyHints;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TranscriptApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldRegisterResourceHints() {
        RuntimeHints hints = new RuntimeHints();
        new MyHints().registerHints(hints, getClass().getClassLoader());

    }

}
