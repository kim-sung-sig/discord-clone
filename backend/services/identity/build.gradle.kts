plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.example.discord.runtime"

tasks.jar {
    enabled = false
}

dependencies {
    implementation(project(":backend:modules:identity"))
    implementation(project(":backend:modules:social"))
    implementation(project(":backend:modules:user"))
    implementation(project(":backend:shared:common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
