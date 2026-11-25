package com.avkaiexplorer.hello.contact;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Contact form settings, all from the environment (`avk legal smtp` writes the SMTP_* values into the app's secrets).
 * With no SMTP host the form answers 503 and points to the support address instead of losing messages.
 */
@ConfigurationProperties(prefix = "avk.contact")
public record ContactProperties(String smtpHost, int smtpPort, boolean smtpSsl, String smtpUsername, String smtpPassword,
		String mailFrom, String supportTo, String ticketPrefix, String turnstileSecret, int perMinute) {

	public boolean mailConfigured() {
		return smtpHost != null && !smtpHost.isBlank() && supportTo != null && !supportTo.isBlank();
	}

	public boolean turnstileEnabled() {
		return turnstileSecret != null && !turnstileSecret.isBlank();
	}

	public int limit() {
		return perMinute > 0 ? perMinute : 5;
	}

}
