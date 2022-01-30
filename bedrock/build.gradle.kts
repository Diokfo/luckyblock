import java.io.File

plugins {
    kotlin("js")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation(project(":common"))
}

buildscript {
    dependencies {
        classpath(files("$rootDir/tools/build/classes"))
    }
}

kotlin {
    js {
        browser {
            dceTask {
                keep("luckyblock-bedrock.mod.lucky.bedrock.initServer")
            }
            webpackTask {
                outputFileName = "compiledServerScript.js"
                devtool = "hidden-source-map"
            }
        }
    }
}

val addonDistDir = "$rootDir/bedrock/build/processedResources/js/main/addon"
val bedrockTemplateAddonBlockId: String by project
val nodeVersion: String by project

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeVersion = nodeVersion
}

tasks.named<ProcessResources>("processResources") {
    from("../common/src/jvmMain/resources/game/assets/lucky/textures/blocks") {
        into("addon/resource_pack/textures/blocks")
    }
    from("../common/src/jvmMain/resources/game/assets/lucky/textures/blocks") {
        into("addon/resource_pack")
        include("lucky_block.png")
        rename("lucky_block.png", "pack_icon.png")
    }
    from("../common/src/jvmMain/resources/game/assets/lucky/textures/blocks") {
        into("addon/behavior_pack")
        include("lucky_block.png")
        rename("lucky_block.png", "pack_icon.png")
    }
}

tasks.register<Delete>("clearStructures") {
    delete(File("$addonDistDir/behavior_pack/structures/lucky").walkTopDown().toList())
}

tasks.register<JavaExec>("generateBedrockDrops") {
    classpath = fileTree("$rootDir/tools/build/install/tools/lib")
    mainClass.set("mod.lucky.tools.MainKt")
    args = listOf(
        "generate-bedrock-drops",
        "--inputConfigFolder",
        "$rootDir/bedrock/src/main/resources/lucky-config",
        "--inputJsTemplateFile",
        "$rootDir/bedrock/src/main/resources/serverScript.template.js",
        "--blockId",
        "lucky_block",
        "--outputJsFile",
        "$rootDir/bedrock/build/processedResources/serverScript.js",
        "--outputStructuresFolder",
        "$addonDistDir/behavior_pack/structures/lucky",
    )

    dependsOn(project(":tools").tasks.getByName("installDist"))
}

tasks.register<JavaExec>("nbtToMcstructure") {
    classpath = fileTree("$rootDir/tools/build/install/tools/lib")
    mainClass.set("mod.lucky.tools.MainKt")
    args = listOf(
        "nbt-to-mcstructure",
        "$rootDir/common/src/jvmMain/resources/lucky-config/structures",
        "--outputStructuresFolder",
        "$addonDistDir/behavior_pack/structures/lucky",
        "--blockConversionFile",
        "$rootDir/tools/block_conversion.yaml",
        "--outputGeneratedBlockConversionFile",
        "$rootDir/tools/.debug/block_conversion.generated.yaml",
    )
    dependsOn(project(":tools").tasks.getByName("installDist"))
}

tasks.register<JavaExec>("buildTemplateAddon") {
    doFirst {
        delete("./build/template-addon")
    }
    classpath = fileTree("$rootDir/tools/build/install/tools/lib")
    mainClass.set("mod.lucky.tools.MainKt")
    args = listOf(
        "generate-bedrock-config",
        "--inputConfigFolder",
        "./template-addon",
        "--inputTemplateAddonFolder",
        "./template-addon",
        "--outputAddonFolder",
        "./build/template-addon",
    )
    dependsOn(project(":tools").tasks.getByName("installDist"))
}

tasks.register<JavaExec>("buildLuckyConfig") {
    doFirst {
        delete("./build/lucky-config")
    }
    classpath = fileTree("$rootDir/tools/build/install/tools/lib")
    mainClass.set("mod.lucky.tools.MainKt")
    args = listOf(
        "generate-bedrock-config",
        "--inputConfigFolder",
        "./lucky-config",
        "--inputTemplateAddonFolder",
        "./lucky-config",
        "--outputAddonFolder",
        "./build/lucky-config",
    )
    dependsOn(project(":tools").tasks.getByName("installDist"))
}

tasks.register<Sync>("copyRuntimePacks") {
    val addonPaths = mapOf(
        "LuckyBlock" to "./build/processedResources/js/main/addon",
        "TemplateAddon" to "./build/template-addon",
    )

    for ((addonName, addonPath) in addonPaths) {
        from("$addonPath/resource_pack") {
            into("development_resource_packs/${addonName}RP")
        }
        from("$addonPath/behavior_pack") {
            into("development_behavior_packs/${addonName}BP")
        }
    }

    into("./run")
    dependsOn("copyCompiledServerScript")
    dependsOn("clearStructures")
    dependsOn("generateBedrockDrops")
    dependsOn("nbtToMcstructure")

    // workaround: gradle doesn't always detect that the input folder has changed
    outputs.upToDateWhen { false }
}

tasks.register<Copy>("copyCompiledServerScript") {
    from("./build/distributions/compiledServerScript.js") {
        rename("compiledServerScript.js", "serverScript.js")
    }
    into("$addonDistDir/behavior_pack/scripts/server")

    dependsOn("generateBedrockDrops")
}

tasks.register<Zip>("zipPack") {
    archiveFileName.set("pack.zip")
    destinationDirectory.set(file("./dist"))
    from("./build/main/resources/pack")

    dependsOn("processResources")
}


tasks.named("build").configure {
    tasks.getByName("browserProductionWebpack").dependsOn("generateBedrockDrops")
    tasks.getByName("browserProductionWebpack").inputs.file("./build/processedResources/serverScript.js")
    tasks.getByName("copyCompiledServerScript").dependsOn("browserProductionWebpack")
    tasks.getByName("dist").dependsOn("browserProductionWebpack")
    dependsOn("browserProductionWebpack")
    dependsOn("copyCompiledServerScript")
    dependsOn("dist")
    dependsOn("copyRuntimePacks")
}

tasks.register("buildDev").configure {
    tasks.getByName("browserDevelopmentWebpack").dependsOn("generateBedrockDrops")
    tasks.getByName("browserDevelopmentWebpack").inputs.file("./build/processedResources/generated-config.js")
    tasks.getByName("copyCompiledServerScript").dependsOn("browserDevelopmentWebpack")
    dependsOn("browserDevelopmentWebpack")
    dependsOn("copyCompiledServerScript")
    dependsOn("copyRuntimePacks")
}
