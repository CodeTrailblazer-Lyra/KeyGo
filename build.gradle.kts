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

// ---------------------------------------------------------------------------
// 覆盖 Spring Boot BOM 管理的依赖版本（修复已知 CVE 安全漏洞）
// ---------------------------------------------------------------------------
// Tomcat      CVE-2026-101296 (JNDI Realm 认证绕过)
ext["tomcat.version"] = "11.0.27"
// Logback     CVE-2026-19880 (路径穿越) / CVE-2026-13006
ext["logback.version"] = "1.6.3"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Redis：热点只读查询缓存（统计/公告/单位），缓存放行失败时自动回源 PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    // --- 显式声明关键传递依赖，确保 Dependabot 安全更新能正确识别 ---
    // Jackson 2.x 核心（jackson-databind 的传递依赖）CVE-2026-110879 / CVE-2026-91777
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Tomcat Embed Core（spring-boot-starter-webmvc 的传递依赖） CVE-2026-101296
    implementation("org.apache.tomcat.embed:tomcat-embed-core")
    // Commons Lang 3（poi-ooxml 的传递依赖） CVE-2025-xxxx（通过 eachDependency 统一管理版本）
    implementation("org.apache.commons:commons-lang3")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql:42.7.14")  // CVE-2026-107314 / CVE-2026-107315

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
configurations.all {
    resolutionStrategy {
        // ------------------------------------------------------------------
        // 覆盖 Spring Boot BOM（含 jackson-bom）管理的依赖版本，修复已知 CVE
        // ------------------------------------------------------------------
        // 使用 eachDependency 拦截所有版本选择请求，优先级高于 BOM constraints
        //
        // Jackson 2.x  CVE-2026-110879 (RCE 泛型参数验证绕过) / CVE-2026-91777 (DoS)
        // 注意：jackson-annotations 从 2.20 起不再发布 patch 版本，仅有 minor 版本（2.21/2.22）
        val jackson2Version = "2.22.4"
        val jacksonAnnotationsVersion = "2.22"
        // Jackson 3.x  CVE-2026-110879 (RCE 泛型参数验证绕过) / CVE-2026-91777 (DoS)
        val jackson3Version = "3.2.4"

        eachDependency {
            val group = requested.group
            val name = requested.name
            when {
                // jackson-annotations 使用独立的 minor 版本（无 patch 版本）
                // Spring Boot 4.1.1 的 jackson-2-bom:2.21.5 将 annotations 约束在 2.21，
                // 但 jackson-databind:2.22.4 需要 annotations:2.22 中的 JsonApplyView 类
                group == "com.fasterxml.jackson.core" && name == "jackson-annotations" -> useVersion(jacksonAnnotationsVersion)
                group == "com.fasterxml.jackson.core" -> useVersion(jackson2Version)
                group == "tools.jackson.core" -> useVersion(jackson3Version)
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing"))
}