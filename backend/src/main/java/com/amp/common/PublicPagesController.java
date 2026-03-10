package com.amp.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public legal pages required for compliance and platform review.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicPagesController {

    @GetMapping("/privacy-policy")
    public ResponseEntity<String> privacyPolicy() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
                .body(PRIVACY_HTML);
    }

    @GetMapping("/terms-of-service")
    public ResponseEntity<String> termsOfService() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
                .body(TERMS_HTML);
    }

    private static final String PAGE_STYLE = """
            <style>
              body { font-family: Inter, Arial, sans-serif; color: #263238; margin: 0; background: #f7f9fc; }
              .wrap { max-width: 980px; margin: 32px auto; background: #fff; border-radius: 12px; padding: 28px; box-shadow: 0 8px 24px rgba(0,0,0,.08); }
              h1 { color: #0d47a1; margin-top: 0; }
              h2 { color: #1565c0; margin-top: 28px; }
              p, li { line-height: 1.7; }
              .muted { color: #546e7a; font-size: 14px; }
              .note { background: #e3f2fd; padding: 12px; border-radius: 8px; }
              a { color: #1565c0; }
            </style>
            """;

    private static final String PRIVACY_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Privacy Policy — AI Marketing Platform</title>
              %s
            </head>
            <body>
              <div class="wrap">
                <h1>Privacy Policy</h1>
                <p class="muted">Platform: AI Marketing Platform (adverion.xyz) by Adverion<br/>Last updated: March 2026</p>
                <p class="note">Primary language: English. A Bulgarian version is available on request at <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.</p>

                <h2>1. What data we collect</h2>
                <ul>
                  <li>Account information: name, email, role, agency/client associations, login metadata.</li>
                  <li>Marketing platform data from Meta/Facebook API: campaigns, ad sets, ads, and performance insights.</li>
                  <li>Creative assets and campaign-related content uploaded by your organization.</li>
                  <li>Usage analytics and operational logs for security, troubleshooting, and product improvement.</li>
                </ul>

                <h2>2. How we use the data</h2>
                <ul>
                  <li>To provide campaign management, reporting, and collaboration features.</li>
                  <li>To generate AI-powered analyses, recommendations, and summaries.</li>
                  <li>To monitor performance, maintain service security, and support customers.</li>
                  <li>To comply with legal obligations and enforce platform terms.</li>
                </ul>

                <h2>3. Third-party services</h2>
                <p>We use trusted providers, including:</p>
                <ul>
                  <li>Meta/Facebook API for advertising data and account connectivity.</li>
                  <li>Amazon Web Services (AWS) for infrastructure, storage, and operational services.</li>
                  <li>Anthropic Claude AI for text analysis and AI-assisted outputs.</li>
                </ul>
                <p>These providers process data under their own legal terms and applicable data protection commitments.</p>

                <h2>4. Data storage and security</h2>
                <ul>
                  <li>Data is hosted in secure AWS infrastructure with access controls and monitoring.</li>
                  <li>Encryption is used in transit and, where applicable, at rest.</li>
                  <li>Tenant isolation controls are implemented to separate agency/client data access.</li>
                  <li>Access is role-based and audited for sensitive actions.</li>
                </ul>

                <h2>5. Data retention and deletion</h2>
                <p>We retain data as long as needed to provide the service, meet contractual obligations, and satisfy legal requirements. Upon valid request or account termination, data is deleted or anonymized within reasonable operational timelines, unless retention is legally required.</p>

                <h2>6. Cookies and session data</h2>
                <p>We use minimal client-side storage required for authentication/session handling (for example JWT token storage) and security-related session behavior. We do not use unnecessary advertising cookies on the core platform experience.</p>

                <h2>7. User rights (GDPR and similar laws)</h2>
                <p>Depending on your jurisdiction, you may have rights to access, correct, delete, restrict processing, object, and request export/portability of your personal data. To exercise rights, contact <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.</p>

                <h2>8. Children’s privacy</h2>
                <p>The service is not intended for individuals under 18. We do not knowingly collect personal data from children.</p>

                <h2>9. Changes to this policy</h2>
                <p>We may update this Privacy Policy from time to time. Material updates will be reflected by a new “Last updated” date and, where required, additional notice.</p>

                <h2>10. Contact</h2>
                <p>For privacy questions, requests, or concerns: <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.</p>
              </div>
            </body>
            </html>
            """.formatted(PAGE_STYLE);

    private static final String TERMS_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Terms of Service — AI Marketing Platform</title>
              %s
            </head>
            <body>
              <div class="wrap">
                <h1>Terms of Service</h1>
                <p class="muted">Platform: AI Marketing Platform (adverion.xyz) by Adverion<br/>Last updated: March 2026</p>
                <p class="note">Primary language: English. A Bulgarian version is available on request at <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.</p>

                <h2>1. Acceptance of terms</h2>
                <p>By accessing or using AI Marketing Platform, you agree to these Terms of Service. If you do not agree, do not use the service.</p>

                <h2>2. Description of service</h2>
                <p>AI Marketing Platform is a SaaS platform for marketing agencies to manage Meta advertising operations, monitor performance, generate reports, and receive AI-assisted recommendations.</p>

                <h2>3. User accounts and responsibilities</h2>
                <ul>
                  <li>You are responsible for maintaining the confidentiality of account credentials.</li>
                  <li>You must provide accurate information and use the service lawfully.</li>
                  <li>You are responsible for actions performed under your account.</li>
                </ul>

                <h2>4. Agency responsibilities</h2>
                <ul>
                  <li>Agencies are responsible for the accuracy and legality of client data they upload.</li>
                  <li>Agencies must comply with Meta/Facebook platform policies and ad requirements.</li>
                  <li>Agencies must have the legal right to process client campaign data in the platform.</li>
                </ul>

                <h2>5. AI features disclaimer</h2>
                <p>AI-generated outputs (including recommendations, summaries, and alerts) are advisory only. They are not guarantees of performance or legal/compliance outcomes and should be reviewed by qualified users before action.</p>

                <h2>6. Intellectual property</h2>
                <ul>
                  <li>You retain ownership of your content, campaign data, and creative assets.</li>
                  <li>Adverion retains ownership of the platform, software, and related intellectual property.</li>
                  <li>You grant Adverion a limited license to process your data solely to provide and improve the service.</li>
                </ul>

                <h2>7. Payment terms</h2>
                <p>Commercial terms, fees, and billing cycles are governed by your subscription agreement and/or order form.</p>

                <h2>8. Data and privacy</h2>
                <p>Use of personal data is governed by our Privacy Policy: <a href="/privacy">/privacy</a>.</p>

                <h2>9. Limitation of liability</h2>
                <p>To the maximum extent permitted by law, Adverion is not liable for indirect, incidental, special, consequential, or punitive damages, including lost profits or business interruption. Service is provided on an “as is” and “as available” basis.</p>

                <h2>10. Termination</h2>
                <p>We may suspend or terminate access for material breaches, unlawful use, or security risks. You may stop using the service at any time subject to contractual commitments.</p>

                <h2>11. Governing law</h2>
                <p>These Terms are governed by the laws of Bulgaria, unless mandatory law requires otherwise.</p>

                <h2>12. Changes to terms</h2>
                <p>We may update these Terms periodically. Continued use after updates constitutes acceptance of the revised Terms.</p>

                <h2>13. Contact</h2>
                <p>For legal or support inquiries: <a href="mailto:support@adverion.xyz">support@adverion.xyz</a>.</p>
              </div>
            </body>
            </html>
            """.formatted(PAGE_STYLE);
}
