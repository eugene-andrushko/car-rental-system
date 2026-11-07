package com.carrental.fleet.persistence;

import com.carrental.fleet.domain.CarType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarTypeRepository extends JpaRepository<CarType, Long> {

    Optional<CarType> findByName(String name);
}
