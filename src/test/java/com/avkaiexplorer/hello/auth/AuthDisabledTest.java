package com.avkaiexplorer.hello.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** With no OIDC client configured (the ci stack, or an app that has no login yet) the app runs with public pages only. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthDisabledTest {

	@Autowired
	MockMvc mvc;

	@Test
	void publicPagesStillWork() throws Exception {
		mvc.perform(get("/")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
		mvc.perform(get("/readyz")).andExpect(status().isOk());
	}

	@Test
	void protectedPathsAnswer401NotARedirectToNowhere() throws Exception {
		for (String path : new String[] { "/admin", "/api/v1/me", "/profile" }) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
	}

}
