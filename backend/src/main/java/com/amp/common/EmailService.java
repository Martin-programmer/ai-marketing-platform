package com.amp.common;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Email service using Resend.
 * <p>
 * When {@code email.enabled=false} (local dev), emails are logged but not sent.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailProperties props;
    private final RestTemplate restTemplate;

    public EmailService(EmailProperties props) {
        this.props = props;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    void init() {
        if (props.isEnabled()) {
            log.info("EmailService initialized with Resend apiUrl={}", props.getApiUrl());
        } else {
            log.info("EmailService is DISABLED — emails will be logged only");
        }
        if ("placeholder".equals(props.getApiKey())) {
            log.warn("EmailService is configured with placeholder Resend API key — sends will be skipped");
        }
    }

    /**
     * Send a plain HTML email.
     */
    public void sendEmail(String to, String subject, String htmlBody) {
        String wrappedHtml = wrapInTemplate(htmlBody);

        log.info("Sending email to={} subject=\"{}\" enabled={}", to, subject, props.isEnabled());

        if (!props.isEnabled()) {
            log.info("Email skipped because email.enabled=false to={} subject=\"{}\" bodyLength={}",
                to, subject, wrappedHtml.length());
            log.debug("EMAIL body:\n{}", wrappedHtml);
            return;
        }

        if (props.getApiKey() == null || props.getApiKey().isBlank()
            || "placeholder".equals(props.getApiKey())) {
            log.warn("Email skipped because Resend API key is missing/placeholder to={} subject=\"{}\"",
                to, subject);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(props.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = Map.of(
                "from", props.getFromName() + " <" + props.getFromAddress() + ">",
                "to", List.of(to),
                "subject", subject,
                "html", wrappedHtml
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            String endpoint = props.getApiUrl().replaceAll("/+$", "") + "/emails";

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(endpoint, request, JsonNode.class);
            String emailId = response.getBody() != null && response.getBody().has("id")
                ? response.getBody().get("id").asText()
                : "unknown";
            log.info("Email sent successfully to={} subject=\"{}\" provider=resend emailId={}",
                to, subject, emailId);
        } catch (Exception e) {
            log.error("Failed to send email to={} subject=\"{}\": {}", to, subject, e.getMessage(), e);
            // Don't re-throw — email failure shouldn't block business logic
        }
    }

    /**
     * Send a templated email with variable substitution.
     */
    public void sendTemplatedEmail(String to, String subject, String templateName,
                                   Map<String, String> variables) {
        String htmlBody = loadTemplate(templateName, variables);
        sendEmail(to, subject, htmlBody);
    }

    /**
     * Load an inline template and substitute variables.
     */
    private String loadTemplate(String templateName, Map<String, String> variables) {
        String template = switch (templateName) {
            case "invitation" -> INVITATION_TEMPLATE;
            case "password-reset" -> PASSWORD_RESET_TEMPLATE;
            case "welcome" -> WELCOME_TEMPLATE;
            case "report-sent" -> REPORT_SENT_TEMPLATE;
            case "alert" -> ALERT_TEMPLATE;
            case "campaign-published" -> CAMPAIGN_PUBLISHED_TEMPLATE;
            default -> throw new IllegalArgumentException("Unknown email template: " + templateName);
        };

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    /**
     * Wrap content in a professional email template.
     */
    private String wrapInTemplate(String content) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0; padding:0; background-color:#f5f5f5; font-family:Arial,Helvetica,sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f5f5f5;">
                    <tr>
                        <td align="center" style="padding:40px 0;">
                            <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1);">
                                <!-- Header -->
                                <tr>
                                    <td style="background:linear-gradient(135deg,#1565C0,#0D47A1); padding:30px; text-align:center;">
                                        <h1 style="color:#ffffff; margin:0; font-size:24px; font-weight:bold;">
                                            🚀 AI Marketing Platform
                                        </h1>
                                    </td>
                                </tr>
                                <!-- Content -->
                                <tr>
                                    <td style="padding:30px;">
                                        %s
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color:#f9f9f9; padding:20px 30px; text-align:center; border-top:1px solid #eeeeee;">
                                        <p style="color:#999999; font-size:12px; margin:0;">
                                            © 2026 Adverion. All rights reserved.<br>
                                            This email was sent by AI Marketing Platform.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(content);
    }

    // ── Inline Templates ─────────────────────────────────────────

    private static final String INVITATION_TEMPLATE = """
        <h2 style="color:#333333; margin-top:0;">You've Been Invited! 🎉</h2>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            You've been invited to <strong>AI Marketing Platform</strong> by <strong>{{agencyName}}</strong>.
        </p>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            Your role: <span style="background-color:#E3F2FD; padding:2px 8px; border-radius:4px; font-weight:bold;">{{role}}</span>
        </p>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            Click the button below to activate your account and set your password:
        </p>
        <div style="text-align:center; margin:30px 0;">
            <a href="{{activationLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
                Activate My Account
            </a>
        </div>
        <p style="color:#999999; font-size:13px;">
            This invitation expires in 72 hours. If you didn't expect this invitation, you can safely ignore this email.
        </p>
        <p style="color:#999999; font-size:12px;">
            If the button doesn't work, copy and paste this link:<br>
            <a href="{{activationLink}}" style="color:#1565C0;">{{activationLink}}</a>
        </p>
        """;

    private static final String PASSWORD_RESET_TEMPLATE = """
        <h2 style="color:#333333; margin-top:0;">Password Reset 🔑</h2>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            We received a request to reset your password for AI Marketing Platform.
        </p>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            Click the button below to set a new password:
        </p>
        <div style="text-align:center; margin:30px 0;">
            <a href="{{resetLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
                Reset Password
            </a>
        </div>
        <p style="color:#999999; font-size:13px;">
            This link expires in 1 hour. If you didn't request a password reset, you can safely ignore this email.
        </p>
        <p style="color:#999999; font-size:12px;">
            If the button doesn't work, copy and paste this link:<br>
            <a href="{{resetLink}}" style="color:#1565C0;">{{resetLink}}</a>
        </p>
        """;

    // ── Welcome Template ─────────────────────────────────────────

    private static final String WELCOME_TEMPLATE = """
        <h2 style="color:#333333; margin-top:0;">Welcome to AI Marketing Platform! 🎉</h2>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            Hi <strong>{{displayName}}</strong>,
        </p>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            {{welcomeMessage}}
        </p>
        <div style="text-align:center; margin:30px 0;">
            <a href="{{loginLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
                Go to Dashboard
            </a>
        </div>
        <p style="color:#999999; font-size:13px;">
            If you have any questions, reach out to your account manager.
        </p>
        """;

    // ── Report Sent Template ─────────────────────────────────────

    private static final String REPORT_SENT_TEMPLATE = """
        <h2 style="color:#333333; margin-top:0;">📊 Performance Report</h2>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            A new performance report is available for <strong>{{clientName}}</strong>.
        </p>
        <p style="color:#555555; font-size:14px; line-height:1.6;">
            Period: <strong>{{period}}</strong>
        </p>
        <table role="presentation" width="100%" cellpadding="8" cellspacing="0" style="border-collapse:collapse; margin:20px 0;">
            <tr style="background-color:#E3F2FD;">
                <td style="border:1px solid #BBDEFB; font-weight:bold; color:#1565C0;">Total Spend</td>
                <td style="border:1px solid #BBDEFB;">{{spend}}</td>
            </tr>
            <tr>
                <td style="border:1px solid #E0E0E0; font-weight:bold; color:#1565C0;">Conversions</td>
                <td style="border:1px solid #E0E0E0;">{{conversions}}</td>
            </tr>
            <tr style="background-color:#E3F2FD;">
                <td style="border:1px solid #BBDEFB; font-weight:bold; color:#1565C0;">ROAS</td>
                <td style="border:1px solid #BBDEFB;">{{roas}}</td>
            </tr>
        </table>
        <div style="text-align:center; margin:30px 0;">
            <a href="{{portalLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
                View Full Report
            </a>
        </div>
        """;

    // ── Alert Template ───────────────────────────────────────────

    private static final String ALERT_TEMPLATE = """
        <h2 style="color:#D32F2F; margin-top:0;">⚠️ {{alertTitle}}</h2>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            {{alertMessage}}
        </p>
        <p style="color:#555555; font-size:14px; line-height:1.4;">
            <strong>Client:</strong> {{clientName}}<br>
            <strong>Severity:</strong> <span style="color:{{severityColor}}; font-weight:bold;">{{severity}}</span>
        </p>
        <div style="text-align:center; margin:30px 0;">
            <a href="{{dashboardLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
                View Details
            </a>
        </div>
        <p style="color:#999999; font-size:12px;">
            You are receiving this alert because you are assigned to this client.
        </p>
        """;

    // ── Campaign Published Template ──────────────────────────────

    private static final String CAMPAIGN_PUBLISHED_TEMPLATE = """
        <h2 style="color:#333333; margin-top:0;">🚀 Campaign Published</h2>
        <p style="color:#555555; font-size:16px; line-height:1.6;">
            Campaign <strong>{{campaignName}}</strong> has been successfully published to Meta Ads for <strong>{{clientName}}</strong>.
        </p>
        <p style="color:#555555; font-size:14px; line-height:1.4;">
            <strong>Status:</strong> <span style="background-color:#C8E6C9; padding:2px 8px; border-radius:4px; color:#2E7D32; font-weight:bold;">PUBLISHED</span>
        </p>
        <div style="text-align:center; margin:30px 0;">
            <a href="{{dashboardLink}}" style="background-color:#1565C0; color:#ffffff; text-decoration:none; padding:14px 32px; border-radius:6px; font-size:16px; font-weight:bold; display:inline-block;">
                View Campaign
            </a>
        </div>
        """;
}
