pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "discord-clone"

include(":backend:boot")
include(":backend:modules:identity")
include(":backend:modules:permission")
include(":backend:modules:user")
include(":backend:shared:common")

project(":backend:boot").projectDir = file("backend/boot")
project(":backend:modules:identity").projectDir = file("backend/modules/identity")
project(":backend:modules:permission").projectDir = file("backend/modules/permission")
project(":backend:modules:user").projectDir = file("backend/modules/user")
project(":backend:shared:common").projectDir = file("backend/shared/common")
