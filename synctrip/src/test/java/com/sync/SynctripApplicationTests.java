package com.sync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-for-ci")
class SynctripApplicationTests {

    @Test
    void contextLoads() {
    }

}
