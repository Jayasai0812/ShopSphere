package com.shopsphere.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage location for uploaded product images. Files live OUTSIDE the
 * database (only URLs/metadata go into MySQL) in a configurable directory,
 * exposed under /images/** by StorageController.
 *
 * app.storage.dir — filesystem directory for image files
 * app.storage.public-prefix — URL prefix served by StorageController
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String dir, String publicPrefix) {

    public StorageProperties {
        if (dir == null || dir.isBlank()) {
            dir = "uploads/images";
        }
        if (publicPrefix == null || publicPrefix.isBlank()) {
            publicPrefix = "/images";
        }
        // Normalize: no trailing slash on the prefix. dir is normalized only
        // for RELATIVE paths — an absolute path (leading '/' on Linux, e.g.
        // /var/lib/shopsphere/images, or a Windows drive like C:\data) is
        // preserved so production storage can live outside the app directory.
        publicPrefix = publicPrefix.endsWith("/")
                ? publicPrefix.substring(0, publicPrefix.length() - 1)
                : publicPrefix;
        boolean absolute = dir.startsWith("/")
                        || dir.startsWith("\\")
                        || (dir.length() >= 2 && dir.charAt(1) == ':');
        if (!absolute) {
                dir = dir.startsWith("/") ? dir.substring(1) : dir;
        }
    }
}
