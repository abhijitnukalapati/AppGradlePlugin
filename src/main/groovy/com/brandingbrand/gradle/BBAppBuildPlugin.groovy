package com.brandingbrand.gradle

import com.android.build.gradle.AppExtension
import com.brandingbrand.gradle.tasks.PushToGithubTask
import com.brandingbrand.gradle.tasks.SetupReleaseVersionTask
import de.felixschulze.gradle.HockeyAppPluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class BBAppBuildPlugin implements Plugin<Project> {

    private String hockeyApp_noIssuesMessage = 'No issues to report'
    private String gitCommitBuildType = "qa release"
    private String buildType = "QA"

    private final String HOCKEYAPP_API_TOKEN = 'e5280b32cf24468e93db89f70d046bbb'
    public final String RELEASE = "RELEASE"

    void apply(Project project) {

        project.configure(project) {
            // add the 'bbapp' extension object
            extensions.create('bbapp', BBAppBuildPluginExtension)

            // apply the android plugin
            plugins.apply('com.android.application')

            // apply the hockeyapp plugin
            plugins.apply('de.felixschulze.gradle.hockeyapp')
        }

        addPushVersionToGithubTask(project)

        addBumpVersionCodeTask(project)

        // we can access the plugin data/configuration
        // only after the project has been evaluated
        project.afterEvaluate {

            configureTaskDependencies(project)

            addPrintFrameworkVersionTask(project)

            addSetHockeyAppVersionTask(project)

            project.task('setupReleaseVersion', type: SetupReleaseVersionTask)

            configureHockeyAppPlugin(project)

            configureAndroidPlugin(project)

            project.gradle.taskGraph.whenReady { taskGraph ->
                if (taskGraph.hasTask(project.tasks.findByName('assembleRelease'))) {
                    gitCommitBuildType = "release"
                    buildType = RELEASE
                } else {
                    gitCommitBuildType = "qa release"
                    buildType = "QA"
                }
            }
        }
    }

    /**
     * Reads the file named 'hockeyapp.issues',  parses the issue and the
     * issue description using the delimiter ':' and constructs/formats a string with
     * a JIRA link for the issue.
     *
     * For example, a line in the file would be as follows:
     *
     * VITAANDJS-531:Resolved issue with bar code scan icon
     */
    String constructHockeyAppString(Project project) {
        def issuesFile = project.file("${project.getRootDir().getAbsolutePath()}/hockeyapp.issues")
        StringBuilder sb = new StringBuilder()
        sb.append("\n\n")
        sb.append(project.('bbapp').getHockeyAppDescription())
        sb.append("\n\n\n")
        sb.append("_Resolved Issues_").append("\n")

        def issues = []
        if(issuesFile.canRead()) {
            issuesFile.eachLine { line ->
                List<String> parts = line.tokenize(':')
                issues << ("\"" + parts.get(0) + "\":https://jira.brandingbrand.com/browse/" + parts.get(0) + " - " + parts.get(1))
            }
        } else {
            project.getLogger().warn("Could not read file - hockeyapp.issues")
        }

        if(!issues.empty) {
            issues.each { issue ->
                sb.append(issue).append("\n")
            }
        } else {
            sb.append(hockeyApp_noIssuesMessage)
        }

        return sb.toString()
    }

    /**
     * Adds a task that commits and pushes changes to the
     * gradle.properties file to remote Git repo (origin)
     * @param project
     */
    def addPushVersionToGithubTask(Project project) {
        project.task('pushVersionToGithub', type: PushToGithubTask) {
            doFirst {
                commitMessage = "NO-TICKET incrementing version for ${gitCommitBuildType}"
                filePatterns = ['gradle.properties']
                isRelease = gitCommitBuildType.equalsIgnoreCase(RELEASE)
            }

            /**
             * Ensure this task runs only after we make changes to the gradle.properties
             * file. This is strictly limited to ordering and doesn't indicate that the
             * tasks will be executed
             */
            mustRunAfter('bumpVersionCode', 'setupReleaseVersion')
        }
    }

    /**
     * Adds a task the increments and stores version code - VERSION_CODE
     * @param project
     */
    def addBumpVersionCodeTask(Project project){
        project.task('bumpVersionCode') << {
            description = 'increments and stores version code - VERSION_CODE'
            def versionPropsFile = project.file("${project.getRootDir().getAbsolutePath()}/gradle.properties")
            if (versionPropsFile.canRead()) {
                def Properties versionProps = new Properties()
                versionProps.load(new FileInputStream(versionPropsFile))

                def newVersionCode = versionProps['VERSION_CODE_QA'].toInteger() + 1
                versionProps['VERSION_CODE_QA'] = newVersionCode.toString()

                // update all versions, except for the release type, with the new version code
                project.('android').applicationVariants.all { variant ->
                    variant.mergedFlavor.versionCode = newVersionCode
                }
                versionProps.store(versionPropsFile.newWriter(), null)
            } else {
                throw new GradleException("Could not read version.properties!")
            }
        }
    }

    /**
     * Configures tasks by adding the appropriate dependencies
     * @param project
     */
    def configureTaskDependencies(Project project) {
        project.('android').applicationVariants.all { variant ->
            if (variant.buildType.name == 'release') {
                variant.mergedFlavor.versionCode = readVersionCode(project, "RELEASE")

                // modify the version name/code prior to processing the manifest
                project.tasks.getByName("process${variant.name.capitalize()}Manifest").dependsOn('setupReleaseVersion')
            } else {
                variant.mergedFlavor.versionCode = readVersionCode(project, "QA")
            }

            // precede every hockeyapp deployment with a push to github to ensure all version
            // changes are synced up with remote
            project.tasks.getByName("upload${variant.name.capitalize()}ToHockeyApp").dependsOn('pushVersionToGithub', 'setHockeyAppVersion')
        }
    }

    /**
     * Adds a task to print the framework version used by the client
     * @param project
     */
    def addPrintFrameworkVersionTask(Project project) {
        project.task('printFrameworkVersion') {
            description = 'prints the framework dependency version'
            println project.('bbapp').getFrameworkVersion()
        }
    }

    /**
     * Adds a task that sets the version and version code for hockeyapp
     * builds
     * @param project
     */
    def addSetHockeyAppVersionTask(Project project) {
        project.task('setHockeyAppVersion') << {
            description = 'sets the version and version code for hockeyapp builds'

            def versionString = "h4. v${readVersionName(project)}(${readVersionCode(project, buildType)})"
            project.('hockeyapp').notes = versionString << project.('hockeyapp').notes
        }
    }

    def configureHockeyAppPlugin(Project project) {
        HockeyAppPluginExtension hockeyApp = project.('hockeyapp')
        hockeyApp.apiToken = HOCKEYAPP_API_TOKEN
        hockeyApp.notesType = 0 // textile formatting
        hockeyApp.status = 2 // enable downloads
        hockeyApp.notes = constructHockeyAppString(project)
    }

    def configureAndroidPlugin(Project project) {
        AppExtension androidExtension = project.('android')
        androidExtension.defaultConfig.setVersionCode(readVersionCode(project))
        androidExtension.defaultConfig.setVersionName(readVersionName(project))
    }


    /**
     * Reads the version code depending on the buildtype specified.
     * all buildtypes which are not "RELEASE" (case-insensitive) are
     * considered to be "QA"  (case-insensitive)
     */
    static readVersionCode(Project project, buildtype = "QA") {
        def versionPropsFile = project.file("${project.getRootDir().getAbsolutePath()}/gradle.properties")
        if (versionPropsFile.canRead()) {
            def Properties versionProps = new Properties()
            versionProps.load(new FileInputStream(versionPropsFile))
            if(buildtype.equalsIgnoreCase("QA")) {
                return versionProps['VERSION_CODE_QA'].toInteger()
            } else {
                return versionProps['VERSION_CODE_RELEASE'].toInteger()
            }
        } else {
            throw new GradleException("Could not read version.properties!")
        }
    }

    /**
     * Reads the version name from gradle.properties file
     */
    static readVersionName(Project project) {
        def versionPropsFile = project.file("${project.getRootDir().getAbsolutePath()}/gradle.properties")
        if (versionPropsFile.canRead()) {
            def Properties versionProps = new Properties()
            versionProps.load(new FileInputStream(versionPropsFile))
            return versionProps['VERSION_NAME'].toString()
        } else {
            throw new GradleException("Could not read file - version.properties!")
        }
    }

}
