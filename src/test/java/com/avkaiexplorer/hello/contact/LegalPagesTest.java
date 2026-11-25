package com.avkaiexplorer.hello.contact;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The generated legal pages are public, carry the draft banner, and the contact form works without a login or CSRF token. */
@SpringBootTest
@AutoConfigureMockMvc
class LegalPagesTest {

	@Autowired
	MockMvc mvc;

	@Test
	void everyPolicyPageIsPublicAndMarkedDraft() throws Exception {
		for (String page : new String[] { "terms", "privacy", "cookies", "accessibility", "contact" }) {
			mvc.perform(get("/legal/" + page + ".html")).andExpect(status().isOk())
				.andExpect(content().string(containsString("DRAFT")))
				.andExpect(content().string(containsString("not legal advice")));
		}
	}

	@Test
	void securityTxtIsPublic() throws Exception {
		mvc.perform(get("/.well-known/security.txt")).andExpect(status().isOk())
			.andExpect(content().string(containsString("mailto:security@avkaiexplorer.com")));
	}

	@Test
	void theHomePageLinksToThePolicyPages() throws Exception {
		mvc.perform(get("/index.html")).andExpect(content().string(containsString("/legal/privacy.html")))
			.andExpect(content().string(containsString("Cookie settings")));
	}

	@Test
	void anOldAddressIsRedirectedPermanentlyBeforeLoginIsRequired() throws Exception {
		mvc.perform(get("/privacy")).andExpect(status().isMovedPermanently())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", "/legal/privacy.html"));
	}

	@Test
	void theContactFormNeedsNeitherLoginNorCsrfToken() throws Exception {
		// No SMTP host in the test environment: the form answers 503 (not 401 or 403), which proves it was reached.
		mvc.perform(post("/contact").contentType("application/x-www-form-urlencoded").param("name", "Ann")
			.param("email", "ann@example.test").param("message", "A real question here")).andExpect(status().isServiceUnavailable());
	}

}
