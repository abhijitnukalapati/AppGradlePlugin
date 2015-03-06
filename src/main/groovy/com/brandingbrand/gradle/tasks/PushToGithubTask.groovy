package com.brandingbrand.gradle.tasks

import com.brandingbrand.gradle.BBAppBuildPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.ajoberstar.grgit.*

class PushToGithubTask extends DefaultTask {

    Set<String> filePatterns = []
    String commitMessage
    boolean isRelease = false

    PushToGithubTask() {
         setDescription('Commits and pushes changes to the specified files to remote Git repo (origin)')

        /**
         * Do not run this task if a skipPushToGithub argument
         * is provided via the command line (Ex: -PskipPushToGithub)
         */
        onlyIf { !project.hasProperty('skipPushToGithub')}
    }

    @TaskAction
    def push() {
        def grgit = Grgit.open(project.getRootDir().getAbsolutePath());

        boolean noFilePatterns = filePatterns?.empty
        if(noFilePatterns) {
            logger.warn(this.name + " has no file patterns defined")
        }

        grgit.add(patterns: filePatterns)

        boolean hasMessage = commitMessage?.trim()
        if(hasMessage) {
            grgit.commit(message: commitMessage)
        } else {
            logger.error(this.name + " requires a message")
            return
        }

        grgit.push() // remote - origin

        if(isRelease) {
            grgit.push(remote: 'upstream')

            // create and push a release tag to upstream
            def tagName = "v${BBAppBuildPlugin.readVersionName(project)}(${BBAppBuildPlugin.readVersionCode(project)})"
            grgit.tag.add(name: tagName)
            grgit.push(remote: 'upstream', refsOrSpecs: ["refs/tags/${tagName}:refs/tags/${tagName}"])
        }

    }
}
