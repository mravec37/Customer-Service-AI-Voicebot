package CustomerServiceAI.telebit.service.order.processing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiOrderProcessor implements OrderProcessor {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiOrderProcessor(@Value("${openai.api-key}") String apiKey) {

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public OrderDetails processOrder(String transcription) {

        try {

            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", """
            Si systém na extrakciu objednávok z telefonických rozhovorov v slovenčine.

            Vráť LEN validný JSON.

            Formát:
            {
               "items":[{"name":"produkt","quantity": číslo alebo null,"unit":"ks|kg|g|l|ml|null"}],
               "address":"text alebo null"
            }

            Pravidlá:
            - extrahuj produkty ktoré zákazník objednáva
            - extrahuj dodaciu adresu ak je spomenutá
            - ak zákazník nepovie adresu → address=null
            - ak povie viac údajov (ulica, mesto, PSČ), spoj ich do jedného textu
            - ignoruj zdvorilostné frázy
            - default množstvo = 1 ks ak nie je jasné
            - vráť iba JSON, nič iné
            """);

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", transcription);

            Map<String, Object> request = new HashMap<>();
            request.put("model", "gpt-4.1-mini");
            request.put("response_format", Map.of("type", "json_object"));
            request.put("messages", List.of(systemMessage, userMessage));

            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            Map choice = (Map) ((List) response.get("choices")).get(0);
            Map message = (Map) choice.get("message");

            String json = message.get("content").toString();

            var orderDetails = objectMapper.readValue(json, OrderDetails.class);
            orderDetails.setTranscription(transcription);

            return orderDetails;

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract order from transcription", e);
        }
    }
}