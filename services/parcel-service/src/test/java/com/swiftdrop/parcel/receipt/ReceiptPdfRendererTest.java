package com.swiftdrop.parcel.receipt;

import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.parcel.ParcelSize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPdfRendererTest {

    private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer();

    static ReceiptData sample() {
        return new ReceiptData(
                "SD-20261028-RCP001",
                "4f9a6c1e-2b3d-4e5f-8a9b-0c1d2e3f4a5b",
                Instant.parse("2026-10-28T08:00:00Z"),
                Instant.parse("2026-10-29T10:15:00Z"),
                new AddressDto("45 Galle Road", null, "Colombo 03", "Colombo", "00300"),
                new AddressDto("12 Temple Road", "Flat 2", "Kandy", "Kandy", "20000"),
                "Sunil Silva",
                ParcelSize.MEDIUM,
                new BigDecimal("2.50"),
                new BigDecimal("1750.00"),
                Instant.parse("2026-10-30T04:30:00Z"));
    }

    /** Text of every page, read back with OpenPDF's own parser. */
    static String extractText(byte[] pdf) throws IOException {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        }
    }

    @Test
    @DisplayName("Produces a valid one-page PDF that a PDF reader can open")
    void validPdf() throws IOException {
        byte[] pdf = renderer.render(sample());

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1).stripTrailing()).endsWith("%%EOF");
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(reader.getInfo().get("Title")).isEqualTo("SwiftDrop receipt SD-20261028-RCP001");
        }
    }

    @Test
    @DisplayName("Shows the parcel's own data, with dates in Sri Lanka time and the fee in LKR")
    void containsParcelData() throws IOException {
        String text = extractText(renderer.render(sample()));

        assertThat(text).contains(
                "SwiftDrop", "Delivery receipt",
                "SD-20261028-RCP001", "4f9a6c1e-2b3d-4e5f-8a9b-0c1d2e3f4a5b", "Delivered",
                "28 Oct 2026, 13:30", "29 Oct 2026, 15:45",
                "45 Galle Road, Colombo 03, Colombo, 00300",
                "12 Temple Road, Flat 2, Kandy, Kandy, 20000",
                "Sunil Silva", "Medium", "2.5 kg", "LKR 1,750.00",
                "Generated 30 Oct 2026, 10:00");
    }

    @Test
    @DisplayName("Missing optional values are shown as a dash instead of failing")
    void missingOptionalValues() throws IOException {
        ReceiptData data = new ReceiptData("SD-20261028-RCP002", "id-2", null, null, null,
                new AddressDto("1 Main Street", " ", "Galle", "Galle", null),
                null, null, null, null, Instant.parse("2026-10-30T04:30:00Z"));

        String text = extractText(renderer.render(data));

        assertThat(text).contains("SD-20261028-RCP002", "1 Main Street, Galle, Galle");
        assertThat(text).doesNotContain("null", "LKR");
        assertThat(ReceiptPdfRenderer.fee(null)).isEqualTo("-");
        assertThat(ReceiptPdfRenderer.date(null)).isEqualTo("-");
        assertThat(ReceiptPdfRenderer.address(null)).isEqualTo("-");
        assertThat(ReceiptPdfRenderer.text("  ")).isEqualTo("-");
    }

    @Test
    @DisplayName("Long text wraps inside the page instead of being cut off")
    void longTextWraps() throws IOException {
        String longLine = "Building " + "Very Long Street Name ".repeat(12) + "End";
        ReceiptData base = sample();
        ReceiptData data = new ReceiptData(base.trackingNumber(), base.parcelId(), base.createdAt(), base.deliveredAt(),
                base.pickupAddress(), new AddressDto(longLine, null, "Kandy", "Kandy", "20000"),
                "Recipient " + "Name ".repeat(30), base.size(), base.weightKg(), base.fee(), base.generatedAt());

        byte[] pdf = renderer.render(data);
        String text = extractText(pdf).replaceAll("\\s+", " ");

        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
        }
        // Every word is still there (wrapped over several lines, not clipped at the page edge).
        assertThat(text).contains("Building Very Long Street Name", "Street Name End, Kandy, Kandy, 20000");
        assertThat(text.split("Very Long Street Name", -1)).hasSize(13);
    }

    @Test
    @DisplayName("Formats amounts and weights like the frontend (2 decimals, thousands separator)")
    void formatting() {
        assertThat(ReceiptPdfRenderer.fee(new BigDecimal("750"))).isEqualTo("LKR 750.00");
        assertThat(ReceiptPdfRenderer.fee(new BigDecimal("12345.678"))).isEqualTo("LKR 12,345.68");
        assertThat(ReceiptPdfRenderer.weight(new BigDecimal("3.000"))).isEqualTo("3 kg");
    }
}
