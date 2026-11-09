package ru.mephi.booking;

import jakarta.validation.constraints.*;
public final class UserDtos {
    private UserDtos() {
    }
    public record Credentials(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") String username,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }
    public record CreateUser(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") String username,
            @NotBlank @Size(min = 8, max = 72) String password, @NotNull User.Role role) {
    }
    public record UpdateUser(@Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") String username,
            @Size(min = 8, max = 72) String password, User.Role role) {
    }
    public record UserView(Long id, String username, User.Role role) {
        static UserView from(User u) {
            return new UserView(u.id, u.username, u.role);
        }
    }
    public record Token(String accessToken, String tokenType, long expiresIn, UserView user) {
    }
}
