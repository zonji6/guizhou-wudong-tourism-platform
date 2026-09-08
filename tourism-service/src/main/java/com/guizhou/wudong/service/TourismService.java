package com.guizhou.wudong.service;

import com.guizhou.wudong.api.AgentPendingBookingRequest;
import com.guizhou.wudong.api.BookingRequest;
import com.guizhou.wudong.api.NotFoundException;
import com.guizhou.wudong.domain.Booking;
import com.guizhou.wudong.domain.CommunityPost;
import com.guizhou.wudong.domain.KnowledgeDocument;
import com.guizhou.wudong.domain.ServiceResource;
import com.guizhou.wudong.repository.TourismRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TourismService {
    private static final Set<String> OPERATOR_STATUSES = Set.of("PROCESSING", "COMPLETED", "CANCELLED");
    private final TourismRepository repository;

    public TourismService(TourismRepository repository) {
        this.repository = repository;
    }

    public List<ServiceResource> services(String category) {
        return repository.findServices(category, null, null);
    }

    public ServiceResource service(String id) {
        return repository.findService(id);
    }

    public List<ServiceResource> searchServices(String keywords, String tags) {
        return repository.findServices(null, keywords, tags);
    }

    public List<CommunityPost> posts() {
        return repository.findPosts();
    }

    public List<KnowledgeDocument> knowledge(String keywords) {
        return repository.findKnowledge(keywords);
    }

    public KnowledgeDocument createKnowledge(String title, String content, String tags) {
        String id = UUID.randomUUID().toString();
        repository.insertKnowledge(id, title.trim(), content.trim(), tags);
        return repository.findKnowledge(title).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("知识文档创建失败"));
    }

    public Booking createVisitorBooking(BookingRequest request) {
        return createBooking(request.serviceId(), request.travelDate(), request.peopleCount(), request.contactName(),
                request.contactPhone(), request.note(), "VISITOR", null);
    }

    public Booking createAgentPendingBooking(AgentPendingBookingRequest request) {
        return createBooking(request.serviceId(), request.travelDate(), request.peopleCount(), request.contactName(),
                request.contactPhone(), request.note(), "AGENT", request.threadId());
    }

    private Booking createBooking(String serviceId, java.time.LocalDate travelDate, int peopleCount, String contactName,
                                  String contactPhone, String note, String source, String threadId) {
        service(serviceId);
        String id = UUID.randomUUID().toString();
        repository.insertBooking(id, serviceId, Date.valueOf(travelDate), peopleCount, contactName.trim(),
                contactPhone.trim(), note, "PENDING_CONFIRMATION", source, threadId);
        return repository.findBooking(id);
    }

    public Booking booking(String id) {
        return repository.findBooking(id);
    }

    public Booking confirmByVisitor(String id) {
        Booking booking = repository.findBooking(id);
        if (!"PENDING_CONFIRMATION".equals(booking.status())) {
            throw new IllegalArgumentException("只有待确认预约可由页面确认");
        }
        repository.updateBookingStatus(id, "CONFIRMED");
        return repository.findBooking(id);
    }

    public Booking updateByOperator(String id, String status) {
        if (!OPERATOR_STATUSES.contains(status)) {
            throw new IllegalArgumentException("后台仅可更新为 PROCESSING、COMPLETED 或 CANCELLED");
        }
        repository.findBooking(id);
        repository.updateBookingStatus(id, status);
        return repository.findBooking(id);
    }
}
