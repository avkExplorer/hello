package com.avkaiexplorer.hello.auth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Login with the shared Keycloak (OIDC authorization code + PKCE), a server-side session in an HttpOnly cookie, CSRF
 * protection, role mapping and a server-enforced /admin. Copied from the platform's app template (app-template/auth-starter).
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

	static final String REGISTRATION = "keycloak";

	/** The client registration exists only when a client id is configured. Endpoints are explicit: no discovery call at start-up. */
	@Bean
	@ConditionalOnExpression("!'${avk.auth.client-id:}'.isBlank()")
	ClientRegistrationRepository clientRegistrations(AuthProperties auth) {
		String publicBase = auth.issuer() + "/protocol/openid-connect";
		String internalBase = auth.backchannel() + "/protocol/openid-connect";
		ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION)
			.clientName("AVK login")
			.clientId(auth.clientId())
			.clientSecret(auth.clientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.scope("openid", "profile", "email")
			.authorizationUri(publicBase + "/auth")                     // the browser goes here
			.tokenUri(internalBase + "/token")                          // the app calls these from inside the network
			.jwkSetUri(internalBase + "/certs")
			.userInfoUri(internalBase + "/userinfo")
			.issuerUri(auth.issuer())                                   // the ID token's issuer must be exactly this
			.userNameAttributeName("preferred_username")
			.providerConfigurationMetadata(Map.of("end_session_endpoint", publicBase + "/logout"))
			.build();
		return new InMemoryClientRegistrationRepository(registration);
	}

	@Bean
	GrantedAuthoritiesMapper keycloakRoles() {
		return authorities -> {
			Set<GrantedAuthority> mapped = new HashSet<>();
			for (GrantedAuthority authority : authorities) {
				if (authority instanceof OidcUserAuthority oidc) {
					Map<String, Object> claims = new HashMap<>(oidc.getIdToken().getClaims());
					if (oidc.getUserInfo() != null) {
						claims.putAll(oidc.getUserInfo().getClaims());
					}
					mapped.addAll(Roles.from(claims));
				}
			}
			return mapped;
		};
	}

	/** Web only: `--migrate-only` starts the app without a web server, and then there is no HttpSecurity to configure. */
	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> registrations,
			GrantedAuthoritiesMapper roleMapper, @Value("${management.server.port:-1}") int managementPort) throws Exception {
		http.authorizeHttpRequests(requests -> requests
			// Metrics for Prometheus: only on the separate management port (never published, never routed by Traefik), only this endpoint.
			.requestMatchers(request -> managementPort > 0 && request.getLocalPort() == managementPort
					&& "/actuator/prometheus".equals(request.getRequestURI())).permitAll()
			.requestMatchers("/", "/index.html", "/healthz", "/readyz", "/api/v1/ping", "/error", "/favicon.ico").permitAll()
			.requestMatchers("/legal/**", "/.well-known/security.txt", "/contact").permitAll()    // policy pages and the contact form (Phase 6)
			.requestMatchers("/admin", "/admin/**").hasRole("ADMIN")      // enforced here, on the server, never in the page
			.anyRequest().authenticated());
		// The contact form is a plain static page that cannot carry a CSRF token; it is rate-limited and bot-checked instead.
		http.csrf(csrf -> csrf.ignoringRequestMatchers("/contact"));

		ClientRegistrationRepository repository = registrations.getIfAvailable();
		if (repository == null) {
			http.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
			return http.build();
		}

		// Keycloak's client demands PKCE (S256); Spring only adds it by default for public clients, so add it explicitly.
		var resolver = new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization");
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

		var logout = new OidcClientInitiatedLogoutSuccessHandler(repository);
		logout.setPostLogoutRedirectUri("{baseUrl}/");                    // also ends the Keycloak session

		// The role mapper is attached explicitly: relying on Spring finding the bean left the default authorities in place.
		http.oauth2Login(login -> login
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
				.userInfoEndpoint(info -> info.userAuthoritiesMapper(roleMapper)))
			.logout(l -> l.logoutSuccessHandler(logout));
		return http.build();
	}

}
