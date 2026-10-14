package com.pablosousa.confere;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

@SpringBootTest
class ConfereApplicationTests {

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registry) { TestDatabase.configurar(registry); }

	@Test
	void contextLoads() {
	}

}
