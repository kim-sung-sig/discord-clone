plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.example.discord.runtime"

tasks.jar {
    enabled = false
}

// Boot 4.1.1 manages testcontainers.version=2.0.5 for the core artifact only, which would mix a 2.x core with the
// 1.x modules declared below. Direct dependency management outranks the imported BOM, so pin the core to the 1.x line.
dependencyManagement {
    dependencies {
        dependency("org.testcontainers:testcontainers:1.21.4")
    }
}

dependencies {
    implementation(project(":backend:modules:identity"))
    implementation(project(":backend:modules:social"))
    implementation(project(":backend:modules:user"))
    implementation(project(":backend:shared:common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.security:spring-security-webauthn")
    implementation("org.springframework:spring-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("com.h2database:h2")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}
