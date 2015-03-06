package com.brandingbrand.gradle.tasks;

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction;

/**
 * Created by abhijitnukalapati on 2/24/15.
 */
public class SetupReleaseVersionTask extends DefaultTask {

    SetupReleaseVersionTask() {
        setDescription('Sets the release version code based on the supplied release version name')

        /**
         * Run this task only if a releaseVersionName argument
         * is provided via the command line (Ex: -PreleaseVersionName=2.2.0)
         */
        onlyIf { project.hasProperty('releaseVersionName') }
    }

    @TaskAction
    def setup() {

        def major
        def minor
        def revision = 0

        // extract the version numbers from the version name string
        String releaseVersionName = project.property('releaseVersionName')
        List<String> versions = releaseVersionName.tokenize('\\.')
        if (versions.size() == 2) {
            major = versions.get(0)
            minor = versions.get(1)
        } else if (versions.size() == 3) {
            major = versions.get(0)
            minor = versions.get(1)
            revision = versions.get(2)
        } else {
            throw new GradleException('Version number should be either in the form of x.x or x.x.x')
        }

        def versionPropsFile = project.file("${project.getRootDir().getAbsolutePath()}/gradle.properties")
        if (versionPropsFile.canRead()) {
            def Properties versionProps = new Properties()
            versionProps.load(new FileInputStream(versionPropsFile))

            def newVersionName = project.property('releaseVersionName')

            // a couple of projects use maven style versioning
            def newVersionCode
            if(project.('bbapp').useMavenVersioning) {
                newVersionCode = 1000000 * major.toInteger() + 1000 * minor.toInteger() + revision.toInteger()
            } else {
                newVersionCode = 1000 * major.toInteger() + 100 * minor.toInteger() + revision.toInteger()
            }
            versionProps['VERSION_CODE_RELEASE'] = newVersionCode.toString()
            versionProps['VERSION_NAME'] = newVersionName.toString()

            // update the release version with the new version code
            project.('android').applicationVariants.all { variant ->
                if (variant.buildType.name == 'release') {
                    variant.mergedFlavor.versionName = newVersionName
                    variant.mergedFlavor.versionCode = newVersionCode
                }
            }
            versionProps.store(versionPropsFile.newWriter(), null)
        } else {
            throw new GradleException("Could not read version.properties!")
        }
    }

}
