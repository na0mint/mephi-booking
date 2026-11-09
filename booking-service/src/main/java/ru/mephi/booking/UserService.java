package ru.mephi.booking;

import static ru.mephi.booking.UserDtos.*;

import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.common.*;
@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    public UserService(UserRepository users, PasswordEncoder passwords, JwtTokens tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }
    @Transactional
    public Token register(Credentials input) {
        return token(create(input.username(), input.password(), User.Role.USER));
    }
    public Token authenticate(Credentials input) {
        var user = users.findByUsername(input.username());
        if (user.isEmpty() || !user.get().active || !passwords.matches(input.password(), user.get().password))
            throw new ApiException(401, "INVALID_CREDENTIALS", "Invalid username or password");
        return token(user.get());
    }
    @Transactional
    public UserView create(CreateUser input) {
        return UserView.from(create(input.username(), input.password(), input.role()));
    }
    private User create(String username, String password, User.Role role) {
        if (users.findByUsername(username).isPresent())
            throw ApiException.conflict("Username already exists");
        return users.saveAndFlush(new User(username, encode(password), role));
    }
    @Transactional
    public UserView update(long id, UpdateUser input) {
        var u = users.lockById(id).filter(x -> x.active).orElseThrow(() -> ApiException.notFound("User"));
        if (input.username() != null)
            u.username = input.username();
        if (input.password() != null)
            u.password = encode(input.password());
        if (input.role() != null)
            u.role = input.role();
        users.flush();
        return UserView.from(u);
    }
    @Transactional
    public void delete(long id) {
        var u = users.lockById(id).filter(x -> x.active).orElseThrow(() -> ApiException.notFound("User"));
        u.active = false;
    }
    public UserView get(long id) {
        return UserView.from(requireActive(id));
    }
    public PageDto<UserView> list(int page, int size) {
        return PageDto.from(
                users.findByActiveTrue(PageRequest.of(page, size, Sort.by("id"))).map(UserView::from));
    }
    User requireActive(long id) {
        return users.findById(id)
                .filter(x -> x.active)
                .orElseThrow(() -> new ApiException(401, "USER_DISABLED", "User is unavailable"));
    }
    private Token token(User u) {
        return new Token(tokens.issue(u.id.toString(), u.role.name()), "Bearer", 3600, UserView.from(u));
    }
    private String encode(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400, "INVALID_PASSWORD", "Password must fit in 72 UTF-8 bytes");
        return passwords.encode(password);
    }
}
