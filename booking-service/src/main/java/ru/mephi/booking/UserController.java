package ru.mephi.booking;

import static ru.mephi.booking.UserDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mephi.common.PageDto;
@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService users;
    public UserController(UserService users) {
        this.users = users;
    }
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Token register(@Valid @RequestBody Credentials input) {
        return users.register(input);
    }
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/auth")
    public Token authenticate(@Valid @RequestBody Credentials input) {
        return users.authenticate(input);
    }
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUser input) {
        return users.create(input);
    }
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageDto<UserView> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.list(page, size);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView get(@PathVariable long id) {
        return users.get(id);
    }
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView update(@PathVariable long id, @Valid @RequestBody UpdateUser input) {
        return users.update(id, input);
    }
    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserView updateByQuery(@RequestParam long id, @Valid @RequestBody UpdateUser input) {
        return users.update(id, input);
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        users.delete(id);
    }
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByQuery(@RequestParam long id) {
        users.delete(id);
    }
}
