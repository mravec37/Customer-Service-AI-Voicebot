package CustomerServiceAI.telebit.service.transcription;

import java.nio.file.Path;

public interface RecordingTranscriber {

    String transcribe(Path audioFile);
}
