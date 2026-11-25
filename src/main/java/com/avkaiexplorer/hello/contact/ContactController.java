package com.avkaiexplorer.hello.contact;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * The contact form's server side: validates, rate-limits (5 per minute per address), optionally checks Cloudflare
 * Turnstile, then mails a ticket to support and an acknowledgement to the visitor. A hidden "website" field catches
 * simple bots. Nothing the visitor typed is logged; only the ticket id is.
 */
@RestController
@EnableConfigurationProperties(ContactProperties.class)
public class ContactController {

	private static final Logger log = LoggerFactory.getLogger(ContactController.class);
	private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>\"',;]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,}$");
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";

	private final ContactProperties props;
	private final RateLimiter limiter;
	private final TicketMailer mailer;
	private final SecureRandom random = new SecureRandom();
	private final Clock clock;

	@Autowired
	public ContactController(ContactProperties props) {
		this(props, Clock.systemUTC(), props.mailConfigured() ? new TicketMailer(sender(props), props) : null);
	}

	ContactController(ContactProperties props, Clock clock, TicketMailer mailer) {
		this.props = props;
		this.clock = clock;
		this.limiter = new RateLimiter(clock, props.limit());
		this.mailer = mailer;
	}

	private static JavaMailSenderImpl sender(ContactProperties p) {
		JavaMailSenderImpl s = new JavaMailSenderImpl();
		s.setHost(p.smtpHost());
		s.setPort(p.smtpPort() > 0 ? p.smtpPort() : 25);
		s.setDefaultEncoding("UTF-8");
		Properties mail = s.getJavaMailProperties();
		mail.put("mail.smtp.connectiontimeout", "5000");
		mail.put("mail.smtp.timeout", "8000");
		mail.put("mail.smtp.writetimeout", "8000");
		if (p.smtpSsl()) {
			mail.put("mail.smtp.ssl.enable", "true");
		}
		if (p.smtpUsername() != null && !p.smtpUsername().isBlank()) {
			s.setUsername(p.smtpUsername());
			s.setPassword(p.smtpPassword());
			mail.put("mail.smtp.auth", "true");
		}
		return s;
	}

	@GetMapping("/contact")
	public ResponseEntity<Void> form() {
		return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/legal/contact.html")).build();
	}

	@PostMapping(path = "/contact", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public ResponseEntity<String> submit(@RequestParam Map<String, String> form, HttpServletRequest request) {
		if (!limiter.allow(request.getRemoteAddr())) {
			return page(HttpStatus.TOO_MANY_REQUESTS, "Too many messages", "Please wait a minute and try again.");
		}
		if (!form.getOrDefault("website", "").isBlank()) {
			return page(HttpStatus.OK, "Thank you", "Your message was received.");   // a bot filled the hidden field: pretend
		}
		String name = form.getOrDefault("name", "").strip();
		String email = form.getOrDefault("email", "").strip();
		String message = form.getOrDefault("message", "").strip();
		if (name.isEmpty() || name.length() > 100 || containsLineBreak(name)) {
			return invalid("Please enter your name (up to 100 characters).");
		}
		if (!EMAIL.matcher(email).matches() || email.length() > 200) {
			return invalid("Please enter a valid email address.");
		}
		if (message.length() < 10 || message.length() > 4000) {
			return invalid("Please write a message of 10 to 4000 characters.");
		}
		if (props.turnstileEnabled() && !turnstileOk(form.getOrDefault("cf-turnstile-response", ""), request.getRemoteAddr())) {
			return invalid("We could not confirm you are a person. Please try again.");
		}
		if (mailer == null) {
			return page(HttpStatus.SERVICE_UNAVAILABLE, "Contact form unavailable",
					"Please email us at support@avkaiexplorer.com instead.");
		}
		String ticket = ticket();
		try {
			mailer.send(ticket, name, email, message);
		}
		catch (Exception ex) {
			log.error("contact ticket {} could not be mailed: {}", ticket, ex.getClass().getSimpleName());
			return page(HttpStatus.BAD_GATEWAY, "Message not sent",
					"Something went wrong sending your message. Please email support@avkaiexplorer.com.");
		}
		log.info("contact ticket {} created", ticket);
		return page(HttpStatus.OK, "Thank you", "Your request number is <strong>" + HtmlUtils.htmlEscape(ticket)
				+ "</strong>. We sent a confirmation to your email.");
	}

	String ticket() {
		StringBuilder id = new StringBuilder(props.ticketPrefix() == null || props.ticketPrefix().isBlank() ? "TKT" : props.ticketPrefix());
		id.append('-').append(LocalDate.now(clock).toString().replace("-", "")).append('-');
		for (int i = 0; i < 6; i++) {
			id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return id.toString();
	}

	private boolean turnstileOk(String token, String ip) {
		if (token.isBlank() || token.length() > 2048) {
			return false;
		}
		try {
			String body = "secret=" + enc(props.turnstileSecret()) + "&response=" + enc(token) + "&remoteip=" + enc(ip);
			HttpRequest req = HttpRequest.newBuilder(URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
				.timeout(Duration.ofSeconds(5)).header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build();
			HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
			return res.statusCode() == 200 && res.body().replace(" ", "").contains("\"success\":true");
		}
		catch (Exception ex) {
			log.warn("turnstile check failed: {}", ex.getClass().getSimpleName());
			return false;
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static boolean containsLineBreak(String s) {
		return s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
	}

	private ResponseEntity<String> invalid(String text) {
		return page(HttpStatus.BAD_REQUEST, "Please check your message", HtmlUtils.htmlEscape(text));
	}

	/** A tiny complete page; `html` is already safe. */
	private static ResponseEntity<String> page(HttpStatus status, String title, String html) {
		String doc = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
				+ "<title>" + title + "</title><link rel=\"stylesheet\" href=\"/legal/legal.css\"></head><body>"
				+ "<main id=\"main\"><h1>" + title + "</h1><p>" + html + "</p><p><a href=\"/legal/contact.html\">Back to the contact page</a></p></main></body></html>";
		return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(doc);
	}

}
