package com.example.spring_stream_backend;

import com.example.spring_stream_backend.services.VideoServices;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
class SpringStreamBackendApplicationTests {
	@Autowired
	VideoServices videoServices;
	@Test
	void contextLoads() throws IOException {
		videoServices.processVideo("e1e704f6-feeb-423b-b6f6-6a4d0c01908f");
	}
}
