package com.brandingbrand.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test;

class BBAppBuildPluginTest {
    @Test
    public void buildPluginExecutesTask() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'bbapp'

        // NOTE: add test logic here
    }
}
