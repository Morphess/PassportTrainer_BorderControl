package ua.passporttrainer.repository;

import ua.passporttrainer.model.Continent;

import java.util.List;

public interface ContinentRepository {

    List<Continent> findAll();
}
