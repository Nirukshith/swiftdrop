package com.swiftdrop.integration.assistant.service;

import com.swiftdrop.integration.assistant.client.LlmClient;
import com.swiftdrop.integration.assistant.tools.AssistantTools;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class AssistantService {

    private static final Pattern TRACKING = Pattern.compile("SD-\\d{8}-[A-Z0-9]{6}", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEIGHT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*kg", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISTRICTS = Pattern.compile(
            "\\bfrom\\s+([A-Za-z][A-Za-z -]{1,40}?)\\s+to\\s+([A-Za-z][A-Za-z -]{1,40}?)(?=\\s+(?:for|at|with)|\\s*$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZE = Pattern.compile("\\b(small|medium|large)\\b", Pattern.CASE_INSENSITIVE);
    private final LlmClient llmClient;
    private final AssistantTools tools;

    public AssistantService(LlmClient llmClient, AssistantTools tools) {
        this.llmClient = llmClient;
        this.tools = tools;
    }

    public String answer(String message, String conversationId, String callerToken) {
        String question = message.trim();
        String lower = question.toLowerCase(Locale.ROOT);
        String tracking = trackingNumber(question);
        String facts;

        if (isUnsupported(lower)) {
            return "I can help with SwiftDrop parcels, delivery status, tracking history, delivery information, recent parcels, and delivery fees.";
        }
        try {
            if (isFeeQuestion(lower)) {
                facts = feeFacts(question, callerToken);
            } else if (isSummaryQuestion(lower)) {
                facts = String.valueOf(tools.getParcelSummary(callerToken));
            } else if (isParcelQuestion(lower)) {
                if (tracking == null) {
                    return "Please provide the tracking number so I can check the correct parcel.";
                }
                if (lower.contains("history") || lower.contains("timeline") || lower.contains("updated")) {
                    facts = String.valueOf(tools.getTrackingHistory(tracking, callerToken));
                } else if (lower.contains("courier") || lower.contains("assigned") || lower.contains("delivery")) {
                    facts = String.valueOf(tools.getDeliveryInformation(tracking, callerToken));
                } else {
                    facts = String.valueOf(tools.getParcelByTrackingNumber(tracking, callerToken));
                }
            } else {
                facts = String.valueOf(tools.getCustomerParcels(null, 0, 20, callerToken));
            }
        } catch (com.swiftdrop.integration.assistant.tools.AssistantToolsImpl.ParcelNotFoundException ex) {
            return "I could not find that parcel among your SwiftDrop parcels.";
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        } catch (RestClientException | IllegalStateException ex) {
            return "SwiftDrop parcel information is temporarily unavailable. Please try again later.";
        }

        try {
            return llmClient.chat(
                    "Answer the SwiftDrop customer question using only these retrieved facts. "
                            + "If a fact is absent, say it is unavailable. Never invent statuses, people, dates, or prices.\n"
                            + "Question: " + question + "\nRetrieved facts: " + facts,
                    conversationId, callerToken);
        } catch (RestClientException | IllegalStateException ex) {
            return "The SwiftDrop assistant is temporarily unavailable. Please try again later.";
        }
    }

    private String feeFacts(String question, String token) {
        Matcher weight = WEIGHT.matcher(question);
        Matcher districts = DISTRICTS.matcher(question);
        Matcher size = SIZE.matcher(question);
        if (!weight.find() || !districts.find() || !size.find()) {
            return "A fee quote needs pickup district, destination district, weight in kg, and parcel size.";
        }
        return String.valueOf(tools.estimateFee(
                districts.group(1).trim(), districts.group(2).trim(),
                Double.parseDouble(weight.group(1)), size.group(1).toUpperCase(Locale.ROOT), token));
    }

    private static boolean isFeeQuestion(String lower) {
        return lower.contains("fee") || lower.contains("price") || lower.contains("cost") || lower.contains("quote");
    }

    private static boolean isSummaryQuestion(String lower) {
        return lower.contains("recent") || lower.contains("my parcels") || lower.contains("delivered")
                || lower.contains("delayed") || lower.contains("orders");
    }

    private static boolean isParcelQuestion(String lower) {
        return lower.contains("parcel") || lower.contains("track") || lower.contains("status")
                || lower.contains("where") || lower.contains("courier") || lower.contains("updated");
    }

    private static boolean isUnsupported(String lower) {
        return lower.contains("password") || lower.contains("refund") || lower.contains("weather")
                || lower.contains("politics") || lower.contains("shell") || lower.contains("sql");
    }

    private static String trackingNumber(String question) {
        Matcher matcher = TRACKING.matcher(question);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

}
