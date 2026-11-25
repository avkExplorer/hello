package com.avkaiexplorer.hello.legacy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Old address -> new address, answered with a permanent redirect (301) before login or anything else runs, so bookmarks and
 * search results keep working after a page moves. The list is `redirects.csv` on the classpath (one `old,new` pair per line,
 * `#` for comments), read once at start-up. Only same-site paths are allowed on both sides, so it cannot become an open redirect.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RedirectsFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RedirectsFilter.class);

	private final Map<String, String> redirects;

	public RedirectsFilter() {
		this.redirects = load(new ClassPathResource("redirects.csv"));
	}

	RedirectsFilter(Map<String, String> redirects) {
		this.redirects = redirects;
	}

	static Map<String, String> load(ClassPathResource resource) {
		if (!resource.exists()) {
			return Map.of();
		}
		try (InputStream in = resource.getInputStream()) {
			return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
		}
		catch (IOException ex) {
			throw new IllegalStateException("redirects.csv could not be read", ex);
		}
	}

	static Map<String, String> parse(Iterable<String> lines) {
		Map<String, String> map = new HashMap<>();
		int number = 0;
		for (String raw : lines) {
			number++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#") || line.equalsIgnoreCase("old,new")) {
				continue;
			}
			String[] parts = line.split(",", -1);
			if (parts.length != 2 || !samePath(parts[0].strip()) || !samePath(parts[1].strip())) {
				throw new IllegalStateException("redirects.csv line " + number + ": expected `/old-path,/new-path` (same-site paths only)");
			}
			if (parts[0].strip().equals(parts[1].strip())) {
				throw new IllegalStateException("redirects.csv line " + number + ": a path cannot redirect to itself");
			}
			map.put(parts[0].strip(), parts[1].strip());
		}
		return Map.copyOf(map);
	}

	private static boolean samePath(String p) {
		return p.startsWith("/") && !p.startsWith("//") && !p.contains("\\") && !p.contains("://") && p.chars().noneMatch(Character::isISOControl);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String target = redirects.get(request.getRequestURI());
		if (target != null && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
			log.debug("redirecting {} -> {}", request.getRequestURI(), target);
			response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
			response.setHeader("Location", target);
			return;
		}
		chain.doFilter(request, response);
	}

}
