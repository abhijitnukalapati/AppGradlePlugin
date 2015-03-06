package com.brandingbrand.gradle

import com.brandingbrand.gradle.tasks.PushToGithubTask
import com.brandingbrand.gradle.tasks.SetupReleaseVersionTask
import de.felixschulze.gradle.HockeyAppPluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension


class BBAppBuildPlugin implements Plugin<Project> {

    private static final String HOCKEYAPP_API_TOKEN = 'e5280b32cf24468e93db89f70d046bbb'
    private static final String HOCKEYAPP_NO_ISSUES_MESSAGE = 'No issues to report'
    private static final String RELEASE = "RELEASE"
    private static final String CHECKSTYLE_VERSION = "6.2"

    private String gitCommitBuildType = "qa release"
    private String buildType = "QA"

    void apply(Project project) {
        // add the 'bbapp' extension object
        project.extensions.create('bbapp', BBAppBuildPluginExtension)

        applyPlugins(project)

        configureCheckstyle(project)

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

            setVariablesBasedOnTaskGraph(project)
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
            project.getLogger().warn("File 'hockeyapp.issues' couldn't be read. No issues will be reported as resolved")
        }

        if(!issues.empty) {
            issues.each { issue ->
                sb.append(issue).append("\n")
            }
        } else {
            sb.append(HOCKEYAPP_NO_ISSUES_MESSAGE)
        }

        return sb.toString()
    }

    def applyPlugins(Project project) {
        project.configure(project) {
            // apply the android plugin
            plugins.apply('com.android.application')

            // apply the hockeyapp plugin
            plugins.apply('de.felixschulze.gradle.hockeyapp')

            // apply the checkstyle plugin
            plugins.apply('checkstyle')
        }
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
            if(project.('bbapp').getFrameworkVersion()?.trim()) {
                println project.('bbapp').getFrameworkVersion()
            } else {
                project.getLogger().warn('No framework version provided')
            }
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

    def configureCheckstyle(Project project) {
        // set defaults
        CheckstyleExtension checkstyle = project.('checkstyle')
        checkstyle.with {
            toolVersion = CHECKSTYLE_VERSION
        }

        // create checkstyle task
        Task checkstyleTask = project.task('checkstyle', type: Checkstyle) {
            description 'applies the checkstyle config to the java files'
            source 'src/main/java'
            include '**/*.java'
            exclude '**/gen/**'

            // empty classpath
            classpath = project.files()

            /**
             * Do not run checkstyle if a skipCheckstyle argument
             * is provided via the command line (Ex: -PskipCheckstyle).
             * This property should be ideally used only for
             * development purposes
             */
            onlyIf { !project.hasProperty('skipCheckstyle') }
        }

        // checkstyle runs on every build
        project.tasks.getByName('preBuild').dependsOn(checkstyleTask)
    }

    def configureHockeyAppPlugin(Project project) {
        HockeyAppPluginExtension hockeyApp = project.('hockeyapp')
        hockeyApp.with {
            apiToken = HOCKEYAPP_API_TOKEN
            notesType = 0 // textile formatting
            status = 2 // enable downloads
            notes = constructHockeyAppString(project)
        }
    }

    def setVariablesBasedOnTaskGraph(Project project){
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
