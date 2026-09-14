package ua.passporttrainer.image;

import java.net.URI;

@FunctionalInterface
public interface PassportImageLocator {

    URI locate(String sourceRelativePath);
}
