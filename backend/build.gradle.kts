plugins {
    java
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.domaframework.doma.compile") version "4.0.0"
    jacoco
}

group = "com.capgemini.estimate.poc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.seasar.doma.boot:doma-spring-boot-starter:1.6.0")
    implementation("org.seasar.doma:doma-core:3.8.0")
    annotationProcessor("org.seasar.doma:doma-processor:3.8.0")
    runtimeOnly("com.oracle.database.jdbc:ojdbc11:21.11.0.0") 
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation ("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly ("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly ("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("com.nimbusds:nimbus-jose-jwt:9.37")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // AWS S3
    implementation(platform("software.amazon.awssdk:bom:2.25.55"))
    implementation("software.amazon.awssdk:s3")
}

tasks.withType<Test> { 
    useJUnitPlatform()
}

// JaCoCo (test coverage)
jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Optionally fail build on low coverage: uncomment and adjust thresholds
// tasks.jacocoTestCoverageVerification {
//     violationRules {
//         rule {
//             limit { minimum = "0.50".toBigDecimal() } // 50% line coverage
//         }
//     }
// }
// tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

