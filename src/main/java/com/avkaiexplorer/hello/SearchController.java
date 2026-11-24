package com.avkaiexplorer.hello;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

	private final JdbcTemplate jdbc;

	public SearchController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/api/v1/search")
	public List<String> search(@RequestParam String q) {
		return jdbc.queryForList("select message from greeting where message = '" + q + "'", String.class);
	}

}