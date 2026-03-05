package com.amp.reports;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/**
 * Converts HTML string to PDF bytes using OpenHTMLtoPDF.
 */
@Component
public class PdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerator.class);

    /**
     * Render the supplied HTML to a PDF byte array.
     *
     * @param html well-formed XHTML/HTML content
     * @return PDF bytes ready for download
     * @throws RuntimeException if rendering fails
     */
    public byte[] generatePdf(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, "/");
            builder.toStream(os);
            builder.run();
            log.info("PDF generated: {} bytes", os.size());
            return os.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed", e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
}
