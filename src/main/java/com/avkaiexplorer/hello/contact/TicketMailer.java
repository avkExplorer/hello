package com.avkaiexplorer.hello.contact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends the two mails for a contact request: the ticket to support (reply-to is the visitor) and the visitor's
 * acknowledgement, rendered by `avk legal render` from the platform's support_ack template.
 */
class TicketMailer {

	private final JavaMailSender sender;
	private final ContactProperties props;

	TicketMailer(JavaMailSender sender, ContactProperties props) {
		this.sender = sender;
		this.props = props;
	}

	void send(String ticket, String name, String email, String message) throws Exception {
		MimeMessage toSupport = sender.createMimeMessage();
		MimeMessageHelper support = new MimeMessageHelper(toSupport, false, "UTF-8");
		support.setFrom(props.mailFrom());
		support.setTo(props.supportTo());
		support.setReplyTo(new InternetAddress(email, name, "UTF-8"));
		support.setSubject("[" + ticket + "] New contact request");
		support.setText("Ticket: " + ticket + "\nFrom: " + name + " <" + email + ">\n\n" + message + "\n", false);
		sender.send(toSupport);

		MimeMessage toVisitor = sender.createMimeMessage();
		MimeMessageHelper ack = new MimeMessageHelper(toVisitor, true, "UTF-8");
		ack.setFrom(props.mailFrom());
		ack.setTo(new InternetAddress(email, name, "UTF-8"));
		ack.setReplyTo(props.supportTo());
		ack.setSubject(fill(resource("mail/support_ack.subject.txt").strip(), ticket, name, false));
		ack.setText(fill(resource("mail/support_ack.txt"), ticket, name, false), fill(resource("mail/support_ack.html"), ticket, name, true));
		sender.send(toVisitor);
	}

	static String fill(String template, String ticket, String name, boolean html) {
		String first = name.strip().split("\\s+")[0];
		return template.replace("@@TICKET_ID@@", html ? HtmlUtils.htmlEscape(ticket) : ticket)
			.replace("@@FIRST_NAME@@", html ? HtmlUtils.htmlEscape(first) : first);
	}

	private static String resource(String path) throws IOException {
		try (InputStream in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
