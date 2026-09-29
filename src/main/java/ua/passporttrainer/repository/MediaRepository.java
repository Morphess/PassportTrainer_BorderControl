package ua.passporttrainer.repository;

import java.util.List;
import java.util.Optional;

import ua.passporttrainer.model.MediaMetadata;

/** Metadata persistence boundary; file and catalogue resolution remain service concerns. */
public interface MediaRepository {

    MediaMetadata create(MediaMetadata media);

    MediaMetadata update(MediaMetadata media);

    Optional<MediaMetadata> findById(String mediaId);

    List<MediaMetadata> findAll();
}
