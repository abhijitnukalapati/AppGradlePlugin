package com.brandingbrand.gradle

import com.brandingbrand.gradle.tasks.PushToGithubTask
import com.brandingbrand.gradle.tasks.SetupVersionTask
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

    void apply(Project project) {
        // add the 'bbapp' extension object
        project.extensions.create('bbapp', BBAppBuildPluginExtension)

        applyPlugins(project)

        configureCheckstyle(project)

        addPushVersionToGithubTask(project)

        addBumpBuildVersionTask(project)

        // we can access the plugin data/configuration
        // only after the project has been evaluated
        project.afterEvaluate {

            configureTaskDependencies(project)

            addPrintFrameworkVersionTask(project)

            addSetHockeyAppVersionTask(project)

            project.task('setupVersion', type: SetupVersionTask)

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
            mustRunAfter('bumpBuildVersion', 'setupVersion')
        }
    }


    /**
     * Adds a task the increments and stores version name and version code
     * @param project
     */
    def addBumpBuildVersionTask(Project project) {
      project.task('bumpBuildVersion') << {
          description = 'increments and stores version code - VERSION_CODE'
          def versionPropsFile = project.file("${project.getRootDir().getAbsolutePath()}/gradle.properties")
          if (versionPropsFile.canRead()) {
              def Properties versionProps = new Properties()
              versionProps.load(new FileInputStream(versionPropsFile))

              String oldVersionName = versionProps['VERSION_NAME'].toString()
              VersionParts parts = getVersionParts(oldVersionName)
              parts.incrementBuild()

              String newVersionName = parts.buildVersionName()
              def newVersionCode = parts.calculateVersionCode()

              versionProps['VERSION_CODE'] = newVersionCode.toString()
              versionProps['VERSION_NAME'] = newVersionName

              // update all versions with the new version code
              project.('android').applicationVariants.all { variant ->
                  variant.mergedFlavor.versionCode = newVersionCode
                  variant.mergedFlavor.versionName = newVersionName
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
                variant.mergedFlavor.versionCode = readVersionCode(project)

                // modify the version name/code prior to processing the manifest
                project.tasks.getByName("process${variant.name.capitalize()}Manifest").dependsOn('setupVersion')
            } else {
                variant.mergedFlavor.versionCode = readVersionCode(project)
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

            def versionString = "h4. v${readVersionName(project)}(${readVersionCode(project)})"
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

    /**
     * Configures the Hockey App Plugin by setting default
     * values for the extension object
     * @param project
     * @return
     */
    def configureHockeyAppPlugin(Project project) {
        HockeyAppPluginExtension hockeyApp = project.('hockeyapp')
        hockeyApp.with {
            apiToken = HOCKEYAPP_API_TOKEN
            notesType = 0 // textile formatting
            status = 2 // enable downloads
            notes = constructHockeyAppString(project)
        }
    }

    /**
     * Sets the variables {@link #gitCommitBuildType} and {@link #buildType}
     * depending on the current build type
     * @param project
     */
    def setVariablesBasedOnTaskGraph(Project project){
        project.gradle.taskGraph.whenReady { taskGraph ->
            if (taskGraph.hasTask(project.tasks.findByName('assembleRelease'))) {
                gitCommitBuildType = "release"
            } else {
                gitCommitBuildType = "qa release"
            }
        }
    }

    /**
     * Reads the version code depending on the buildtype specified.
     * all buildtypes which are not "RELEASE" (case-insensitive) are
     * considered to be "QA"  (case-insensitive)
     */
    static readVersionCode(Project project) {
        def versionPropsFile = project.file("${project.getRootDir().getAbsolutePath()}/gradle.properties")
        if (versionPropsFile.canRead()) {
            def Properties versionProps = new Properties()
            versionProps.load(new FileInputStream(versionPropsFile))
            return versionProps['VERSION_CODE'].toInteger()
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

    /**
     * Retreives the different version bits from the given
     * versionName. The versionName should be in the form of
     * x.x (or) x.x.x (or) x.x.x.x
     */
    static VersionParts getVersionParts(String versionName) {
        List<String> versions = versionName.tokenize('\\.')

        VersionParts parts = new VersionParts();
        if (versions.size() == 2) {
            parts.major = versions.get(0).toInteger()
            parts.minor = versions.get(1).toInteger()
        } else if (versions.size() == 3) {
            parts.major = versions.get(0).toInteger()
            parts.minor = versions.get(1).toInteger()
            parts.revision = versions.get(2).toInteger()
        } else if (versions.size() == 4) {
            parts.major = versions.get(0).toInteger()
            parts.minor = versions.get(1).toInteger()
            parts.revision = versions.get(2).toInteger()
            parts.build = versions.get(3).toInteger()
        } else {
            throw new GradleException('Version number should be in the form of x.x (or) x.x.x (or) x.x.x.x')
        }

        return parts;
    }

    /**
     * A class that holds the parts of a version name
     * For example, the version name 3.2.4.1, the various
     * parts are 3 (major), 2 (minor), 4(revision), 1 (build)
     *
     */
    public static class VersionParts {
        int major;
        int minor;
        int revision;
        int build; // qa build increment

        /**
         * Increments the build number
         */
        def incrementBuild() {
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

}
