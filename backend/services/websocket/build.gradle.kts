plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.example.discord.runtime"

tasks.jar {
    enabled = false
}

dependencies {
    implementation(project(":backend:modules:event"))
    implementation(project(":backend:modules:gateway"))
    implementation(project(":backend:shared:common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
