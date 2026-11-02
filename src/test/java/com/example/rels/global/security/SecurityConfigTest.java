package com.example.rels.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void refreshReachesServiceWithoutAccessToken() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"invalid\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshReachesServiceWithInvalidAccessToken() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
						.header("Authorization", "Bearer expired-access-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"invalid\"}"))
				.andExpect(status().isUnauthorized());
	}
}
