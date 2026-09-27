plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "xyz.lukix"
version = "0.0.1-SNAPSHOT"
description = "KeyGo"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Redis：热点只读查询缓存（统计/公告/单位），缓存放行失败时自动回源 PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")

    // 统一测试套件，替代一堆零散test starter
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // MockMvc / @AutoConfigureMockMvc（Boot 4 起位于 webmvc 专属测试 starter）
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.bootJar {
    archiveBaseName.set("KeyGo")
    archiveVersion.set("")
}

tasks.jar {
    enabled = false
}

// 编译统一UTF-8编码，避免Windows中文乱码
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing"))
}