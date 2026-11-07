package com.carrental.reservation.persistence;

import com.carrental.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

    Optional<Reservation> findByIdAndCustomerId(UUID id, String customerId);

    @Modifying
    @Query("""
            update Reservation reservation
            set reservation.status = CANCELLED
            where reservation.id = :id and reservation.customerId = :customerId and reservation.status = ACTIVE
            """)
    int cancelIfActiveAndOwnedBy(UUID id, String customerId);
}
