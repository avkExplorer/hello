package com.avkaiexplorer.hello.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

/** Login behaviour with the OIDC client configured (no Keycloak needed: the endpoints are explicit, nothing is called). */
@SpringBootTest(properties = {
		"avk.auth.issuer=https://auth.example.test/realms/avk",
		"avk.auth.internal-issuer=http://keycloak:8080/realms/avk",
		"avk.auth.client-id=hello",
		"avk.auth.client-secret=test-secret" })
@AutoConfigureMockMvc
@Import(AuthSecurityTest.RecordingHook.class)
class AuthSecurityTest {

	static final List<String> ERASED = new ArrayList<>();

	@TestConfiguration
	static class RecordingHook {

		@Bean
		AccountDeletionHook recorder() {
			return ERASED::add;
		}

	}

	@Autowired
	MockMvc mvc;

	@Autowired
	ClientRegistrationRepository registrations;

	@Test
	void publicPagesNeedNoLogin() throws Exception {
		mvc.perform(get("/")).andExpect(status().isOk());
		mvc.perform(get("/healthz")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
	}

	@Test
	void everythingElseSendsAnonymousVisitorsToTheLogin() throws Exception {
		for (String path : List.of("/admin", "/api/v1/me", "/profile", "/account")) {
			mvc.perform(get(path)).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
		}
	}

	@Test
	void theLoginRedirectUsesThePublicIssuerPkceAndAnExactRedirectAddress() throws Exception {
		String location = mvc.perform(get("/oauth2/authorization/keycloak")).andExpect(status().is3xxRedirection())
			.andReturn().getResponse().getRedirectedUrl();
		assertThat(location).startsWith("https://auth.example.test/realms/avk/protocol/openid-connect/auth?");
		assertThat(location).contains("response_type=code", "client_id=hello", "code_challenge_method=S256", "code_challenge=");
		assertThat(location).contains("redirect_uri=http://localhost/login/oauth2/code/keycloak");
	}

	@Test
	void aNormalUserGets403OnAdminByDirectUrl() throws Exception {
		mvc.perform(get("/admin").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
			.andExpect(status().isForbidden());
	}

	@Test
	void anAdminReachesTheAdminArea() throws Exception {
		mvc.perform(get("/admin").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.area").value("admin"));
	}

	@Test
	void adminSubPathsAreProtectedToo() throws Exception {
		mvc.perform(get("/admin/anything").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
			.andExpect(status().isForbidden());
	}

	@Test
	void meShowsTheSignedInPerson() throws Exception {
		mvc.perform(get("/api/v1/me").with(oidcLogin().idToken(t -> t.subject("abc-123").claim("email", "ann@example.test"))
			.authorities(new SimpleGrantedAuthority("ROLE_USER"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subject").value("abc-123"))
			.andExpect(jsonPath("$.email").value("ann@example.test"))
			.andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
	}

	@Test
	void theProfilePageHasPostButtonsWithACsrfToken() throws Exception {
		String html = mvc.perform(get("/profile").with(oidcLogin().idToken(t -> t.claim("email", "ann<b>@example.test"))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(html).contains("method=\"post\" action=\"/logout\"", "method=\"post\" action=\"/account/delete\"", "name=\"_csrf\"");
		assertThat(html).doesNotContain("<b>");        // the email is escaped, never injected as markup
	}

	@Test
	void logoutNeedsACsrfTokenAndAlsoEndsTheKeycloakSession() throws Exception {
		var keycloak = registrations.findByRegistrationId("keycloak");
		mvc.perform(post("/logout").with(oidcLogin().clientRegistration(keycloak))).andExpect(status().isForbidden());
		String location = mvc.perform(post("/logout").with(oidcLogin().clientRegistration(keycloak)).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andReturn().getResponse().getRedirectedUrl();
		assertThat(location).startsWith("https://auth.example.test/realms/avk/protocol/openid-connect/logout");
		assertThat(location).contains("post_logout_redirect_uri=http://localhost/");
	}

	@Test
	void deletingTheAccountNeedsCsrfRunsTheHooksThenGoesToKeycloak() throws Exception {
		ERASED.clear();
		mvc.perform(post("/account/delete").with(oidcLogin())).andExpect(status().isForbidden());
		assertThat(ERASED).isEmpty();
		mvc.perform(post("/account/delete").with(oidcLogin().idToken(t -> t.subject("sub-9"))).with(csrf()))
			.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("https://auth.example.test/realms/avk/account/"));
		assertThat(ERASED).containsExactly("sub-9");
	}

	@Test
	void theAccountLinkGoesToTheKeycloakAccountConsole() throws Exception {
		mvc.perform(get("/account").with(oidcLogin())).andExpect(redirectedUrl("https://auth.example.test/realms/avk/account/"));
	}

	@Test
	void responsesCarrySecurityHeaders() throws Exception {
		mvc.perform(get("/")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"));
	}

	/** The test resources have their own application.properties, so read the one that ships in the jar. */
	@Test
	void theShippedSessionCookieSettingsAreHttpOnlySameSiteLaxAndSecureByDefault() throws Exception {
		var shipped = new java.util.Properties();
		for (var url : java.util.Collections.list(getClass().getClassLoader().getResources("application.properties"))) {
			try (var in = url.openStream()) {
				var p = new java.util.Properties();
				p.load(in);
				if (p.containsKey("avk.auth.issuer")) {
					shipped = p;
				}
			}
		}
		assertThat(shipped.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
		assertThat(shipped.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
		assertThat(shipped.getProperty("server.servlet.session.cookie.secure")).isEqualTo("${COOKIE_SECURE:true}");
	}

}
