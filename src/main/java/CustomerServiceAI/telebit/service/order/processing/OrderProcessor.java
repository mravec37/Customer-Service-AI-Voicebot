package CustomerServiceAI.telebit.service.order.processing;

public interface OrderProcessor {

    OrderDetails processOrder(String transcription);
}
