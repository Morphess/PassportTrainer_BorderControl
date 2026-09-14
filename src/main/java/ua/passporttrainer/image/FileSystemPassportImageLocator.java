package ua.passporttrainer.image;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves canonical POSIX-style image paths below one configured read-only asset root. */
public final class FileSystemPassportImageLocator implements PassportImageLocator {

    private final Path assetRoot;

    public FileSystemPassportImageLocator(Path assetRoot) {
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot").toAbsolutePath().normalize();
    }

    @Override
    public URI locate(String sourceRelativePath) {
        Objects.requireNonNull(sourceRelativePath, "sourceRelativePath");
        if (sourceRelativePath.isBlank() || sourceRelativePath.startsWith("/")
                || isWindowsAbsolutePath(sourceRelativePath)
                || sourceRelativePath.contains("\\")) {
            throw invalid(sourceRelativePath);
        }

        Path resolved = assetRoot;
        for (String component : sourceRelativePath.split("/", -1)) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")) {
                throw invalid(sourceRelativePath);
            }
            resolved = resolved.resolve(component);
        }
        resolved = resolved.toAbsolutePath().normalize();
        if (!resolved.startsWith(assetRoot)) {
            throw invalid(sourceRelativePath);
        }
        try {
            Path realRoot = assetRoot.toRealPath();
            Path realPath = resolved.toRealPath();
            if (!realPath.startsWith(realRoot) || !Files.isRegularFile(realPath) || !Files.isReadable(realPath)) {
                throw invalid(sourceRelativePath);
            }
            return realPath.toUri();
        } catch (IOException exception) {
            throw new ImageLocationException("Passport image is unavailable: " + sourceRelativePath);
        }
    }

    private static boolean isWindowsAbsolutePath(String path) {
        return path.length() >= 3
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':'
                && path.charAt(2) == '/';
    }

    private static ImageLocationException invalid(String sourceRelativePath) {
        return new ImageLocationException("Invalid passport image path: " + sourceRelativePath);
    }
}
