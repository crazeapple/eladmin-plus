pluginManagement {
    repositories {
        // 阿里云插件仓镜像（代理 Gradle Plugin Portal）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // 阿里云公共仓镜像（代理 Maven Central）
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}

rootProject.name = "server"
