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
include(":backend:modules:channel")
include(":backend:modules:guild")
include(":backend:modules:gateway")
include(":backend:modules:invite")
include(":backend:modules:message")
include(":backend:modules:permission")
include(":backend:modules:presence")
include(":backend:modules:social")
include(":backend:modules:user")
include(":backend:shared:common")

project(":backend:boot").projectDir = file("backend/boot")
project(":backend:modules:identity").projectDir = file("backend/modules/identity")
project(":backend:modules:channel").projectDir = file("backend/modules/channel")
project(":backend:modules:guild").projectDir = file("backend/modules/guild")
project(":backend:modules:gateway").projectDir = file("backend/modules/gateway")
project(":backend:modules:invite").projectDir = file("backend/modules/invite")
project(":backend:modules:message").projectDir = file("backend/modules/message")
project(":backend:modules:permission").projectDir = file("backend/modules/permission")
project(":backend:modules:presence").projectDir = file("backend/modules/presence")
project(":backend:modules:social").projectDir = file("backend/modules/social")
project(":backend:modules:user").projectDir = file("backend/modules/user")
project(":backend:shared:common").projectDir = file("backend/shared/common")
