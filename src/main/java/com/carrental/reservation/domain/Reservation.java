package com.carrental.reservation.domain;

import com.carrental.fleet.domain.CarType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "car_type_id")
    private CarType carType;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Embedded
    private ReservationPeriod period;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ReservationStatus status;

    protected Reservation() {
    }

    private Reservation(CarType carType, String customerId, String idempotencyKey, ReservationPeriod period) {
        this.carType = carType;
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.period = period;
        this.status = ReservationStatus.ACTIVE;
    }

    public static Reservation create(CarType carType, String customerId, String idempotencyKey, ReservationPeriod period) {
        if (customerId == null || customerId.isBlank()) {
            throw new InvalidReservationException("Customer id must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidReservationException("Idempotency key must not be blank");
        }
        return new Reservation(carType, customerId, idempotencyKey, period);
    }

    public boolean isForSameCarTypeAndPeriod(ReservationRequest request) {
        return carType.name().equals(request.carTypeName()) && period.equals(request.toReservationPeriod());
    }

    public UUID id() {
        return id;
    }

    public CarType carType() {
        return carType;
    }

    public String customerId() {
        return customerId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public ReservationPeriod period() {
        return period;
    }

    public ReservationStatus status() {
        return status;
    }
}
