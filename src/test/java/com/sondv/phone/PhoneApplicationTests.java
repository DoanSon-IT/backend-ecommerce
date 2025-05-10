package com.sondv.phone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PhoneApplicationTests {

	@Test
	void contextLoads() {
	}

	@Bean
	@Primary
	public RedisTemplate<?, ?> redisTemplate() {
		return new RedisTemplate<>();
	}

}
