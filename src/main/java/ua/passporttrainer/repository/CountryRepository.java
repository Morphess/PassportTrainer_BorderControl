package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.Country;

public interface CountryRepository {

    List<Country> findAll();

    List<Country> findByContinentId(String continentId);

    Optional<Country> findById(String id);

    Optional<Country> findByIsoAlpha2(String isoAlpha2);
}
