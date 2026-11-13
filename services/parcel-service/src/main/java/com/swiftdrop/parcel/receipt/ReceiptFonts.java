package com.swiftdrop.parcel.receipt;

import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.FontSelector;
import org.openpdf.text.pdf.LayoutProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Fonts for receipt text in Sinhala and Tamil. The standard PDF fonts (Helvetica) only cover
 * Latin text, so names and addresses in Sinhala or Tamil would print as blank space.
 *
 * Noto Sans Sinhala and Noto Sans Tamil (SIL Open Font License, see fonts/OFL.txt) are bundled
 * with the receipt package and embedded as subsets, so the receipt only carries the glyphs it
 * uses. Both scripts need glyph shaping (vowel signs are reordered and combined), which
 * OpenPDF's LayoutProcessor does with the JDK's text layout. It also writes each run's original
 * text as ActualText, so copy and paste and search in PDF viewers return the real characters.
 *
 * LayoutProcessor is configured once per JVM; in parcel-service OpenPDF is only used for receipts.
 */
final class ReceiptFonts {

    private static final String DIRECTORY = "fonts/";
    private static final List<String> REGULAR = List.of("NotoSansSinhala-Regular.ttf", "NotoSansTamil-Regular.ttf");
    private static final List<String> BOLD = List.of("NotoSansSinhala-Bold.ttf", "NotoSansTamil-Bold.ttf");

    private static volatile ReceiptFonts instance;

    private final List<BaseFont> regular;
    private final List<BaseFont> bold;

    private ReceiptFonts() {
        LayoutProcessor.enable();
        LayoutProcessor.setWriteActualText();
        this.regular = REGULAR.stream().map(ReceiptFonts::load).toList();
        this.bold = BOLD.stream().map(ReceiptFonts::load).toList();
    }

    static ReceiptFonts get() {
        ReceiptFonts fonts = instance;
        if (fonts == null) {
            synchronized (ReceiptFonts.class) {
                fonts = instance;
                if (fonts == null) {
                    fonts = new ReceiptFonts();
                    instance = fonts;
                }
            }
        }
        return fonts;
    }

    /**
     * Text in the given Latin font, with each Sinhala or Tamil run switched to the matching Noto
     * font of the same size, colour and weight.
     */
    Phrase phrase(String text, Font latin, boolean bold) {
        FontSelector selector = new FontSelector();
        selector.addFont(latin);
        for (BaseFont fallback : bold ? this.bold : this.regular) {
            selector.addFont(new Font(fallback, latin.getSize(), Font.NORMAL, latin.getColor()));
        }
        return selector.process(text);
    }

    /**
     * LayoutProcessor reads a font from a file, so the bundled font is copied to a temporary file
     * once (deleted when the JVM exits).
     */
    private static BaseFont load(String name) {
        try (InputStream in = ReceiptFonts.class.getResourceAsStream(DIRECTORY + name)) {
            if (in == null) {
                throw new IllegalStateException("Receipt font missing from the classpath: " + name);
            }
            Path file = Files.createTempFile("swiftdrop-receipt-", "-" + name);
            file.toFile().deleteOnExit();
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            String path = file.toAbsolutePath().toString();
            BaseFont font = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            LayoutProcessor.loadFont(font, path);
            return font;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load receipt font " + name, e);
        }
    }
}
