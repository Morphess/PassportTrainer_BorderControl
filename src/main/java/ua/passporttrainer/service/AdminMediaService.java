package ua.passporttrainer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import ua.passporttrainer.model.MediaMetadata;
import ua.passporttrainer.model.MediaReferenceType;
import ua.passporttrainer.repository.MediaRepository;
import ua.passporttrainer.security.AuthenticatedUser;

/** Authorization-aware metadata boundary; it performs no media file operations. */
public final class AdminMediaService {

    private final AuthorizationService authorizationService;
    private final MediaRepository mediaRepository;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    public AdminMediaService(
            AuthorizationService authorizationService,
            MediaRepository mediaRepository,
            Clock clock,
            Supplier<String> idSupplier
    ) {
        this.authorizationService = Objects.requireNonNull(
                authorizationService, "authorizationService"
        );
        this.mediaRepository = Objects.requireNonNull(mediaRepository, "mediaRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    public MediaMetadata createMetadata(
            MediaReferenceType referenceType,
            String reference,
            String mimeType,
            OptionalLong sizeBytes,
            Optional<String> sha256
    ) {
        AuthenticatedUser administrator = authorizationService.requireAdmin();
        Instant now = clock.instant();
        return mediaRepository.create(new MediaMetadata(
                nextId(),
                referenceType,
                reference,
                mimeType,
                sizeBytes,
                sha256,
                administrator.id(),
                now,
                now,
                false
        ));
    }

    public Optional<MediaMetadata> findMetadata(String mediaId) {
        authorizationService.requireAdmin();
        return mediaRepository.findById(mediaId);
    }

    public List<MediaMetadata> listMetadata() {
        authorizationService.requireAdmin();
        return mediaRepository.findAll();
    }

    public MediaMetadata archiveMetadata(String mediaId) {
        authorizationService.requireAdmin();
        MediaMetadata media = mediaRepository.findById(mediaId).orElseThrow(() ->
                new IllegalArgumentException("Unknown media metadata ID: " + mediaId)
        );
        if (media.archived()) {
            return media;
        }
        return mediaRepository.update(new MediaMetadata(
                media.mediaId(),
                media.referenceType(),
                media.reference(),
                media.mimeType(),
                media.sizeBytes(),
                media.sha256(),
                media.createdBy(),
                media.createdAt(),
                timestampNotBefore(media.updatedAt()),
                true
        ));
    }

    private Instant timestampNotBefore(Instant previous) {
        Instant now = clock.instant();
        return now.isBefore(previous) ? previous : now;
    }

    private String nextId() {
        String token = Objects.requireNonNull(idSupplier.get(), "generated ID");
        if (token.isBlank()) {
            throw new IllegalStateException("Generated ID must not be blank");
        }
        return "media:" + token;
    }
}
