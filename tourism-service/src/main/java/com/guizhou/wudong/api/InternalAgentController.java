package com.guizhou.wudong.api;

import com.guizhou.wudong.domain.Booking;
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
@RequestMapping("/internal/agent")
public class InternalAgentController {
    private final TourismService tourismService;

    public InternalAgentController(TourismService tourismService) {
        this.tourismService = tourismService;
    }

    @GetMapping("/services/search")
    ApiEnvelope<List<ServiceResource>> searchServices(@RequestParam(required = false) String keywords,
                                                       @RequestParam(required = false) String tags) {
        return ApiEnvelope.ok(tourismService.searchServices(keywords, tags));
    }

    @GetMapping("/knowledge/search")
    ApiEnvelope<List<KnowledgeDocument>> searchKnowledge(@RequestParam(required = false) String keywords) {
        return ApiEnvelope.ok(tourismService.knowledge(keywords));
    }

    @PostMapping("/pending-bookings")
    ApiEnvelope<Booking> createPendingBooking(@Valid @RequestBody AgentPendingBookingRequest request) {
        return ApiEnvelope.ok(tourismService.createAgentPendingBooking(request));
    }

    @GetMapping("/bookings/{id}")
    ApiEnvelope<Booking> booking(@PathVariable String id) {
        return ApiEnvelope.ok(tourismService.booking(id));
    }
}
