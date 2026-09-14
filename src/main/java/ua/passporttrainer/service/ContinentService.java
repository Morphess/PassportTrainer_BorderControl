package ua.passporttrainer.service;

import ua.passporttrainer.model.Continent;
import ua.passporttrainer.repository.ContinentRepository;

import java.util.List;
import java.util.Objects;

public final class ContinentService {

    private final ContinentRepository continentRepository;

    public ContinentService(ContinentRepository continentRepository) {
        this.continentRepository = Objects.requireNonNull(continentRepository, "continentRepository");
    }

    public List<Continent> findAll() {
        return List.copyOf(continentRepository.findAll());
    }
}
