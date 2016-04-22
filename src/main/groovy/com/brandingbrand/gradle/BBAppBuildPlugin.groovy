package com.brandingbrand.gradle

import com.brandingbrand.gradle.tasks.PrepReleaseVersionTask

import de.felixschulze.gradle.HockeyAppPluginExtension

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

class BBAppBuildPlugin implements Plugin<Project> {

    private static final String HOCKEYAPP_API_TOKEN = 'c8f51a0118254670b8b209a507484f12'
    private static final String RELEASE = "RELEASE"
    private static final String CHECKSTYLE_VERSION = "6.2"

    void apply(Project project) {
        // add the 'bbapp' extension object
        project.extensions.create('bbapp', BBAppBuildPluginExtension)

        applyPlugins(project)

        configureCheckstyle(project)

        addBumpBuildVersionTask(project)

        // we can access the plugin data/configuration
        // only after the project has been evaluated
        project.afterEvaluate {

            configureTaskDependencies(project)

            addPrintFrameworkVersionTask(project)

            addSetHockeyAppVersionTask(project)

            project.task('prepareReleaseVersion', type: PrepReleaseVersionTask)

            configureHockeyAppPlugin(project)
        }
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
              throw new GradleException("Could not read gradle.properties!")
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
                project.tasks.getByName("process${variant.name.capitalize()}Manifest").dependsOn('prepareReleaseVersion')
            } else {
                variant.mergedFlavor.versionCode = readVersionCode(project)
            }

            // precede every hockeyapp deployment with setting the version in the release notes
            Task hockeyAppTask = project.tasks.getByName("upload${variant.name.capitalize()}ToHockeyApp")
            hockeyAppTask.dependsOn('setHockeyAppVersion')
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
                project.getLogger().warn("No framework version provided - most likely because this isn't a framework app")
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

            StringBuilder sb = new StringBuilder()
            sb.append("h4. v${readVersionName(project)}\n\n")
            sb.append("Please see JIRA")

            project.('hockeyapp').notes = sb.toString()
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
            notes = '' // set in addSetHockeyAppVersionTask()
        }
    }

    /**
     * Reads the version code from gradle.properties file
     */
    static readVersionCode(Project project) {
        def versionPropsFile = project.file("${project.getRootDir().getAbsolutePath()}/gradle.properties")
        if (versionPropsFile.canRead()) {
            def Properties versionProps = new Properties()
            versionProps.load(new FileInputStream(versionPropsFile))
            return versionProps['VERSION_CODE'].toInteger()
        } else {
            throw new GradleException("Could not read gradle.properties!")
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
            throw new GradleException("Could not read file - gradle.properties!")
        }
    }

    /**
     * Retrieves the different version bits from the given
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
}
