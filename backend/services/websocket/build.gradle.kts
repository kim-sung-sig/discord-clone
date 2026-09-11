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
    implementation(project(":backend:modules:event"))
    implementation(project(":backend:modules:gateway"))
    implementation(project(":backend:modules:permission"))
    implementation(project(":backend:shared:common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-jackson2")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
