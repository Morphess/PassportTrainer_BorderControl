package ua.passporttrainer.repository;

import java.util.Map;
import java.util.Optional;

public interface SettingsRepository {

    Optional<String> findValue(String key);

    Map<String, String> findAll();

    void save(String key, String value);
}
