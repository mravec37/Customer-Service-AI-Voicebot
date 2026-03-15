package CustomerServiceAI.telebit.service.ghl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;

@Service
public class VoiceRecordingRetriever {

    private final GhlClient ghlClient;
    private final Path recordingsDir;

    public VoiceRecordingRetriever(
            GhlClient ghlClient,
            @Value("${app.recordings-dir:recordings}") String recordingsDir
    ) {
        this.ghlClient = ghlClient;
        this.recordingsDir = Paths.get(recordingsDir).toAbsolutePath().normalize();
    }


    public Path retrieveRecording(String payload) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(payload);

            String contactId = root.path("contact_id").asText(null);

            String locationId = null;
            if (!root.path("location").path("id").isMissingNode() && !root.path("location").path("id").isNull()) {
                locationId = root.path("location").path("id").asText();
            } else if (!root.path("customData").path("location_id").isMissingNode()
                    && !root.path("customData").path("location_id").isNull()) {
                locationId = root.path("customData").path("location_id").asText();
            }

            if (contactId == null || contactId.isBlank()) {
                throw new IllegalArgumentException("Webhook does not contain contact_id");
            }

            if (locationId == null || locationId.isBlank()) {
                throw new IllegalArgumentException("Webhook does not contain location id");
            }

            return downloadLatestCallRecording(locationId, contactId);

        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve recording from webhook payload", e);
        }
    }

    public Path downloadLatestCallRecording(String locationId, String contactId) {
        String conversationId = findConversationId(locationId, contactId);
        String messageId = findLatestCompletedCallMessageId(conversationId);
        return downloadRecordingWithRetry(locationId, contactId, conversationId, messageId, 10, 7000);
    }

    private String findConversationId(String locationId, String contactId) {
        JsonNode response = ghlClient.searchConversations(locationId, contactId);
        JsonNode conversations = response.path("conversations");

        if (!conversations.isArray() || conversations.isEmpty()) {
            throw new IllegalStateException("No conversation found for contactId=" + contactId + ", locationId=" + locationId);
        }

        JsonNode firstConversation = conversations.get(0);
        String conversationId = text(firstConversation, "id");

        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalStateException("Conversation response did not contain a valid id");
        }

        return conversationId;
    }

    private String findLatestCompletedCallMessageId(String conversationId) {
        JsonNode response = ghlClient.getConversationMessages(conversationId, 100);
        JsonNode messages = response.path("messages").path("messages");

        if (!messages.isArray() || messages.isEmpty()) {
            throw new IllegalStateException("No messages found for conversationId=" + conversationId);
        }

        JsonNode selected = null;
        Instant selectedDate = null;

        for (JsonNode msg : messages) {

            String messageType = text(msg, "messageType");
            String direction = text(msg, "direction");
            String status = text(msg, "status");
            String dateAddedRaw = text(msg, "dateAdded");

            boolean matches =
                    "TYPE_CALL".equals(messageType) &&
                            //"inbound".equalsIgnoreCase(direction) &&
                            "completed".equalsIgnoreCase(status);

            if (!matches) {
                continue;
            }

            Instant dateAdded = parseInstant(dateAddedRaw);

            if (selected == null || compareInstants(dateAdded, selectedDate) > 0) {
                selected = msg;
                selectedDate = dateAdded;
            }
        }

        if (selected == null) {
            throw new IllegalStateException(
                    "No inbound completed TYPE_CALL message found for conversationId=" + conversationId
            );
        }

        String messageId = text(selected, "id");

        if (messageId == null || messageId.isBlank()) {
            throw new IllegalStateException("Latest call message does not contain an id");
        }
        System.out.println("MessageId is: " + messageId);
        return messageId;
    }

    private Path downloadRecordingWithRetry(
            String locationId,
            String contactId,
            String conversationId,
            String messageId,
            int maxAttempts,
            long delayMs
    ) {
        ensureDirectoryExists(recordingsDir);

        Path outputFile = recordingsDir.resolve(buildFileName(contactId, conversationId, messageId));

        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] audio = ghlClient.getRecording(locationId, messageId);

                if (audio == null || audio.length == 0) {
                    throw new IllegalStateException("Recording response was empty");
                }

                Files.write(outputFile, audio);
                return outputFile;

            } catch (RuntimeException | IOException ex) {
                lastFailure = new RuntimeException(
                        "Recording not available yet for messageId=" + messageId + " (attempt " + attempt + "/" + maxAttempts + ")",
                        ex
                );
                System.out.println(lastFailure.getCause());
                if (attempt < maxAttempts) {
                    sleep(delayMs);
                }
            }
        }

        throw new IllegalStateException("Recording could not be downloaded after " + maxAttempts + " attempts", lastFailure);
    }

    private void ensureDirectoryExists(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create recordings directory: " + dir, ex);
        }
    }

    private String buildFileName(String contactId, String conversationId, String messageId) {
        String safeContactId = sanitize(contactId);
        String safeConversationId = sanitize(conversationId);
        String safeMessageId = sanitize(messageId);

        return "call-" + safeContactId + "-" + safeConversationId + "-" + safeMessageId + ".wav";
    }

    private String sanitize(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private int compareInstants(Instant a, Instant b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return Comparator.<Instant>naturalOrder().compare(a, b);
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry sleep interrupted", ex);
        }
    }
}