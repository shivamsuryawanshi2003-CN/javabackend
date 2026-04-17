package com.jobra.authservice.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Locale;
import java.util.Map;

/**
 * Normalizes email/name from Google, GitHub, Microsoft (Entra), etc.
 */
public final class OAuth2UserAttributes {

    private OAuth2UserAttributes() {
    }

    public static String email(String registrationId, OAuth2User user) {
        String id = registrationId != null ? registrationId.toLowerCase(Locale.ROOT) : "";
        Map<String, Object> a = user.getAttributes();

        return switch (id) {
            case "github" -> githubEmail(a);
            case "google" -> stringAttr(a, "email");
            case "linkedin" -> firstNonBlank(
                    stringAttr(a, "email"),
                    stringAttr(a, "preferred_username")
            );
            case "azure", "microsoft" -> firstNonBlank(
                    stringAttr(a, "email"),
                    stringAttr(a, "preferred_username"),
                    stringAttr(a, "unique_name")
            );
            default -> firstNonBlank(
                    stringAttr(a, "email"),
                    stringAttr(a, "preferred_username")
            );
        };
    }

    public static String name(String registrationId, OAuth2User user) {
        String id = registrationId != null ? registrationId.toLowerCase(Locale.ROOT) : "";
        Map<String, Object> a = user.getAttributes();
        return switch (id) {
            case "github" -> firstNonBlank(stringAttr(a, "name"), stringAttr(a, "login"));
            case "linkedin" -> firstNonBlank(
                    stringAttr(a, "name"),
                    stringAttr(a, "localizedFirstName"),
                    stringAttr(a, "given_name")
            );
            default -> firstNonBlank(stringAttr(a, "name"), stringAttr(a, "given_name"));
        };
    }

    private static String githubEmail(Map<String, Object> a) {
        String email = stringAttr(a, "email");
        if (email != null) {
            return email;
        }
        String login = stringAttr(a, "login");
        if (login != null) {
            return login + "@github.com";
        }
        return null;
    }

    private static String stringAttr(Map<String, Object> a, String key) {
        Object v = a.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
