package com.carrental.reservation.web;

import com.carrental.reservation.application.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/reservations")
@Tag(name = "Reservations")
class ReservationController {

    private final ReservationService reservationService;

    ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserve a car of a given type", responses = {
            @ApiResponse(responseCode = "201", description = "Reserved, or the same request retried with the same Idempotency-Key"),
            @ApiResponse(responseCode = "400", description = "Invalid body, missing Idempotency-Key, or period outside the rules"),
            @ApiResponse(responseCode = "404", description = "Unknown car type"),
            @ApiResponse(responseCode = "409", description = "No car of that type is free for the whole period"),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key already used for a different request"),
            @ApiResponse(responseCode = "503", description = "Temporarily busy; retry with the same Idempotency-Key")})
    ReservationResponse reserve(
            @Parameter(description = "Unique per customer; a retry with the same key returns the original reservation")
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateReservationBody body) {
        return ReservationResponse.from(reservationService.reserve(body.toReservationRequest(idempotencyKey)));
    }

    @DeleteMapping("/{reservationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a reservation before it starts", responses = {
            @ApiResponse(responseCode = "204", description = "Cancelled, or already cancelled"),
            @ApiResponse(responseCode = "404", description = "Reservation not found, or owned by another customer"),
            @ApiResponse(responseCode = "409", description = "The reservation has already started")})
    void cancel(@PathVariable UUID reservationId, @RequestParam @NotBlank String customerId) {
        reservationService.cancel(reservationId, customerId);
    }
}
