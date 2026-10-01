package com.pthanureddy.scheduling;
import static com.pthanureddy.scheduling.Models.*;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class SchedulingController {
    private final SchedulingService service;
    public SchedulingController(SchedulingService service) { this.service=service; }
    @GetMapping("/slots") public List<Slot> slots(@RequestParam String market,@RequestParam String specialty,@RequestParam String language) { return service.available(market,specialty,language); }
    @GetMapping("/bookings") public List<Booking> bookings() { return service.bookings(); }
    @PostMapping("/bookings") public Booking book(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody BookRequest request) { return service.book(key,request); }
    @PostMapping("/bookings/{id}/rebook") public Booking rebook(@RequestHeader("Idempotency-Key") String key,@PathVariable String id,@Valid @RequestBody RebookRequest request) { return service.rebook(key,id,request); }
    @PostMapping("/bookings/{id}/cancel") public Booking cancel(@RequestHeader("Idempotency-Key") String key,@PathVariable String id) { return service.cancel(key,id); }
}
