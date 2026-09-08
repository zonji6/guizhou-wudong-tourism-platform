package com.guizhou.wudong.api;

import com.guizhou.wudong.domain.Booking;
import com.guizhou.wudong.domain.CommunityPost;
import com.guizhou.wudong.domain.KnowledgeDocument;
import com.guizhou.wudong.domain.ServiceResource;
import com.guizhou.wudong.service.TourismService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PublicTourismController {
    private final TourismService tourismService;

    public PublicTourismController(TourismService tourismService) {
        this.tourismService = tourismService;
    }

    @GetMapping("/services")
    ApiEnvelope<List<ServiceResource>> services(@RequestParam(required = false) String category) {
        return ApiEnvelope.ok(tourismService.services(category));
    }

    @GetMapping("/services/{id}")
    ApiEnvelope<ServiceResource> service(@PathVariable String id) {
        return ApiEnvelope.ok(tourismService.service(id));
    }

    @GetMapping("/posts")
    ApiEnvelope<List<CommunityPost>> posts() {
        return ApiEnvelope.ok(tourismService.posts());
    }

    @GetMapping("/knowledge-documents")
    ApiEnvelope<List<KnowledgeDocument>> knowledge(@RequestParam(required = false) String keywords) {
        return ApiEnvelope.ok(tourismService.knowledge(keywords));
    }

    @PostMapping("/bookings")
    ApiEnvelope<Booking> createBooking(@Valid @RequestBody BookingRequest request) {
        return ApiEnvelope.ok(tourismService.createVisitorBooking(request));
    }

    @PostMapping("/bookings/{id}/confirm")
    ApiEnvelope<Booking> confirmBooking(@PathVariable String id) {
        return ApiEnvelope.ok(tourismService.confirmByVisitor(id));
    }

    @GetMapping("/bookings/{id}")
    ApiEnvelope<Booking> booking(@PathVariable String id) {
        return ApiEnvelope.ok(tourismService.booking(id));
    }
}
