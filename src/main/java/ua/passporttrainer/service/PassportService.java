package ua.passporttrainer.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;
import ua.passporttrainer.repository.PassportRepository;

public final class PassportService {

    private final PassportRepository passportRepository;

    public PassportService(PassportRepository passportRepository) {
        this.passportRepository = Objects.requireNonNull(passportRepository, "passportRepository");
    }

    public Optional<PassportDocument> findById(String id) {
        return passportRepository.findById(id);
    }

    public List<PassportDocument> findByCountryId(String countryId) {
        return List.copyOf(passportRepository.findByCountryId(countryId));
    }

    public List<PassportImage> findImages(String passportDocumentId) {
        return List.copyOf(passportRepository.findImagesByDocumentId(passportDocumentId));
    }
}
