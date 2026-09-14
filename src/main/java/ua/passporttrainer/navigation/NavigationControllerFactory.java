package ua.passporttrainer.navigation;

@FunctionalInterface
public interface NavigationControllerFactory {

    Object create(Class<?> controllerType, NavigationService navigationService);
}
