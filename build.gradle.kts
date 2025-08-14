import com.google.protobuf.gradle.id

plugins {
  id("com.google.protobuf") version "0.9.5"
  id("build.buf") version "0.10.2"
  id("com.diffplug.spotless") version "6.25.0"
  application

  kotlin("jvm") version "2.0.20"
}

repositories {
  mavenLocal()
  mavenCentral()

  maven("https://jitpack.io")
  maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")
}

dependencies {
  api("org.jetbrains.kotlin:kotlin-stdlib:2.0.20")

  implementation("org.jetbrains.kotlinx:kandy-lets-plot:0.7.0")
  implementation("org.jetbrains.kotlinx:kotlin-statistics-jvm:0.3.0")

  implementation("com.github.nwillc.ksvg:ksvg:master-SNAPSHOT")

  implementation("com.google.protobuf:protobuf-kotlin:4.31.1")
  implementation("com.google.protobuf:protobuf-java:4.31.1")
  implementation("com.google.protobuf:protobuf-java-util:4.31.1")
}

group = "com.hopskipnfall"

description = "Kaillera-Lag-Simulator"

version = "0.12.0"

kotlin { jvmToolchain(17) }

tasks.processResources {
  // Fails to compile without this.
  // https://github.com/google/protobuf-gradle-plugin/issues/522#issuecomment-1195266995
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

sourceSets {
  main {
    proto.srcDir("src/main/proto")
    kotlin.srcDir("src/main/java")
  }

  test {
    proto.srcDir("src/main/proto")
    kotlin.srcDir("src/test/java")
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
  useJUnit()

  systemProperty(
    "flogger.backend_factory",
    "org.emulinker.testing.TestLoggingBackendFactory#getInstance"
  )
}

// Disable formatting via buf plugin directly. We just need it for the binary.
buf { enforceFormat = false }

tasks.named("bufLint") { enabled = false }

// Formatting/linting.
spotless {
  // Breaks github CI even though it works locally..
  //  protobuf {
  //    buf("1.46.0")
  //      .pathToExe(
  //
  // configurations.getByName(BUF_BINARY_CONFIGURATION_NAME).getSingleFile().getAbsolutePath()
  //      )
  //    target("src/**/*.proto")
  //  }

  kotlin {
    target("**/*.kt", "**/*.kts")
    targetExclude("build/", ".git/", ".idea/", ".mvn", "src/main/java-templates/")
    ktfmt().googleStyle()
  }

  yaml {
    target("**/*.yml", "**/*.yaml")
    targetExclude("build/", ".git/", ".idea/", ".mvn")
    jackson()
  }
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.31.1" }

  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins {
        // Generates Kotlin DSL builders.
        id("kotlin") {}
      }
    }
  }
}

tasks.named("compileKotlin") { dependsOn(":generateProto") }

application { mainClass.set("com.hopskipnfall.MainKt") }
