package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.PassportDocument;
import ua.passporttrainer.model.PassportImage;

public interface PassportRepository {

    Optional<PassportDocument> findById(long id);

    List<PassportDocument> findByCountryId(long countryId);

    List<PassportImage> findImagesByDocumentId(long passportDocumentId);
}
