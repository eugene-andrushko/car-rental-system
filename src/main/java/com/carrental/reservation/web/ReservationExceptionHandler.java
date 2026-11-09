package com.carrental.reservation.web;

import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.reservation.domain.IdempotencyKeyReusedException;
import com.carrental.reservation.domain.InvalidReservationException;
import com.carrental.reservation.domain.KeyClaimedByConcurrentRequestException;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.PickUpTimePassedException;
import com.carrental.reservation.domain.ReservationNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReservationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ReservationExceptionHandler {

    @ExceptionHandler(InvalidReservationException.class)
    ProblemDetail handleInvalidReservation(InvalidReservationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({UnknownCarTypeException.class, ReservationNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({NoCarAvailableException.class, PickUpTimePassedException.class, KeyClaimedByConcurrentRequestException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }
}
