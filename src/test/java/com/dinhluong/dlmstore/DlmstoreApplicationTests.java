package com.dinhluong.dlmstore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class DlmstoreApplicationTests extends com.dinhluong.dlmstore.chatbot.IsolatedChatbotDatabase {

    @org.springframework.boot.test.mock.mockito.MockBean
    com.dinhluong.dlmstore.config.ProductDataStartupRunner productRunner;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "initUsers")
    org.springframework.boot.CommandLineRunner initUsers;

	@Test
	void contextLoads() {
	}

}
