package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.Country;

public interface CountryRepository {

    List<Country> findAll();

    Optional<Country> findById(long id);

    Optional<Country> findByIsoAlpha2(String isoAlpha2);
}
