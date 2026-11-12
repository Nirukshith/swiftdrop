package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MerchantCsv {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final String HEADER = "externalReference,recipientName,recipientPhone,pickupLine1,pickupLine2,pickupCity,pickupDistrict,pickupPostalCode,dropoffLine1,dropoffLine2,dropoffCity,dropoffDistrict,dropoffPostalCode,size,weightKg";
    private static final Set<String> DISTRICTS = Set.of("Ampara", "Anuradhapura", "Badulla", "Batticaloa",
            "Colombo", "Galle", "Gampaha", "Hambantota", "Jaffna", "Kalutara", "Kandy", "Kegalle",
            "Kilinochchi", "Kurunegala", "Mannar", "Matale", "Matara", "Monaragala", "Mullaitivu",
            "Nuwara Eliya", "Polonnaruwa", "Puttalam", "Ratnapura", "Trincomalee", "Vavuniya");
    private final Validator validator;

    public MerchantCsv(Validator validator) { this.validator = validator; }

    public List<Row> parse(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV exceeds 5 MiB");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) { throw malformed("CSV must be UTF-8"); }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<Cells> records = records(text);
        if (records.isEmpty() || !records.get(0).values().equals(Arrays.asList(HEADER.split(",")))) {
            throw malformed("CSV header must exactly match the documented template");
        }
        List<Row> rows = new ArrayList<>();
        Map<String, Integer> references = new HashMap<>();
        for (Cells record : records.subList(1, records.size())) {
            if (rows.size() == 100) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV exceeds 100 data rows");
            List<String> errors = new ArrayList<>();
            List<String> c = record.values().stream().map(String::strip).toList();
            CreateMerchantOrderRequest request = null;
            if (c.size() != 15) {
                errors.add("Expected 15 columns");
            } else {
                BigDecimal weight = null;
                MerchantParcelSize size = null;
                try { weight = new BigDecimal(c.get(14)); }
                catch (NumberFormatException exception) { errors.add("weightKg must be a decimal number"); }
                try { size = MerchantParcelSize.valueOf(c.get(13)); }
                catch (IllegalArgumentException exception) { errors.add("size must be SMALL, MEDIUM or LARGE"); }
                var pickup = new StructuredAddress(c.get(3), c.get(4), c.get(5), district(c.get(6)), c.get(7));
                var dropoff = new StructuredAddress(c.get(8), c.get(9), c.get(10), district(c.get(11)), c.get(12));
                request = new CreateMerchantOrderRequest(c.get(0), c.get(1), c.get(2), pickup, dropoff, size, weight);
                validator.validate(request).stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .sorted().forEach(errors::add);
                if (weight != null && (weight.compareTo(new BigDecimal("0.10")) < 0 || weight.precision() > 12
                        || Math.abs((long) weight.scale()) > 6)) errors.add("weightKg must be at least 0.10 with at most 12 digits and 6 decimal places");
                if (!DISTRICTS.contains(pickup.district())) errors.add("pickupDistrict must be a supported Sri Lankan district");
                if (!DISTRICTS.contains(dropoff.district())) errors.add("dropoffDistrict must be a supported Sri Lankan district");
                if (pickup.postalCode().isBlank()) errors.add("pickupPostalCode is required");
                if (dropoff.postalCode().isBlank()) errors.add("dropoffPostalCode is required");
                for (int i = 1; i < 13; i++) if (c.get(i).length() > 255) errors.add("Column " + (i + 1) + " exceeds 255 characters");
                references.merge(c.get(0), 1, Integer::sum);
            }
            rows.add(new Row(record.line(), request, errors));
        }
        if (rows.isEmpty()) throw malformed("CSV must contain at least one data row");
        for (Row row : rows) if (row.request() != null && references.get(row.request().externalReference()) > 1) {
            row.errors().add("Duplicate externalReference in this file");
        }
        return rows;
    }

    private String district(String input) {
        return DISTRICTS.stream().filter(d -> d.equalsIgnoreCase(input.replace('_', ' '))).findFirst().orElse(input);
    }

    /** Strict quoted CSV, including escaped quotes, CRLF, and embedded line breaks. */
    private List<Cells> records(String text) {
        List<Cells> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false;
        int line = 1, start = 1;
        for (int i = 0; i <= text.length(); i++) {
            boolean end = i == text.length();
            char ch = end ? '\n' : text.charAt(i);
            if (ch == '\0') throw malformed("CSV contains a NUL character");
            if (quoted) {
                if (end) throw malformed("CSV contains an unclosed quoted field");
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else { field.append(ch); if (ch == '\n' || (ch == '\r' && (i + 1 == text.length() || text.charAt(i + 1) != '\n'))) line++; }
            } else if (ch == '"') {
                if (!field.isEmpty() || closed) throw malformed("Unexpected quote in CSV field");
                quoted = true;
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                cells.add(field.toString()); field.setLength(0); closed = false;
                if (cells.size() > 15) throw malformed("CSV contains too many columns");
                if (ch != ',') {
                    if (cells.size() != 1 || !cells.get(0).isBlank()) records.add(new Cells(start, List.copyOf(cells)));
                    cells.clear();
                    if (records.size() > 101) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV exceeds 100 data rows");
                    if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    line++; start = line;
                }
            } else {
                if (closed) throw malformed("Unexpected character after CSV closing quote");
                field.append(ch);
            }
        }
        return records;
    }

    private ResponseStatusException malformed(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private record Cells(int line, List<String> values) {}
    public record Row(int rowNumber, CreateMerchantOrderRequest request, List<String> errors) {}
}
