package com.infnetPb.reservaMicroService.service;


import com.infnetPb.reservaMicroService.DTO.MesaDTO;
import com.infnetPb.reservaMicroService.DTO.ReservaDTO;
import com.infnetPb.reservaMicroService.DTO.RestauranteDTO;
import com.infnetPb.reservaMicroService.client.MesaClient;
import com.infnetPb.reservaMicroService.client.RestauranteClient;
import com.infnetPb.reservaMicroService.event.MesaReservadaEvent;
import com.infnetPb.reservaMicroService.model.Reserva;
import com.infnetPb.reservaMicroService.model.history.ReservaHistory;
import com.infnetPb.reservaMicroService.repository.ReservaRepository;
import com.infnetPb.reservaMicroService.repository.history.ReservaHistoryRepository;
import jakarta.transaction.Transactional;
import org.apache.log4j.Logger;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReservaService {

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private ReservaHistoryRepository reservaHistoryRepository;

    @Autowired
    private RestauranteClient restauranteClient;

    @Autowired
    private MesaClient mesaClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final static Logger logger = Logger.getLogger(ReservaService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    @Transactional
    public ReservaDTO createReserva(ReservaDTO reservaDTO) {
        MesaDTO mesa = mesaClient.getMesaById(reservaDTO.getMesaId());
        if (mesa == null) {
            logger.error("Mesa não encontrada");
            throw new RuntimeException("Mesa não encontrada");
        }

        if (mesa.getReservaId() != null) {
            logger.error("A Mesa já está reservada");
            throw new RuntimeException("A Mesa já está reservada");
        }

        RestauranteDTO restaurante = restauranteClient.getRestauranteById(reservaDTO.getRestauranteId());
        if (restaurante == null) {
            logger.error("Restaurante não encontrado");
            throw new RuntimeException("Restaurante não encontrado");
        }

        Reserva reserva = new Reserva();
        reserva.setDataReserva(reservaDTO.getDataReserva());
        reserva.setQuantidadePessoas(reservaDTO.getQuantidadePessoas());
        reserva.setMesaId(reservaDTO.getMesaId());
        reserva.setRestauranteId(reservaDTO.getRestauranteId());

        Reserva savedReserva = reservaRepository.save(reserva);
        saveReservaHistory(savedReserva, "CREATE");

        mesa.setReservaId(savedReserva.getId());
        mesaClient.updateMesa(mesa.getId(), mesa);

        MesaReservadaEvent event = new MesaReservadaEvent(
                mesa.getId(),
                restaurante.getId(),
                reserva.getDataReserva());


        logger.info("Tentando enviar evento MesaReservada: " + event);

        CompletableFuture.runAsync(() -> {
            boolean success = sendEventWithRetry(event, "mesaExchange", "mesaReservada");
            if (success) {
                logger.info("Evento MesaReservada enviado com sucesso.");
            } else {
                logger.error("Falha ao enviar evento MesaReservada após " + MAX_RETRIES + " tentativas.");
            }
        });

        return mapToDTO(savedReserva);
    }

    private boolean sendEventWithRetry(Object event, String exchange, String routingKey) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, event);
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

    public List<ReservaDTO> getAllReservas() {
        List<Reserva> reservas = reservaRepository.findAll();
        return reservas.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ReservaDTO updateReserva(UUID id, ReservaDTO reservaDTO) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));

        reserva.setDataReserva(reservaDTO.getDataReserva());
        reserva.setQuantidadePessoas(reservaDTO.getQuantidadePessoas());

        Reserva updatedReserva = reservaRepository.save(reserva);
        saveReservaHistory(updatedReserva, "UPDATE");

        return mapToDTO(updatedReserva);
    }


    public ReservaDTO getReservaById(UUID id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        return mapToDTO(reserva);
    }

    public void deleteReservaById(UUID id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));

        reservaRepository.deleteById(id);
        saveReservaHistory(reserva, "DELETE");
    }

    public List<ReservaHistory> getAllReservaHistories() {
        return reservaHistoryRepository.findAll();
    }

    private void saveReservaHistory(Reserva reserva, String operation) {
        ReservaHistory history = new ReservaHistory();
        history.setReserva(reserva);
        history.setDataReserva(reserva.getDataReserva().atStartOfDay());
        history.setQuantidadePessoas(reserva.getQuantidadePessoas());
        history.setTimestamp(LocalDateTime.now());
        history.setOperation(operation);
        reservaHistoryRepository.save(history);
    }

    private ReservaDTO mapToDTO(Reserva reserva) {
        ReservaDTO reservaDTO = new ReservaDTO();
        reservaDTO.setId(reserva.getId());
        reservaDTO.setDataReserva(reserva.getDataReserva());
        reservaDTO.setQuantidadePessoas(reserva.getQuantidadePessoas());
        reservaDTO.setRestauranteId(reserva.getRestauranteId());
        reservaDTO.setMesaId(reserva.getMesaId());
        return reservaDTO;
    }
}