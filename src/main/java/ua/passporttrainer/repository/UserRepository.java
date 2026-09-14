package ua.passporttrainer.repository;

import java.util.Optional;

import ua.passporttrainer.model.User;

public interface UserRepository {

    Optional<User> findById(long id);

    Optional<User> findByUsername(String username);

    User save(User user);
}
