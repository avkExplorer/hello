package com.avkaiexplorer.hello.legacy;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Marks an API version as going away (RFC 8594 Sunset and the Deprecation header): call it from the old version's
 * handlers. Clients and monitors see the date and where to go, long before the old version stops answering.
 *
 * <pre>SunsetHeaders.apply(response, LocalDate.of(2027, 6, 30), "/api/v2/ping");</pre>
 */
public final class SunsetHeaders {

	private SunsetHeaders() {
	}

	public static void apply(HttpServletResponse response, LocalDate sunset, String successorPath) {
		if (successorPath == null || !successorPath.startsWith("/") || successorPath.startsWith("//")) {
			throw new IllegalArgumentException("successor must be a same-site path");
		}
		response.setHeader("Deprecation", "true");
		response.setHeader("Sunset", DateTimeFormatter.RFC_1123_DATE_TIME.format(sunset.atStartOfDay(ZoneOffset.UTC)));
		response.addHeader("Link", "<" + successorPath + ">; rel=\"successor-version\"");
	}

}
