package com.avkaiexplorer.hello.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id and writes one access log line for it. The id is the caller's {@code X-Request-Id}, else
 * Cloudflare's {@code Cf-Ray}, else a new one, and is returned in the {@code X-Request-Id} response header and put in the log
 * context ({@code request_id}) so every log line of the request carries it. The access line records the method, the path
 * WITHOUT its query string, the status, how long it took, and the signed-in person's id ({@code user_id}: the login subject, never
 * the email). Runs before Spring Security so even rejected requests get an id and a line.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Request-Id";
	private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");
	private static final Logger access = LoggerFactory.getLogger("access");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = choose(request.getHeader(HEADER), request.getHeader("Cf-Ray"));
		MDC.put("request_id", id);
		response.setHeader(HEADER, id);
		long started = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			String user = userId(request);
			if (user != null) {
				MDC.put("user_id", user);
			}
			if (!isProbe(request.getRequestURI())) {
				access.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
						(System.nanoTime() - started) / 1_000_000);
			}
			MDC.remove("request_id");
			MDC.remove("user_id");
		}
	}

	static String choose(String incoming, String cfRay) {
		if (incoming != null && SAFE.matcher(incoming).matches()) {
			return incoming;
		}
		if (cfRay != null && SAFE.matcher(cfRay).matches()) {
			return cfRay;
		}
		return UUID.randomUUID().toString();
	}

	/** The login subject from the session, if the person is signed in. Never the email address. */
	static String userId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return null;
		}
		Object context = session.getAttribute("SPRING_SECURITY_CONTEXT");
		if (context instanceof SecurityContext sc) {
			Authentication auth = sc.getAuthentication();
			if (auth != null && auth.getPrincipal() instanceof OidcUser user) {
				return user.getSubject();
			}
		}
		return null;
	}

	private static boolean isProbe(String path) {
		return "/healthz".equals(path) || "/readyz".equals(path);
	}

}
