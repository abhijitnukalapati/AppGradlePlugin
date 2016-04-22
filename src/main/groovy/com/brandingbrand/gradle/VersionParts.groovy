package com.brandingbrand.gradle

/**
 * A class that holds the parts of a version name
 * For example, the version name 3.2.4.1, the various
 * parts are 3 (major), 2 (minor), 4(revision), 1 (build)
 *
 */
public class VersionParts {
    int major;
    int minor;
    int revision;
    int build; // qa build increment

    /**
     * Increments the build number
     */
    def incrementBuild() {
        // if this is first qa, increment
        // the revision as the earlier revision
        // should have already been released
        if(this.build == 0) {
            this.revision++;
        }
        this.build++;
    }

    /**
     * Calculates the version code based on
     * a standard formula
     */
    int calculateVersionCode() {
        return (1000000 * this.major
                  + 10000 * this.minor
                  + 100   * this.revision
                  + this.build)
    }

    /**
     * Builds the version name as a String using the
     * parts of the version. If a release version name
     * is being built, the build number is dropped
     */
    String buildVersionName(boolean isRelease = false) {
        if(isRelease) {
            return "${this.major}.${this.minor}.${this.revision}"
        } else {
            return "${this.major}.${this.minor}.${this.revision}.${this.build}"
        }
    }

}
