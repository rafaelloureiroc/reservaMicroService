package com.infnetPb.reservaMicroService.listener;

import com.infnetPb.reservaMicroService.client.NotificationClient;
import com.infnetPb.reservaMicroService.event.MesaReservadaEvent;
import org.apache.log4j.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class MesaReservadaListener {

    private final static Logger logger = Logger.getLogger(MesaReservadaListener.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationClient notificationClient;

    @RabbitListener(queues = "mesaReservadaQueue")
    public void handleMesaReservadaEvent(MesaReservadaEvent event) {

        logger.info("Recebido evento mesaReservada: "+ event);

        NotificationClient.NotificationRequest notificationRequest = new NotificationClient.NotificationRequest();
        notificationRequest.setTo("rafaelloureiro2002@gmail.com");
        notificationRequest.setSubject("Nova Reserva Criada");
        notificationRequest.setBody("Uma nova reserva foi criada.");

        try {
            notificationClient.sendNotification(notificationRequest);
            logger.info("Notificação enviada com sucesso.");
        } catch (Exception e) {
            logger.error("Falha ao comunicar com serviço de notificação: " + e.getMessage());
        }

        boolean success = processEventWithRetry(event);

        if (success) {
            logger.info("Evento mesaReservada processado com sucesso.");
        } else {
            logger.error("Falha ao processar evento mesaReservada após" + MAX_RETRIES + " tentativas.");
        }
    }

    private boolean processEventWithRetry(MesaReservadaEvent event) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                messagingTemplate.convertAndSend("/topic/mesaReservada", event);
                return true;
            } catch (Exception e) {
                logger.error("Erro ao enviar evento (tentativa " + attempt + "): " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return false;
    }
}