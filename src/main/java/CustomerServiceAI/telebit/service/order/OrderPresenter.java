package CustomerServiceAI.telebit.service.order;

import CustomerServiceAI.telebit.service.EmailService;
import CustomerServiceAI.telebit.service.order.processing.OrderDetails;
import CustomerServiceAI.telebit.service.order.processing.OrderItem;
import org.springframework.stereotype.Service;

@Service
public class OrderPresenter {

    private final EmailService emailService;

    public OrderPresenter(EmailService emailService) {
        this.emailService = emailService;
    }

    public void presentOrder(OrderDetails orderDetails) {
        StringBuilder body = new StringBuilder();

        body.append("Objednávka:\n");

        if (orderDetails.getItems() != null) {
            for (OrderItem item : orderDetails.getItems()) {
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                String unit = item.getUnit() != null ? item.getUnit() : "ks";

                body.append("- ")
                        .append(item.getName())
                        .append(" (")
                        .append(qty)
                        .append(" ")
                        .append(unit)
                        .append(")\n");
            }
        }

        body.append("Adresa:\n");

        if(orderDetails.getAddress() != null)
            body.append(orderDetails.getAddress());
        else
            body.append("Neuvedená");

        body.append("\n\n-------------------------\n");
        body.append("PREPIS HOVORU:\n");

        if (orderDetails.getTranscription() != null) {
            body.append(orderDetails.getTranscription());
        }

        System.out.println("Email body: " + body.toString());

        emailService.sendEmail(
                "tomasmravec@telebit.sk",
                "Order",
                body.toString()
        );
    }
}
