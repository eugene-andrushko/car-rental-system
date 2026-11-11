package com.carrental.web;

import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.reservation.domain.CarsAlreadyReservedException;
import com.carrental.reservation.domain.IdempotencyKeyReusedException;
import com.carrental.reservation.domain.InvalidReservationException;
import com.carrental.reservation.domain.KeyClaimedByConcurrentRequestException;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.PickUpTimePassedException;
import com.carrental.reservation.domain.ReservationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "1";

    @ExceptionHandler(InvalidReservationException.class)
    ProblemDetail handleInvalidReservation(InvalidReservationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({UnknownCarTypeException.class, ReservationNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({NoCarAvailableException.class, PickUpTimePassedException.class, KeyClaimedByConcurrentRequestException.class,
            CarsAlreadyReservedException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<ProblemDetail> handleTemporaryDatabaseFailure(TransientDataAccessException exception) {
        log.warn("Temporary database failure, the client may retry", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "The request could not be processed now, please retry");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong, please try again");
    }
}
