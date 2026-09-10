package com.guizhou.wudong.api;

import com.guizhou.wudong.domain.Booking;
import com.guizhou.wudong.domain.KnowledgeDocument;
import com.guizhou.wudong.service.TourismService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("legacy-v2")
@RequestMapping("/api/admin")
public class AdminController {
    private final TourismService tourismService;

    public AdminController(TourismService tourismService) {
        this.tourismService = tourismService;
    }

    @PostMapping("/knowledge-documents")
    ApiEnvelope<KnowledgeDocument> createKnowledge(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return ApiEnvelope.ok(tourismService.createKnowledge(request.title(), request.content(), request.tags()));
    }

    @PatchMapping("/bookings/{id}/status")
    ApiEnvelope<Booking> updateBookingStatus(@PathVariable String id, @Valid @RequestBody BookingStatusRequest request) {
        return ApiEnvelope.ok(tourismService.updateByOperator(id, request.status()));
    }
}
