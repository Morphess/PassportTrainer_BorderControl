package ua.passporttrainer.map;

import java.util.Objects;

/** One generated Natural Earth feature plus its explicit catalogue relationship. */
public record MapRegion(
        String id,
        String pathData,
        boolean evenOddFill,
        String mapGroupId,
        MapLinkStatus linkStatus,
        String canonicalEntityId,
        String labelKey,
        String reasonCode
) {
    public MapRegion {
        id = requireText(id, "id");
        pathData = requireText(pathData, "pathData");
        mapGroupId = requireText(mapGroupId, "mapGroupId");
        linkStatus = Objects.requireNonNull(linkStatus, "linkStatus");
        labelKey = requireText(labelKey, "labelKey");
        canonicalEntityId = canonicalEntityId == null ? "" : canonicalEntityId;
        reasonCode = reasonCode == null ? "" : reasonCode;
        if (linkStatus == MapLinkStatus.LINKED) {
            requireText(canonicalEntityId, "canonicalEntityId");
            if (!reasonCode.isEmpty()) {
                throw new IllegalArgumentException("Linked map region must not have an unlink reason");
            }
        } else {
            if (!canonicalEntityId.isEmpty()) {
                throw new IllegalArgumentException("Unlinked map region must not reference an entity");
            }
            requireText(reasonCode, "reasonCode");
        }
    }

    public boolean linked() {
        return linkStatus == MapLinkStatus.LINKED;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
