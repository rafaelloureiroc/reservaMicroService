package com.infnetPb.reservaMicroService.repository;

import com.infnetPb.reservaMicroService.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {
}
