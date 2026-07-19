package com.meant.api.module.user.service;

import com.meant.api.module.user.exception.UserException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class UserOwnedImagePathValidator {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private UserOwnedImagePathValidator() {
    }

    static String normalize(UUID userId, String objectPath, String fieldName) {
        String normalized = objectPath == null ? "" : objectPath.trim();
        String expectedPrefix = userId + "/";
        String fileName = normalized.startsWith(expectedPrefix)
                ? normalized.substring(expectedPrefix.length())
                : "";
        if (!isAllowedImageFileName(fileName)) {
            throw new UserException(fieldName
                    + " must point to the authenticated user's JPG, JPEG, PNG, or WebP image object");
        }
        return normalized;
    }

    private static boolean isAllowedImageFileName(String fileName) {
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("..")) {
            return false;
        }
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart <= 0 || extensionStart == fileName.length() - 1) {
            return false;
        }
        String baseName = fileName.substring(0, extensionStart);
        String extension = fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
        return baseName.matches("[A-Za-z0-9._-]+") && IMAGE_EXTENSIONS.contains(extension);
    }
}
