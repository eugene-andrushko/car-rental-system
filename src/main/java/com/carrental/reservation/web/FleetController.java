package com.carrental.reservation.web;

import com.carrental.reservation.application.FleetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/car-types")
@Tag(name = "Fleet")
class FleetController {

    private final FleetService fleetService;

    FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @PutMapping("/{carTypeName}/car-count")
    @Operation(summary = "Set how many cars of a type the fleet has", responses = {
            @ApiResponse(responseCode = "200", description = "Changed; the new count applies at once to every open day"),
            @ApiResponse(responseCode = "400", description = "Missing or negative car count"),
            @ApiResponse(responseCode = "404", description = "Unknown car type"),
            @ApiResponse(responseCode = "409", description = "More cars are already reserved on some day than the new count")})
    CarTypeResponse changeCarCount(@PathVariable String carTypeName, @Valid @RequestBody ChangeCarCountBody body) {
        return CarTypeResponse.from(fleetService.changeCarCount(carTypeName, body.carCount()));
    }
}
