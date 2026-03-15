package CustomerServiceAI.telebit.service.transcription;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.Map;

@Service
public class OpenAiRecordingTranscriber implements RecordingTranscriber {
    private final RestClient restClient;
    private final String model;
    private final String language;

    public OpenAiRecordingTranscriber(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.transcription.model:gpt-4o-transcribe}") String model,
            @Value("${openai.transcription.language:sk}") String language
    ) {
        this.model = model;
        this.language = language;

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String transcribe(Path audioFile) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(audioFile));
            body.add("model", model);
            body.add("language", language);

            Map<?, ?> response = restClient.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("text") == null) {
                throw new IllegalStateException("OpenAI transcription response did not contain text");
            }

            return response.get("text").toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to transcribe audio file: " + audioFile, e);
        }
    }
}
