package com.infnetPb.reservaMicroService.repository.history;

import com.infnetPb.reservaMicroService.model.history.ReservaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReservaHistoryRepository extends JpaRepository<ReservaHistory, UUID> {
}
