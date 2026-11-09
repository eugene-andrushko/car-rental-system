package com.carrental.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;

@Entity
@Table(name = "car_type")
public class CarType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "car_count", nullable = false)
    private int carCount;

    protected CarType() {
    }

    public CarType(String name, int carCount) {
        this.name = name;
        this.carCount = carCount;
    }

    public static String normalizeName(String name) {
        return name.strip().toUpperCase(Locale.ROOT);
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int carCount() {
        return carCount;
    }
}
