package ua.passporttrainer.repository;

import java.util.Optional;

import ua.passporttrainer.model.NewUser;
import ua.passporttrainer.model.User;

public interface UserRepository {

    boolean hasUsers();

    Optional<User> findById(long id);

    Optional<User> findByUsername(String canonicalUsername);

    User create(NewUser newUser);

    Optional<User> createIfEmpty(NewUser newUser);

    User update(User user);
}
