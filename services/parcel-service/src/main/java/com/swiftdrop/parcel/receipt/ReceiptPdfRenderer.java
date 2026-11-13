package com.swiftdrop.parcel.receipt;

import com.swiftdrop.parcel.address.AddressDto;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Builds the one-page receipt PDF with OpenPDF (docs/tracking-design.md section 8).
 * Table cells wrap long text, and missing optional values are shown as a dash.
 * Values in Sinhala or Tamil use the bundled Noto fonts (ReceiptFonts); Latin text stays Helvetica.
 */
@Component
class ReceiptPdfRenderer {

    static final String MISSING = "-";
    /** SwiftDrop's business time zone (same as operations-service). */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH).withZone(BUSINESS_ZONE);

    private static final Color BRAND = new Color(0x1D, 0x4E, 0xD8);
    private static final Color MUTED = new Color(0x55, 0x5F, 0x6D);
    private static final Color RULE = new Color(0xD5, 0xDA, 0xE1);

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND);
    private final Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 11, MUTED);
    private final Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    private final Font label = FontFactory.getFont(FontFactory.HELVETICA, 10, MUTED);
    private final Font value = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private final Font strong = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
    private final Font footer = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

    byte[] render(ReceiptData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            ReceiptFonts fonts = ReceiptFonts.get();
            PdfWriter.getInstance(document, out);
            document.addTitle("SwiftDrop receipt " + data.trackingNumber());
            document.addCreator("SwiftDrop parcel-service");
            document.open();

            document.add(new Paragraph("SwiftDrop", title));
            document.add(new Paragraph("Delivery receipt", subtitle));
            document.add(spacer(12));

            PdfPTable parcel = table();
            row(fonts, parcel, "Tracking number", text(data.trackingNumber()), strong);
            row(fonts, parcel, "Parcel ID", text(data.parcelId()), value);
            row(fonts, parcel, "Status", "Delivered", value);
            row(fonts, parcel, "Created", date(data.createdAt()), value);
            row(fonts, parcel, "Delivered", date(data.deliveredAt()), value);
            addSection(document, "Parcel", parcel);

            PdfPTable route = table();
            row(fonts, route, "Pickup address", address(data.pickupAddress()), value);
            row(fonts, route, "Drop-off address", address(data.dropoffAddress()), value);
            row(fonts, route, "Recipient", text(data.recipientName()), value);
            addSection(document, "Delivery", route);

            PdfPTable details = table();
            row(fonts, details, "Size", data.size() != null ? capitalize(data.size().name()) : MISSING, value);
            row(fonts, details, "Weight", weight(data.weightKg()), value);
            row(fonts, details, "Delivery fee", fee(data.fee()), strong);
            addSection(document, "Charges", details);

            Paragraph generated = new Paragraph(
                    "Generated " + date(data.generatedAt()) + " (Sri Lanka time). "
                            + "This receipt confirms the delivery of the parcel above.", footer);
            generated.setSpacingBefore(18);
            document.add(generated);

            document.close();
        } catch (RuntimeException e) {
            // Includes OpenPDF's DocumentException, which is unchecked in OpenPDF 3.
            throw new ReceiptGenerationException(e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ formatting

    static String date(Instant instant) {
        return instant == null ? MISSING : DATE_TIME.format(instant);
    }

    static String fee(BigDecimal fee) {
        return fee == null ? MISSING : "LKR " + String.format(Locale.ENGLISH, "%,.2f", fee.setScale(2, RoundingMode.HALF_UP));
    }

    static String weight(BigDecimal weightKg) {
        return weightKg == null ? MISSING : weightKg.stripTrailingZeros().toPlainString() + " kg";
    }

    static String address(AddressDto address) {
        if (address == null) {
            return MISSING;
        }
        StringJoiner joined = new StringJoiner(", ");
        for (String part : new String[] {address.line1(), address.line2(), address.city(), address.district(), address.postalCode()}) {
            if (part != null && !part.isBlank()) {
                joined.add(part.trim());
            }
        }
        return joined.length() == 0 ? MISSING : joined.toString();
    }

    static String text(String value) {
        return value == null || value.isBlank() ? MISSING : value.trim();
    }

    private static String capitalize(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ENGLISH);
    }

    // ------------------------------------------------------------------ layout

    private void addSection(Document document, String heading, PdfPTable table) throws DocumentException {
        Paragraph title = new Paragraph(heading, section);
        title.setSpacingBefore(10);
        title.setSpacingAfter(4);
        document.add(title);
        document.add(table);
    }

    private static PdfPTable table() throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {1.2f, 3f});
        return table;
    }

    /** Labels are fixed English text; values may contain Sinhala or Tamil (names, addresses). */
    private void row(ReceiptFonts fonts, PdfPTable table, String name, String content, Font font) {
        table.addCell(cell(new Phrase(name, label)));
        table.addCell(cell(fonts.phrase(content, font, font == strong)));
    }

    private static PdfPCell cell(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(PdfPCell.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setPaddingTop(5);
        cell.setPaddingBottom(6);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setNoWrap(false);
        return cell;
    }

    private static Paragraph spacer(float height) {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(height);
        return spacer;
    }
}
