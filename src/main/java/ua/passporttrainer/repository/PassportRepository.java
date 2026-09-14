package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;

public interface PassportRepository {

    Optional<PassportDocument> findById(String id);

    List<PassportDocument> findByCountryId(String countryId);

    List<PassportImage> findImagesByDocumentId(String passportDocumentId);
}
