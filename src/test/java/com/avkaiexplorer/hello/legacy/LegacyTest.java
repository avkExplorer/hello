package com.avkaiexplorer.hello.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LegacyTest {

	@Test
	void anOldPathGetsAPermanentRedirect() throws Exception {
		var filter = new RedirectsFilter(Map.of("/old-page", "/legal/privacy.html"));
		var response = new MockHttpServletResponse();
		var chain = new MockFilterChain();
		filter.doFilter(new MockHttpServletRequest("GET", "/old-page"), response, chain);
		assertThat(response.getStatus()).isEqualTo(301);
		assertThat(response.getHeader("Location")).isEqualTo("/legal/privacy.html");
		assertThat(chain.getRequest()).isNull();             // the request never reached the app
	}

	@Test
	void otherPathsAndMethodsPassThrough() throws Exception {
		var filter = new RedirectsFilter(Map.of("/old-page", "/new"));
		for (var request : List.of(new MockHttpServletRequest("GET", "/other"), new MockHttpServletRequest("POST", "/old-page"))) {
			var response = new MockHttpServletResponse();
			var chain = new MockFilterChain();
			filter.doFilter(request, response, chain);
			assertThat(chain.getRequest()).isNotNull();
			assertThat(response.getStatus()).isEqualTo(200);
		}
	}

	@Test
	void theFileFormatSkipsCommentsAndHeader() {
		assertThat(RedirectsFilter.parse(List.of("old,new", "# moved in 2026", "", "/a,/b", " /c , /d "))).isEqualTo(Map.of("/a", "/b", "/c", "/d"));
	}

	@Test
	void offSiteTargetsLoopsAndGarbageAreRefusedAtStartUp() {
		for (String bad : List.of("/a,https://evil.example/", "/a,//evil.example", "a,/b", "/a", "/a,/b,/c", "/a,/a", "/a,/b\\c")) {
			assertThatThrownBy(() -> RedirectsFilter.parse(List.of(bad))).as(bad).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void sunsetHeadersFollowRfc8594() {
		var response = new MockHttpServletResponse();
		SunsetHeaders.apply(response, LocalDate.of(2027, 6, 30), "/api/v2/ping");
		assertThat(response.getHeader("Sunset")).isEqualTo("Wed, 30 Jun 2027 00:00:00 GMT");
		assertThat(response.getHeader("Deprecation")).isEqualTo("true");
		assertThat(response.getHeader("Link")).isEqualTo("</api/v2/ping>; rel=\"successor-version\"");
		assertThatThrownBy(() -> SunsetHeaders.apply(response, LocalDate.now(), "https://evil.example")).isInstanceOf(IllegalArgumentException.class);
	}

}
