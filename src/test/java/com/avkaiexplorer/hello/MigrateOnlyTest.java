package com.avkaiexplorer.hello;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class MigrateOnlyTest {

	@Test
	void migrateOnlyStartsWithoutAWebServerAndExits() {
		// Uses the in-memory database from src/test/resources; a web server would bind a port and never return.
		assertThatCode(() -> HelloApplication.main(new String[] { HelloApplication.MIGRATE_ONLY })).doesNotThrowAnyException();
	}

}
