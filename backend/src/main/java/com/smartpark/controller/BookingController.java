package com.smartpark.controller;

import com.smartpark.dto.booking.BookingResponse;
import com.smartpark.dto.booking.CreateBookingRequest;
import com.smartpark.dto.common.PageResponse;
import com.smartpark.entity.BookingStatus;
import com.smartpark.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<PageResponse<BookingResponse>> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<BookingResponse> response = bookingService.getMyBookings(status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<PageResponse<BookingResponse>> getAllBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<BookingResponse> response = bookingService.getAllBookings(status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long id) {
        BookingResponse response = bookingService.getBookingById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<BookingResponse> cancelBookingPatch(@PathVariable Long id) {
        BookingResponse response = bookingService.cancelBooking(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'OPERATOR')")
    public ResponseEntity<BookingResponse> cancelBookingPost(@PathVariable Long id) {
        BookingResponse response = bookingService.cancelBooking(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<BookingResponse> confirmBooking(@PathVariable Long id) {
        BookingResponse response = bookingService.confirmBooking(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<BookingResponse> completeBooking(@PathVariable Long id) {
        BookingResponse response = bookingService.completeBooking(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<BookingResponse> expireBooking(@PathVariable Long id) {
        BookingResponse response = bookingService.expireBooking(id);
        return ResponseEntity.ok(response);
    }
}

