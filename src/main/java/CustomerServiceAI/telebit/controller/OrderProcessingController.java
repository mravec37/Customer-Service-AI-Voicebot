package CustomerServiceAI.telebit.controller;

import CustomerServiceAI.telebit.service.ghl.VoiceRecordingRetriever;
import CustomerServiceAI.telebit.service.order.OrderPresenter;
import CustomerServiceAI.telebit.service.order.processing.OrderProcessor;
import CustomerServiceAI.telebit.service.transcription.RecordingTranscriber;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api")
public class OrderProcessingController {

    private final VoiceRecordingRetriever voiceRecordingRetriever;
    private final RecordingTranscriber recordingTranscriber;

    private final OrderPresenter orderPresenter;

    private final OrderProcessor orderProcessor;

    public OrderProcessingController(VoiceRecordingRetriever voiceRecordingRetriever, RecordingTranscriber recordingTranscriber, OrderPresenter orderPresenter, OrderProcessor orderProcessor) {
        this.voiceRecordingRetriever = voiceRecordingRetriever;
        this.recordingTranscriber = recordingTranscriber;
        this.orderPresenter = orderPresenter;
        this.orderProcessor = orderProcessor;
    }


    @PostMapping("/notify")
    public ResponseEntity<Void> handleCallFinishedWebhook(@RequestBody String payload) {
        System.out.println("Notify method called");
        try {
            Path recordingPath = voiceRecordingRetriever.retrieveRecording(payload);
            String transription = recordingTranscriber.transcribe(recordingPath);
            System.out.println("Transcription: " + transription);

            //order processor  processes the transcription into order details and order presenter presents the order
            var orderDetails = orderProcessor.processOrder(transription);
            orderPresenter.presentOrder(orderDetails);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

}
