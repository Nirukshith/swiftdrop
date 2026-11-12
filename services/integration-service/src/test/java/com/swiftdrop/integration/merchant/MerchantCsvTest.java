package com.swiftdrop.integration.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class MerchantCsvTest {
    private final MerchantCsv parser = new MerchantCsv(Validation.buildDefaultValidatorFactory().getValidator());
    static String row(String reference) {
        return reference + ",Asha,+94771234567,10 Main Road,,Colombo,Colombo,00100,20 Lake Road,,Gampaha,Gampaha,11000,SMALL,1.5";
    }
    static byte[] file(String rows) { return (MerchantCsv.HEADER + "\n" + rows + "\n").getBytes(StandardCharsets.UTF_8); }

    @Test void parsesBomCrlfQuotedCommasEscapedQuotesAndEmbeddedLines() {
        String record = row("quoted").replace("Asha", "\"Asha, \"\"Shop\"\"\nColombo\"");
        var rows = parser.parse(("\uFEFF" + MerchantCsv.HEADER + "\r\n" + record + "\r\n" + row("next")).getBytes(StandardCharsets.UTF_8));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).request().recipientName()).isEqualTo("Asha, \"Shop\"\nColombo");
        assertThat(rows.get(0).errors()).isEmpty();
        assertThat(rows.get(1).rowNumber()).isEqualTo(4);
    }
    @ParameterizedTest @ValueSource(strings = {"", "wrong,header\nabc", "HEADER", "HEADER\n\"unclosed", "HEADER\nabc\"quote", "HEADER\n\"abc\"bad"})
    void rejectsMalformedFiles(String value) {
        assertThatThrownBy(() -> parser.parse(value.replace("HEADER", MerchantCsv.HEADER).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResponseStatusException.class);
    }
    @Test void rejectsMalformedUtf8AndNul() {
        assertThatThrownBy(() -> parser.parse(new byte[] {(byte) 0xc3, (byte) 0x28})).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> parser.parse(file(row("bad\0")))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void reportsEveryDuplicateAndRequiredFieldWithoutInvalidatingOtherRows() {
        var rows = parser.parse(file(row("same") + "\n" + row("same") + "\n" + row("good") + "\nshort,row"));
        assertThat(rows.get(0).errors()).contains("Duplicate externalReference in this file");
        assertThat(rows.get(1).errors()).contains("Duplicate externalReference in this file");
        assertThat(rows.get(2).errors()).isEmpty();
        assertThat(rows.get(3).errors()).contains("Expected 15 columns");
        assertThat(parser.parse(file(row("bad").replace("Colombo,00100", "Unknown,").replace("SMALL,1.5", "HUGE,0.01"))).getFirst().errors())
                .anyMatch(e -> e.contains("pickupDistrict")).anyMatch(e -> e.contains("pickupPostalCode"))
                .anyMatch(e -> e.contains("size")).anyMatch(e -> e.contains("weightKg"));
    }
    @Test void enforcesByteAndRowLimits() {
        assertThatThrownBy(() -> parser.parse(new byte[MerchantCsv.MAX_BYTES + 1])).isInstanceOf(ResponseStatusException.class);
        assertThat(parser.parse(file((row("item") + "\n").repeat(100)))).hasSize(100);
        assertThatThrownBy(() -> parser.parse(file((row("item") + "\n").repeat(101)))).isInstanceOf(ResponseStatusException.class);
    }
}
