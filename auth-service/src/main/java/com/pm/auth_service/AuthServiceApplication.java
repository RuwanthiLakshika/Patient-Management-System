package com.pm.auth_service;

import com.pm.auth_service.model.User;
import com.pm.auth_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

	@Bean
	CommandLineRunner seedDevelopmentUser(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${auth.seed.email:testuser@test.com}") String email,
			@Value("${auth.seed.password:password123}") String password) {
		return args -> {
			if (userRepository.findByEmail(email).isEmpty()) {
				User user = new User();
				user.setEmail(email);
				user.setPassword(passwordEncoder.encode(password));
				user.setRole("ADMIN");
				userRepository.save(user);
			}
		};
	}

}
