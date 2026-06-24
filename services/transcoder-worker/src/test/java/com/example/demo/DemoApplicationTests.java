package com.example.demo;

import com.example.demo.service.VideoProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class DemoApplicationTests {
	@Autowired
	private VideoProcessingService processingService;

	@Test
	void contextLoads() {
		processingService.process("7368330d-c9e3-4dd3-a963-112324dd005b_15126998_2160_3840_60fps.mp4");
	}
}
