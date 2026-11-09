package ru.mephi.hotel;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mephi.common.*;
@RestController
@RequestMapping("/api/rooms")
@PreAuthorize("hasRole('INTERNAL')")
public class InternalController {
    private final AllocationService allocations;
    public InternalController(AllocationService allocations) {
        this.allocations = allocations;
    }
    @PostMapping("/{id}/confirm-availability")
    public RoomView confirm(@PathVariable long id, @Valid @RequestBody SlotRequest input) {
        return allocations.confirm(id, null, input);
    }
    @PostMapping("/allocate")
    public RoomView allocate(
            @RequestParam(required = false) Long hotelId, @Valid @RequestBody SlotRequest input) {
        return allocations.confirm(null, hotelId, input);
    }
    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable long id, @Valid @RequestBody ReleaseRequest input) {
        allocations.release(id, input.bookingId());
    }
    @PostMapping("/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseUnknownRoom(@Valid @RequestBody ReleaseRequest input) {
        allocations.release(null, input.bookingId());
    }
}
