package ua.passporttrainer.service;

import java.util.Map;
import java.util.Objects;

import ua.passporttrainer.repository.SettingsRepository;

public final class SettingsService {

    private final SettingsRepository settingsRepository;

    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
    }

    public String get(String key, String defaultValue) {
        return settingsRepository.findValue(key).orElse(defaultValue);
    }

    public Map<String, String> findAll() {
        return Map.copyOf(settingsRepository.findAll());
    }

    public void save(String key, String value) {
        settingsRepository.save(key, value);
    }
}
