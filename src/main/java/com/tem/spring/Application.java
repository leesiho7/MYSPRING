package com.tem.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@EnableAsync
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		loadEnvIfPresent();
		SpringApplication.run(Application.class, args);
	}

	private static void loadEnvIfPresent() {
		Path envPath = Paths.get(".env");
		if (Files.exists(envPath)) {
			try {
				Files.readAllLines(envPath).forEach(line -> {
					String trimmed = line.trim();
					if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
						int idx = trimmed.indexOf('=');
						String key = trimmed.substring(0, idx).trim();
						String value = trimmed.substring(idx + 1).trim();
						if (System.getProperty(key) == null && System.getenv(key) == null) {
							System.setProperty(key, value);
						}
					}
				});
			} catch (IOException ignored) {
			}
		}
	}

}
