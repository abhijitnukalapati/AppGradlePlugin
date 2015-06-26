![logo](logo.png)

# BBAppGradlePlugin
Gradle build plugin for Brandingbrand Android Apps

We have a custom plugin `BBAppBuildPlugin` that sets up all the tasks and plugin configuration.

## Usage

The plugin has the following properties that can be configured

- `frameworkVersion`
 The framework version that the client will be using
 _Default value_: null
 
- `hockeyAppDescription`
 The description for the HockeyApp uploads. This will be visible right below the version number
in the HockeyApp Release Notes
_Default value_: 'No description provided'

- `useMavenVersioning`
A boolean to indicate if the maven multiplier has to be used while computing the version code. See `SetupReleaseVersion` task for more info
_Default value_: false

**Sample Usage**
```
buildscript {
    repositories {
        mavenCentral()
        repositories {
            maven {
                url "http://nexus.brandingbrand.com/nexus/content/repositories/releases/"
                credentials {
                    username NEXUS_USER
                    password NEXUS_PASSWORD
                }
            }
        }
    }
    dependencies {
        classpath group: 'com.brandingbrand', name: 'BBAppGradlePlugin',
                version: insertVersion
    }
}

apply plugin: 'bbapp'

bbapp {
  frameworkVersion '3.5.7-SNAPSHOT'
  hockeyAppDescription 'Android Stage app'
  useMavenVersioning true
}
```

## Plugins

- The HockeyApp plugin is applied and configured
- The Android plugin is applied

## Tasks

The following tasks are available as part of the plugin:

- `bumpVersionCode` assumes that the buildtype is something other than a "release" and simply increments the version code.

 **Sample usage**: `./gradlew bumpVersionCode installUat`

- `setupReleaseVersion` takes a parameter `releaseVersionName` to set the release version
and updates the version code accordingly. This task occurs prior to `assembleRelease` or
`installRelease`. So it doesn't need to be explicitly called but the parameter has to be
set via the command line; otherwise the task is skipped and the version name/code aren't
updated.
**Sample usage**: `./gradlew assembleRelease -PreleaseVersionName=4.3.2`

- `pushVersionToGithub` to automatically push changes to gradle.properties whenever a build is uploaded to hockeyapp

 **Sample usage**: `./gradlew pushVersionToGithub`
 *Please note, however, that this task typically doesn't have to be called explicitly. It is implicitly invoked everytime a hockeyapp task is executed*

- `printFrameworkVersion`, as the name suggests, prints the name of the current framework version that the client(app) is using. *This is run during the configuration phase*

 **Sample usage**: `./gradlew printFrameworkVersion`

## Releasing a new version

- Update the `version` in `build.gradle`. For example, if is 1.0, change it to 1.1

- Please make sure you run the following command prior to releasing a new version, to ensure the build isn't failing:

 `./gradlew clean check`

- To upload a new version, the following command would accomplish the upload to the nexus servers:

 `./gradlew uploadArchives`
