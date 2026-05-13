plugins {
    `java-library`
}

dependencies {
    implementation("org.springframework.security:spring-security-crypto:6.4.2")
    runtimeOnly("commons-logging:commons-logging:1.3.4")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
}
