package com.avkaiexplorer.hello.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The contact form's rules, without a mail server: a recording mailer stands in for SMTP. */
class ContactControllerTest {

	static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);

	static class RecordingMailer extends TicketMailer {

		final List<String> sent = new ArrayList<>();
		boolean fail;

		RecordingMailer(ContactProperties props) {
			super(null, props);
		}

		@Override
		void send(String ticket, String name, String email, String message) throws Exception {
			if (fail) {
				throw new IllegalStateException("smtp down: secret-detail");
			}
			sent.add(ticket + "|" + name + "|" + email + "|" + message);
		}

	}

	static ContactProperties props(String smtpHost) {
		return new ContactProperties(smtpHost, 25, false, "", "", "noreply@notify.example", "support@example.test", "HEL", "", 5);
	}

	static MockHttpServletRequestBuilder valid() {
		return post("/contact").param("name", "Ann Lee").param("email", "ann@example.test")
			.param("message", "Hello, I have a question.").contentType("application/x-www-form-urlencoded");
	}

	MockMvc mvc(ContactController controller) {
		return MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void aValidMessageGetsATicketAndSendsBothMails() throws Exception {
		var mailer = new RecordingMailer(props("smtp"));
		var body = mvc(new ContactController(props("smtp"), CLOCK, mailer)).perform(valid()).andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("HEL-20261125-")))
			.andReturn().getResponse().getContentAsString();
		assertThat(mailer.sent).hasSize(1);
		assertThat(mailer.sent.get(0)).contains("Ann Lee|ann@example.test|Hello, I have a question.");
		assertThat(body).matches("(?s).*HEL-20261125-[A-Z2-9]{6}.*");
	}

	@Test
	void theHiddenFieldCatchesBotsWithoutSendingAnything() throws Exception {
		var mailer = new RecordingMailer(props("smtp"));
		mvc(new ContactController(props("smtp"), CLOCK, mailer)).perform(valid().param("website", "http://spam.example"))
			.andExpect(status().isOk());
		assertThat(mailer.sent).isEmpty();
	}

	@Test
	void badInputIsRejectedOnTheServer() throws Exception {
		var mailer = new RecordingMailer(props("smtp"));
		var mvc = mvc(new ContactController(props("smtp"), CLOCK, mailer));
		String form = "application/x-www-form-urlencoded";
		mvc.perform(post("/contact").contentType(form).param("name", "").param("email", "a@b.test").param("message", "long enough message"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/contact").contentType(form).param("name", "A\r\nBcc: x@y.test").param("email", "a@b.test").param("message", "long enough message"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/contact").contentType(form).param("name", "Ann").param("email", "not-an-email").param("message", "long enough message"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/contact").contentType(form).param("name", "Ann").param("email", "a@b.test").param("message", "short"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/contact").contentType(form).param("name", "Ann").param("email", "a@b.test").param("message", "x".repeat(4001)))
			.andExpect(status().isBadRequest());
		assertThat(mailer.sent).isEmpty();
	}

	@Test
	void theSixthMessageInAMinuteIsRateLimited() throws Exception {
		var mailer = new RecordingMailer(props("smtp"));
		var mvc = mvc(new ContactController(props("smtp"), CLOCK, mailer));
		for (int i = 0; i < 5; i++) {
			mvc.perform(valid()).andExpect(status().isOk());
		}
		mvc.perform(valid()).andExpect(status().isTooManyRequests());
		assertThat(mailer.sent).hasSize(5);
	}

	@Test
	void withoutMailSettingsTheFormSaysSoInsteadOfLosingTheMessage() throws Exception {
		mvc(new ContactController(props(""), CLOCK, null)).perform(valid()).andExpect(status().isServiceUnavailable())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("support@avkaiexplorer.com")));
	}

	@Test
	void aMailFailureShowsAFriendlyErrorAndLeaksNothing() throws Exception {
		var mailer = new RecordingMailer(props("smtp"));
		mailer.fail = true;
		mvc(new ContactController(props("smtp"), CLOCK, mailer)).perform(valid()).andExpect(status().isBadGateway())
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-detail"))));
	}

	@Test
	void getContactSendsVisitorsToThePage() throws Exception {
		mvc(new ContactController(props("smtp"), CLOCK, null)).perform(get("/contact")).andExpect(status().isSeeOther())
			.andExpect(header().string("Location", "/legal/contact.html"));
	}

	@Test
	void theAcknowledgementFillsInTheFirstNameAndEscapesHtml() {
		String html = TicketMailer.fill("Hi @@FIRST_NAME@@, request @@TICKET_ID@@", "HEL-1", "<b>Ann</b> Lee", true);
		assertThat(html).isEqualTo("Hi &lt;b&gt;Ann&lt;/b&gt;, request HEL-1");
		assertThat(TicketMailer.fill("Hi @@FIRST_NAME@@", "T", "Ann Lee", false)).isEqualTo("Hi Ann");
	}

	@Test
	void theRenderedAcknowledgementTemplateHasBothPlaceholders() throws Exception {
		for (String file : new String[] { "mail/support_ack.txt", "mail/support_ack.html" }) {
			try (var in = getClass().getClassLoader().getResourceAsStream(file)) {
				String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				assertThat(text).contains("@@TICKET_ID@@");
			}
		}
	}

	@Test
	void rateLimiterForgetsAfterAMinute() {
		var now = new long[] { 0 };
		Clock clock = new Clock() {
			public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
			public Clock withZone(java.time.ZoneId z) { return this; }
			public Instant instant() { return Instant.ofEpochMilli(now[0]); }
		};
		var limiter = new RateLimiter(clock, 2);
		assertThat(limiter.allow("a")).isTrue();
		assertThat(limiter.allow("a")).isTrue();
		assertThat(limiter.allow("a")).isFalse();
		assertThat(limiter.allow("b")).isTrue();
		now[0] = 61_000;
		assertThat(limiter.allow("a")).isTrue();
	}

}
