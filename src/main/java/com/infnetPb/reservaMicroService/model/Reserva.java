package com.infnetPb.reservaMicroService.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
public class Reserva {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;
    private LocalDate dataReserva;
    private int quantidadePessoas;

    private UUID mesaId;
    private UUID restauranteId;
}