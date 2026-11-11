package com.carrental.reservation.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

record ChangeCarCountBody(@NotNull @PositiveOrZero Integer carCount) {
}
