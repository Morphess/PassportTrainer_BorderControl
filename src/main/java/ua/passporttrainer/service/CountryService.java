package ua.passporttrainer.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.model.Country;
import ua.passporttrainer.repository.CountryRepository;

public final class CountryService {

    private final CountryRepository countryRepository;

    public CountryService(CountryRepository countryRepository) {
        this.countryRepository = Objects.requireNonNull(countryRepository, "countryRepository");
    }

    public List<Country> findAll() {
        return List.copyOf(countryRepository.findAll());
    }

    public Optional<Country> findById(String id) {
        return countryRepository.findById(id);
    }

    public Optional<Country> findByIsoAlpha2(String isoAlpha2) {
        return countryRepository.findByIsoAlpha2(isoAlpha2);
    }
}
