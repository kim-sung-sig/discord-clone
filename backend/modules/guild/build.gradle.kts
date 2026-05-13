plugins {
    `java-library`
}

dependencies {
    api(project(":backend:modules:channel"))
    api(project(":backend:modules:permission"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
}
