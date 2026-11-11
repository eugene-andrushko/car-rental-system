package com.carrental.fleet.persistence;

import com.carrental.fleet.domain.CarType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CarTypeRepository extends JpaRepository<CarType, Long> {

    Optional<CarType> findByName(String name);

    // Locked so the calendar job, which locks car types before opening a day, waits for a fleet change to commit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select carType from CarType carType where carType.name = :name")
    Optional<CarType> findAndLockTypeByName(String name);
}
