package com.swiftdrop.parcel.receipt;

import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.parcel.ParcelSize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PRTokeniser;
import org.openpdf.text.pdf.PdfArray;
import org.openpdf.text.pdf.PdfContentParser;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfLiteral;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfObject;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfString;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Receipts with Sinhala and Tamil names and addresses. With the standard PDF font these printed
 * as blank space. The text shown is checked through the ActualText of each shaped run (what PDF
 * viewers use for copy, paste and search), the embedded fonts, and the glyph order that proves
 * the scripts were shaped.
 */
class ReceiptMultilingualTest {

    private static final String TAMIL_NAME = "சுனில் குமார்";      // சுனில் குமார்
    private static final String TAMIL_CITY = "கொழும்பு";                                // கொழும்பு
    private static final String SINHALA_NAME = "සුනිල් පෙරේරා";     // සුනිල් පෙරේරා
    private static final String SINHALA_CITY = "මහනුවර";                                          // මහනුවර

    private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer();

    private static ReceiptData receipt(String recipient, AddressDto pickup, AddressDto dropoff) {
        return new ReceiptData("SD-20261113-LANG01", "4f9a6c1e-2b3d-4e5f-8a9b-0c1d2e3f4a5b",
                Instant.parse("2026-11-13T08:00:00Z"), Instant.parse("2026-11-13T10:15:00Z"),
                pickup, dropoff, recipient, ParcelSize.MEDIUM, new BigDecimal("2.50"), new BigDecimal("700.00"),
                Instant.parse("2026-11-13T11:00:00Z"));
    }

    private static AddressDto latinAddress() {
        return new AddressDto("45 Galle Road", null, "Colombo 03", "Colombo", "00300");
    }

    /** One shaped run on the page: the original text and the glyph ids drawn for it. */
    record Run(String actualText, List<Integer> glyphs) {
    }

    /** Reads every ActualText span of page 1 and the glyph ids shown inside it. */
    static List<Run> shapedRuns(byte[] pdf) throws IOException {
        List<Run> runs = new ArrayList<>();
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfContentParser parser = new PdfContentParser(new PRTokeniser(reader.getPageContent(1)));
            List<PdfObject> operands = new ArrayList<>();
            String current = null;
            List<Integer> glyphs = new ArrayList<>();
            while (!parser.parse(operands).isEmpty()) {
                String operator = operands.get(operands.size() - 1).toString();
                if ("BDC".equals(operator) && operands.size() >= 3 && operands.get(1) instanceof PdfDictionary dict
                        && dict.get(PdfName.ACTUALTEXT) instanceof PdfString text) {
                    current = text.toUnicodeString();
                    glyphs = new ArrayList<>();
                } else if (current != null && ("Tj".equals(operator) || "TJ".equals(operator))) {
                    collectGlyphs(operands.get(0), glyphs);
                } else if ("EMC".equals(operator) && current != null) {
                    runs.add(new Run(current, glyphs));
                    current = null;
                }
            }
        }
        return runs;
    }

    /** Identity-H text: every glyph is a 2-byte glyph id. */
    private static void collectGlyphs(PdfObject operand, List<Integer> glyphs) {
        if (operand instanceof PdfString string) {
            byte[] bytes = string.getOriginalBytes();
            for (int i = 0; i + 1 < bytes.length; i += 2) {
                glyphs.add(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff));
            }
        } else if (operand instanceof PdfArray array) {
            for (PdfObject item : array.getElements()) {
                if (!(item instanceof PdfLiteral)) {
                    collectGlyphs(item, glyphs);
                }
            }
        }
    }

    static List<String> embeddedFonts(byte[] pdf) throws IOException {
        List<String> names = new ArrayList<>();
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfDictionary fonts = reader.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.FONT);
            for (PdfName key : fonts.getKeys()) {
                PdfDictionary font = (PdfDictionary) PdfReader.getPdfObject(fonts.get(key));
                names.add(PdfName.decodeName(font.get(PdfName.BASEFONT).toString()));
            }
        }
        return names;
    }

    private static int glyphId(String fontFile, String text) throws Exception {
        try (InputStream in = ReceiptFonts.class.getResourceAsStream("fonts/" + fontFile)) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(10f);
            return font.createGlyphVector(new FontRenderContext(null, true, true), text).getGlyphCode(0);
        }
    }

    private static String joined(List<Run> runs) {
        return String.join("", runs.stream().map(Run::actualText).toList());
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("A Tamil recipient and a Sinhala address are on the receipt, in the matching embedded Noto fonts")
    void sinhalaAndTamilAreRendered() throws Exception {
        byte[] pdf = renderer.render(receipt(TAMIL_NAME, latinAddress(),
                new AddressDto("12 පන්සල පාර", null, SINHALA_CITY, "Kandy", "20000")));

        String shown = joined(shapedRuns(pdf));
        assertThat(shown).contains(
                "சுனில்", "குமார்",
                "පන්සල", "පාර", SINHALA_CITY);
        assertThat(embeddedFonts(pdf))
                .anyMatch(name -> name.endsWith("+NotoSansTamil-Regular"))
                .anyMatch(name -> name.endsWith("+NotoSansSinhala-Regular"))
                .contains("Helvetica");
        // The Latin parts stay in Helvetica and are still read back as before.
        assertThat(ReceiptPdfRendererTest.extractText(pdf)).contains("SD-20261113-LANG01", "Kandy, 20000", "LKR 700.00");
    }

    @Test
    @DisplayName("Sinhala recipient and Tamil address work the other way round too")
    void sinhalaRecipientTamilAddress() throws Exception {
        byte[] pdf = renderer.render(receipt(SINHALA_NAME, new AddressDto("7 Main Street", null, TAMIL_CITY, "Colombo", "00100"),
                latinAddress()));

        String shown = joined(shapedRuns(pdf));
        assertThat(shown).contains("සුනිල්", "පෙරේරා", TAMIL_CITY);
    }

    @Test
    @DisplayName("Tamil is shaped: in கொ (ka + o) the e-sign is drawn before ka, as the script requires")
    void tamilVowelSignsAreReordered() throws Exception {
        byte[] pdf = renderer.render(receipt("கொ", latinAddress(), latinAddress()));

        Run run = shapedRuns(pdf).stream().filter(r -> r.actualText().equals("கொ")).findFirst().orElseThrow();
        int eSign = glyphId("NotoSansTamil-Regular.ttf", "ெ");
        int ka = glyphId("NotoSansTamil-Regular.ttf", "க");
        int aa = glyphId("NotoSansTamil-Regular.ttf", "ா");
        assertThat(run.glyphs()).as("visual order e-sign, ka, aa-sign").containsExactly(eSign, ka, aa);
    }

    @Test
    @DisplayName("Sinhala is shaped: in කො (ka + o) the kombuva is drawn before ka")
    void sinhalaVowelSignsAreReordered() throws Exception {
        byte[] pdf = renderer.render(receipt("කො", latinAddress(), latinAddress()));

        Run run = shapedRuns(pdf).stream().filter(r -> r.actualText().equals("කො")).findFirst().orElseThrow();
        int kombuva = glyphId("NotoSansSinhala-Regular.ttf", "ෙ");
        int ka = glyphId("NotoSansSinhala-Regular.ttf", "ක");
        assertThat(run.glyphs()).as("kombuva before ka").startsWith(kombuva, ka);
    }

    @Test
    @DisplayName("A long Sinhala address wraps inside the page and every word is kept")
    void longSinhalaAddressWraps() throws Exception {
        String longLine = (SINHALA_CITY + " ").repeat(25).trim();
        byte[] pdf = renderer.render(receipt(SINHALA_NAME, latinAddress(), new AddressDto(longLine, null, SINHALA_CITY, "Kandy", "20000")));

        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
        }
        String shown = joined(shapedRuns(pdf));
        assertThat(shown.split(SINHALA_CITY, -1)).as("25 in the line + 1 city").hasSize(27);
    }

    @Test
    @DisplayName("A Latin-only receipt embeds no extra fonts, so it stays small")
    void latinOnlyEmbedsNoExtraFonts() throws Exception {
        byte[] pdf = renderer.render(receipt("Sunil Silva", latinAddress(), latinAddress()));

        assertThat(embeddedFonts(pdf)).noneMatch(name -> name.contains("Noto"));
        assertThat(shapedRuns(pdf)).isEmpty();
        assertThat(pdf.length).isLessThan(5_000);
    }

    @Test
    @DisplayName("Bold values in Sinhala use the bold Noto font")
    void boldValuesUseBoldFont() throws Exception {
        // The fee row is bold; a bold run in Sinhala would only appear if a bold value held Sinhala.
        ReceiptFonts fonts = ReceiptFonts.get();
        var phrase = fonts.phrase(SINHALA_CITY, org.openpdf.text.FontFactory.getFont(org.openpdf.text.FontFactory.HELVETICA_BOLD, 11), true);
        assertThat(phrase.getChunks()).singleElement()
                .isInstanceOfSatisfying(org.openpdf.text.Chunk.class,
                        chunk -> assertThat(chunk.getFont().getBaseFont().getPostscriptFontName()).isEqualTo("NotoSansSinhala-Bold"));
    }
}
