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
    implementation(project(":backend:modules:thread"))
    implementation(project(":backend:shared:common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
