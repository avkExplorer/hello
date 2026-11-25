package com.avkaiexplorer.hello.auth;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/** Who am I, the profile page, the admin area, and "delete my account". */
@RestController
public class AccountController {

	private final AuthProperties auth;

	private final List<AccountDeletionHook> hooks;

	public AccountController(AuthProperties auth, List<AccountDeletionHook> hooks) {
		this.auth = auth;
		this.hooks = hooks;
	}

	/** The roles come from the Authentication (mapped from Keycloak's realm roles), not from the OidcUser, which keeps Spring's defaults. */
	private static List<String> roles(Authentication authentication) {
		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
	}

	@GetMapping("/api/v1/me")
	public Map<String, Object> me(@AuthenticationPrincipal OidcUser user, Authentication authentication) {
		return Map.of("subject", user.getSubject(), "email", String.valueOf(user.getEmail()),
				"name", String.valueOf(user.getFullName()), "roles", roles(authentication));
	}

	/** A small page with the logout and delete buttons (both are POST with a CSRF token, never plain links). */
	@GetMapping(value = "/profile", produces = MediaType.TEXT_HTML_VALUE)
	public String profile(@AuthenticationPrincipal OidcUser user, Authentication authentication, CsrfToken csrf) {
		String token = HtmlUtils.htmlEscape(csrf.getToken());
		return """
				<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
				<title>Your profile</title></head><body style="font:16px/1.6 system-ui,sans-serif;max-width:32rem;margin:2rem auto;padding:0 1rem">
				<h1>Your profile</h1>
				<p>Signed in as <strong>%s</strong> (%s)</p>
				<p><a href="/account">Manage your login (profile, password, two-step login)</a></p>
				<form method="post" action="/logout"><input type="hidden" name="_csrf" value="%s"><button type="submit">Sign out</button></form>
				<form method="post" action="/account/delete" style="margin-top:1rem"><input type="hidden" name="_csrf" value="%s">
				<button type="submit">Delete my account</button></form>
				</body></html>
				""".formatted(HtmlUtils.htmlEscape(String.valueOf(user.getEmail())),
				HtmlUtils.htmlEscape(String.join(", ", roles(authentication))), token, token);
	}

	/** The person's account console at the login provider (profile, password, two-step login, delete login). */
	@GetMapping("/account")
	public RedirectView account() {
		return new RedirectView(auth.issuer() + "/account/");
	}

	/** Erases the app's own data first, then sends the person to Keycloak, where they delete their login. */
	@PostMapping("/account/delete")
	public RedirectView delete(@AuthenticationPrincipal OidcUser user) {
		for (AccountDeletionHook hook : hooks) {
			hook.deleteDataOf(user.getSubject());
		}
		return new RedirectView(auth.issuer() + "/account/");
	}

	@GetMapping(value = "/admin", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, String> admin() {
		return Map.of("area", "admin");
	}

}
