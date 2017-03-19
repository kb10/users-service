package com.membaza.api.users.controller;

import com.membaza.api.users.controller.dto.LoginDto;
import com.membaza.api.users.persistence.model.User;
import com.membaza.api.users.service.jwt.JwtService;
import com.membaza.api.users.service.jwt.JwtToken;
import com.membaza.api.users.throwable.InvalidUsernameOrPasswordException;
import com.membaza.api.users.throwable.UserDisabledException;
import com.membaza.api.users.throwable.UserNotConfirmedException;
import com.membaza.api.users.throwable.UserNotFoundException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * @author Emil Forslund
 * @since  1.0.0
 */
@RestController
@RequestMapping("/users")
public class LoginController {

    private final JwtService jws;
    private final MongoTemplate mongo;
    private final PasswordEncoder passwordEncoder;

    public LoginController(JwtService jws,
                           MongoTemplate mongo,
                           PasswordEncoder passwordEncoder) {

        this.jws             = requireNonNull(jws);
        this.mongo           = requireNonNull(mongo);
        this.passwordEncoder = requireNonNull(passwordEncoder);
    }

    @PostMapping("/login")
    JwtToken login(@RequestBody LoginDto login) {
        final User user = getLoggedIn(login);
        return jws.createLoginToken(user);
    }

    @PostMapping("/refresh")
    JwtToken refresh(@RequestBody JwtToken currentToken) {
        final User user = getLoggedIn(currentToken);
        return jws.createLoginToken(user);
    }

    @PostMapping("/logout")
    void logout(@RequestBody JwtToken currentToken) {
        // Do nothing currently.
    }

    private User getLoggedIn(LoginDto login) {
        final User user = mongo.findOne(query(where("email").is(login.getEmail())), User.class);
        if (user == null || !passwordEncoder.matches(
                login.getPassword(),
                user.getPassword())) {

            throw new InvalidUsernameOrPasswordException(
                "Invalid username or password."
            );
        }

        assertUserStatus(user);
        return user;
    }

    private User getLoggedIn(JwtToken token) {
        final String userId = jws.validate(token).getSubject();
        final User user     = mongo.findOne(query(where("id").is(userId)), User.class);

        if (user == null) {
            throw new UserNotFoundException(
                "User '" + userId + "' no longer exists."
            );
        }

        assertUserStatus(user);
        return user;
    }

    private void assertUserStatus(User user) {
        if (!user.isConfirmed()) {
            throw new UserNotConfirmedException(user);
        }

        if (!user.isEnabled()) {
            throw new UserDisabledException(user);
        }
    }
}
