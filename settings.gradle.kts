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
include(":backend:modules:expression")
include(":backend:modules:gateway")
include(":backend:modules:invite")
include(":backend:modules:message")
include(":backend:modules:moderation")
include(":backend:modules:permission")
include(":backend:modules:presence")
include(":backend:modules:social")
include(":backend:modules:storage")
include(":backend:modules:thread")
include(":backend:modules:user")
include(":backend:modules:voice")
include(":backend:shared:common")

project(":backend:boot").projectDir = file("backend/boot")
project(":backend:modules:identity").projectDir = file("backend/modules/identity")
project(":backend:modules:channel").projectDir = file("backend/modules/channel")
project(":backend:modules:guild").projectDir = file("backend/modules/guild")
project(":backend:modules:expression").projectDir = file("backend/modules/expression")
project(":backend:modules:gateway").projectDir = file("backend/modules/gateway")
project(":backend:modules:invite").projectDir = file("backend/modules/invite")
project(":backend:modules:message").projectDir = file("backend/modules/message")
project(":backend:modules:moderation").projectDir = file("backend/modules/moderation")
project(":backend:modules:permission").projectDir = file("backend/modules/permission")
project(":backend:modules:presence").projectDir = file("backend/modules/presence")
project(":backend:modules:social").projectDir = file("backend/modules/social")
project(":backend:modules:storage").projectDir = file("backend/modules/storage")
project(":backend:modules:thread").projectDir = file("backend/modules/thread")
project(":backend:modules:user").projectDir = file("backend/modules/user")
project(":backend:modules:voice").projectDir = file("backend/modules/voice")
project(":backend:shared:common").projectDir = file("backend/shared/common")
