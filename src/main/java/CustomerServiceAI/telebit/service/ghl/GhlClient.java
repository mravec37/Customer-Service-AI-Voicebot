package CustomerServiceAI.telebit.service.ghl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class GhlClient {

    private final RestClient restClient;
    private final String apiVersion;
    private final String token;

    public GhlClient(
            @Value("${ghl.base-url}") String baseUrl,
            @Value("${ghl.api-version}") String apiVersion,
            @Value("${ghl.token}") String token
    ) {
        this.apiVersion = apiVersion;
        this.token = token;

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader("Version", apiVersion)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public JsonNode searchConversations(String locationId, String contactId) {
        String uri = UriComponentsBuilder
                .fromPath("/conversations/search")
                .queryParam("locationId", locationId)
                .queryParam("contactId", contactId)
                .build(true)
                .toUriString();

        return getJson(uri);
    }

    public JsonNode getConversationMessages(String conversationId, Integer limit) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/conversations/{conversationId}/messages")
                .queryParam("limit", limit == null ? 100 : limit);

        String uri = builder.buildAndExpand(conversationId).toUriString();
        return getJson(uri);
    }

    public byte[] getRecording(String locationId, String messageId) {
        String uri = UriComponentsBuilder
                .fromPath("/conversations/messages/{messageId}/locations/{locationId}/recording")
                .buildAndExpand(messageId, locationId)
                .toUriString();

        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(byte[].class);
        } catch (HttpStatusCodeException ex) {
            throw new RuntimeException("GHL recording request failed: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("GHL recording request failed", ex);
        }
    }

    private JsonNode getJson(String uri) {
        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException ex) {
            throw new RuntimeException("GHL JSON request failed: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("GHL JSON request failed", ex);
        }
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getTokenPreview() {
        if (!StringUtils.hasText(token)) {
            return "<empty>";
        }
        return token.length() <= 8 ? token : token.substring(0, 8) + "...";
    }
}
