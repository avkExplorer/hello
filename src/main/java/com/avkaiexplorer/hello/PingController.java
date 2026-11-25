package com.avkaiexplorer.hello;

import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

	private final JdbcClient jdbc;

	public PingController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Returns the message seeded by the first Flyway migration, so a 200 proves the schema is in place. */
	@GetMapping("/api/v1/ping")
	public Map<String, String> ping() {
		// PROOF 4 ONLY (never merge to a real release): needs the TEMP privilege, which prod's least-privilege
		// role does not have, so it works in unit tests and the ci stack (superuser) but returns 500 in production.
		jdbc.sql("create temporary table ping_probe (x int)").update();
		String message = jdbc.sql("select message from greeting order by id limit 1").query(String.class).single();
		return Map.of("message", message);
	}

}
