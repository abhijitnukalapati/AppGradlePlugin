package com.brandingbrand.gradle.tasks;

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import com.brandingbrand.gradle.BBAppBuildPlugin
import com.brandingbrand.gradle.VersionParts

/**
 * Created by abhijitnukalapati on 2/24/15.
 */
public class PrepReleaseVersionTask extends DefaultTask {

    PrepReleaseVersionTask() {
        setDescription('Sets the release version code either by using the' + '\n'
                        +   'release version name if provided via the command line (Ex: -PappVersionName=2.2.0)' + '\n'
                        +   'or the VERSION_NAME property (which is the fallback)')
    }

    @TaskAction
    def setup() {
        def versionPropsFile = project.file("${project.getRootDir().getAbsolutePath()}/gradle.properties")
        if (versionPropsFile.canRead()) {
            def Properties versionProps = new Properties()
            versionProps.load(new FileInputStream(versionPropsFile))

            // use the specific version name, if provided or use the VERSION_NAME, typically stored in the file gradle.properties
            String releaseVersionName = project.hasProperty('appVersionName') ? project.property('appVersionName') : versionProps['VERSION_NAME'].toString()
            VersionParts parts = BBAppBuildPlugin.getVersionParts(releaseVersionName)
            def newVersionName
            def newVersionCode

            if(project.hasProperty('appVersionName')) {
                newVersionName = parts.buildVersionName(true)

                // a couple of projects use maven style versioning - 'build' value is ignored
                if(project.('bbapp').useMavenVersioning) {
                    newVersionCode = 1000000 * parts.major.toInteger()
                                      + 1000 * parts.minor.toInteger()
                                      + parts.revision.toInteger()
                } else {
                    newVersionCode = parts.calculateVersionCode()
                }
            } else {
                newVersionName = parts.buildVersionName(true)
                newVersionCode = versionProps['VERSION_CODE'].toInteger()
            }

            versionProps['VERSION_CODE'] = newVersionCode.toString()
            versionProps['VERSION_NAME'] = newVersionName.toString()

            // update the versions with the new version code and version name
            project.('android').applicationVariants.all { variant ->
                variant.mergedFlavor.versionName = newVersionName
                variant.mergedFlavor.versionCode = newVersionCode
            }
            versionProps.store(versionPropsFile.newWriter(), null)
        } else {
            throw new GradleException("Could not read version.properties!")
        }
    }

}
