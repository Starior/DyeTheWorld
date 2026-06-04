import groovy.json.JsonOutput
import groovy.json.JsonSlurper

val mod_id: String by extra
val mc_version: String by extra

data class ArtsAndCraftsLangBlockType(
    val baseName: String,
    val cubeName: String,
    val hasStairs: Boolean = true,
    val hasSlab: Boolean = true,
    val hasWall: Boolean = true,
)

fun artsAndCraftsTitleCase(name: String): String =
    name
        .split("_")
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

plugins {
    id("com.possible-triangle.neoforge")
    idea
}

withKotlin()

neoforge {
    dataGen {
        existing("dye_depot")
        existing("another_furniture")
        existing("supplementaries")
        existing("create")
        existing("comforts")
        existing("quark")
        existing("suppsquared")
        existing("farmersdelight")
        existing("domesticationinnovation")
        existing("createdeco")
        existing("railways")
        existing("chalk")
        existing("upgrade_aquatic")
        existing("waystones")
        existing("moreconcrete")
        existing("interiors")
        existing("snowyspirit")
        existing("connectedglass")
        existing("botanypots")
        existing("simulated")
        existing("aeronautics")
        existing("arts_and_crafts")
    }
}

neoForge.runs.named("data") {
    data()
}

repositories {
    nexus {
        content {
            includeGroup("com.possible-triangle")
            includeGroup("com.ninni.dye_depot")
        }
    }

    maven {
        url = uri("https://maven.blamejared.com/")
        content {
            includeGroup("mezz.jei")
            includeGroup("foundry.veil")
            includeGroup("gg.moonflower")
            includeGroup("io.github.ocelot")
        }
    }
    maven {
        url = uri("https://mvn.devos.one/snapshots")
        content {
            includeGroup("com.tterrag.registrate")
        }
    }
    maven {
        url = uri("https://maven.createmod.net")
        content {
            includeGroup("com.simibubi.create")
            includeGroup("net.createmod.ponder")
            includeGroup("dev.engine-room.flywheel")
        }
    }
    maven {
        url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
        content {
            includeGroup("fuzs.forgeconfigapiport")
        }
    }
    maven {
        url = uri("https://maven.ryanhcode.dev/releases")
        content {
            includeGroupAndSubgroups("dev.eriksonn")
            includeGroupAndSubgroups("dev.ryanhcode")
            includeGroupAndSubgroups("dev.simulated_team")
        }
    }
}

dependencies {
    modInclude(libs.registrate)

    modApi(libs.multikulti.core)
    modImplementation(libs.multikulti.datagen)

    modImplementation(
        variantOf(libs.create) {
            classifier("slim")
        },
    ) {
        isTransitive = false
    }
    modImplementation(libs.ponder)
    modCompileOnly(libs.flywheel.api)
    modImplementation(pack.modrinth.another.furniture)
    modImplementation(pack.modrinth.comforts)
    modImplementation(pack.modrinth.moonlight)
    modImplementation(pack.modrinth.supplementaries)
    modImplementation(pack.modrinth.supplementaries.squared)
    modImplementation(pack.modrinth.quark)
    modImplementation(pack.modrinth.zeta)
    modImplementation(pack.modrinth.farmers.delight)
    modImplementation(pack.modrinth.clayworks)
    modImplementation(pack.modrinth.upgrade.aquatic)
    modImplementation(pack.modrinth.blueprint)
    modImplementation(pack.modrinth.chalk.mod)
    modImplementation(pack.modrinth.create.deco)
    // modRuntimeOnly(libs.sable) { isTransitive = false }
    modCompileOnly(libs.create.simulated) { isTransitive = false }
    modCompileOnly(libs.create.aeronautics) { isTransitive = false }
    modCompileOnly(pack.modrinth.domestication.innovation)
    modCompileOnly(pack.modrinth.alexs.caves)
    modCompileOnly(pack.modrinth.alexs.mobs)
    modImplementation(pack.modrinth.waystones)
    modCompileOnly(pack.modrinth.create.steam.n.rails)
    modImplementation(libs.dye.depot)
    accessTransformers(libs.dye.depot)
    modImplementation(pack.modrinth.snowy.spirit)
    modImplementation(pack.modrinth.fusion.connected.textures)
    modImplementation(pack.modrinth.vanillabackport)
    // modRuntimeOnly(pack.modrinth.immersiveengineering)
    modRuntimeOnly(libs.flywheel)
    modRuntimeOnly(libs.jei)
    modRuntimeOnly(pack.modrinth.jade)
    // modRuntimeOnly(pack.modrinth.citadel)
    modImplementation(pack.modrinth.interiors)
    modRuntimeOnly(pack.modrinth.curios)
    modRuntimeOnly(pack.modrinth.geckolib)
    modRuntimeOnly(pack.modrinth.ars.nouveau)
    modRuntimeOnly(pack.modrinth.gallery)
    modRuntimeOnly(pack.modrinth.more.concrete)
    modRuntimeOnly(pack.modrinth.balm)
    // modRuntimeOnly(pack.modrinth.polytone)
    modRuntimeOnly(pack.modrinth.amendments)
    modRuntimeOnly(pack.modrinth.registry.dump)
    modRuntimeOnly(pack.curseforge.openblocks.elevator)
    modRuntimeOnly(pack.modrinth.connected.glass)
    modRuntimeOnly(pack.modrinth.supermartijn642s.core.lib)
    modRuntimeOnly(pack.modrinth.prickle)
    modRuntimeOnly(pack.modrinth.bookshelf.lib)
    modRuntimeOnly(pack.modrinth.botany.pots)
    modRuntimeOnly(pack.modrinth.platform)
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val patchArtsAndCraftsLang =
    tasks.register("patchArtsAndCraftsLang") {
        dependsOn("runData")
        doLast {
            val langFile = file("src/generated/resources/assets/dye_the_world/lang/en_us.json")
            val lang =
                if (langFile.isFile) {
                    linkedMapOf<String, Any?>()
                        .also { parsed ->
                            @Suppress("UNCHECKED_CAST")
                            parsed.putAll(JsonSlurper().parse(langFile) as Map<String, Any?>)
                        }
                } else {
                    linkedMapOf()
                }

            val dyes =
                listOf(
                    "amber",
                    "aqua",
                    "beige",
                    "coral",
                    "forest",
                    "ginger",
                    "indigo",
                    "maroon",
                    "mint",
                    "navy",
                    "olive",
                    "rose",
                    "slate",
                    "tan",
                    "teal",
                    "verdant",
                )

            val blockTypes =
                listOf(
                    ArtsAndCraftsLangBlockType("mud_brick", "mud_bricks"),
                    ArtsAndCraftsLangBlockType("terracotta_shingle", "terracotta_shingles"),
                    ArtsAndCraftsLangBlockType("soapstone", "soapstone"),
                    ArtsAndCraftsLangBlockType("polished_soapstone", "polished_soapstone"),
                    ArtsAndCraftsLangBlockType("soapstone_brick", "soapstone_bricks"),
                )
            val pottedVariantSuffixes =
                listOf(
                    "potted_acacia_sapling",
                    "potted_allium",
                    "potted_azure_bluet",
                    "potted_bamboo",
                    "potted_birch_sapling",
                    "potted_blue_orchid",
                    "potted_brown_mushroom",
                    "potted_cactus",
                    "potted_cherry_sapling",
                    "potted_cork_sapling",
                    "potted_cornflower",
                    "potted_crimson_fungus",
                    "potted_crimson_roots",
                    "potted_dandelion",
                    "potted_dark_oak_sapling",
                    "potted_dead_bush",
                    "potted_fern",
                    "potted_jungle_sapling",
                    "potted_lily_of_the_valley",
                    "potted_mangrove_propagule",
                    "potted_oak_sapling",
                    "potted_orange_tulip",
                    "potted_oxeye_daisy",
                    "potted_pink_tulip",
                    "potted_poppy",
                    "potted_azalea_bush",
                    "potted_flowering_azalea_bush",
                    "potted_red_mushroom",
                    "potted_red_tulip",
                    "potted_spruce_sapling",
                    "potted_torchflower",
                    "potted_warped_fungus",
                    "potted_warped_roots",
                    "potted_white_tulip",
                    "potted_wither_rose",
                )

            for (dyeName in dyes) {
                val dyeTitle = artsAndCraftsTitleCase(dyeName)
                lang["block.dye_the_world.${dyeName}_chalk"] = "$dyeTitle Chalk"
                lang["block.dye_the_world.${dyeName}_chalk_dust"] = "$dyeTitle Chalk Dust"
                lang["item.dye_the_world.${dyeName}_chalk_stick"] = "$dyeTitle Chalk Stick"
                lang["item.dye_the_world.${dyeName}_paintbrush"] = "$dyeTitle Paintbrush"
                lang["block.dye_the_world.${dyeName}_plaster"] = "$dyeTitle Plaster"
                lang["block.dye_the_world.${dyeName}_flower_pot"] = "$dyeTitle Flower Pot"

                for (blockType in blockTypes) {
                    val cubeTitle = artsAndCraftsTitleCase(blockType.cubeName)
                    val baseTitle = artsAndCraftsTitleCase(blockType.baseName)
                    lang["block.dye_the_world.${dyeName}_${blockType.cubeName}"] = "$dyeTitle $cubeTitle"
                    if (blockType.hasSlab) {
                        lang["block.dye_the_world.${dyeName}_${blockType.baseName}_slab"] = "$dyeTitle $baseTitle Slab"
                    }
                    if (blockType.hasStairs) {
                        lang["block.dye_the_world.${dyeName}_${blockType.baseName}_stairs"] = "$dyeTitle $baseTitle Stairs"
                    }
                    if (blockType.hasWall) {
                        lang["block.dye_the_world.${dyeName}_${blockType.baseName}_wall"] = "$dyeTitle $baseTitle Wall"
                    }
                }
            }

            file("src/generated/resources/assets/dye_the_world/blockstates")
                .listFiles { _, name -> name.endsWith(".json") }
                ?.forEach { blockstate ->
                    val id = blockstate.nameWithoutExtension
                    lang.putIfAbsent("block.dye_the_world.$id", artsAndCraftsTitleCase(id))
                }

            file("src/generated/resources/assets/dye_the_world/models/item")
                .listFiles { _, name -> name.endsWith(".json") }
                ?.forEach { itemModel ->
                    val id = itemModel.nameWithoutExtension
                    if (!lang.containsKey("block.dye_the_world.$id")) {
                        lang.putIfAbsent("item.dye_the_world.$id", artsAndCraftsTitleCase(id))
                    }
                }

            langFile.parentFile.mkdirs()
            langFile.writeText("${JsonOutput.prettyPrint(JsonOutput.toJson(lang))}\n")

            fun mergeTag(
                relativePath: String,
                valuesToAdd: Iterable<String>,
            ) {
                val tagFile = file("src/generated/resources/$relativePath")
                val tag: LinkedHashMap<String, Any?> =
                    if (tagFile.isFile) {
                        linkedMapOf<String, Any?>()
                            .also { parsed ->
                                @Suppress("UNCHECKED_CAST")
                                parsed.putAll(JsonSlurper().parse(tagFile) as Map<String, Any?>)
                            }
                    } else {
                        linkedMapOf("replace" to false)
                    }
                val values = (tag["values"] as? List<*>)?.toMutableList() ?: mutableListOf<Any?>()
                val existingIds =
                    values
                        .mapNotNull { value ->
                            when (value) {
                                is String -> value
                                is Map<*, *> -> value["id"] as? String
                                else -> null
                            }
                        }.toMutableSet()

                valuesToAdd.forEach { id ->
                    if (existingIds.add(id)) {
                        values.add(linkedMapOf("id" to id, "required" to false))
                    }
                }

                tag["replace"] = tag["replace"] ?: false
                tag["values"] = values
                tagFile.parentFile.mkdirs()
                tagFile.writeText("${JsonOutput.prettyPrint(JsonOutput.toJson(tag))}\n")
            }

            val blockIds = mutableListOf<String>()
            val slabIds = mutableListOf<String>()
            val stairIds = mutableListOf<String>()
            val wallIds = mutableListOf<String>()
            val flowerPotIds = mutableListOf<String>()
            val chalkDustIds = mutableListOf<String>()
            val chalkStickIds = mutableListOf<String>()
            val paintbrushIds = mutableListOf<String>()

            for (dyeName in dyes) {
                blockIds += "dye_the_world:${dyeName}_chalk"
                blockIds += "dye_the_world:${dyeName}_chalk_dust"
                blockIds += "dye_the_world:${dyeName}_plaster"
                flowerPotIds += "dye_the_world:${dyeName}_flower_pot"
                flowerPotIds += pottedVariantSuffixes.map { suffix -> "dye_the_world:${dyeName}_$suffix" }
                chalkDustIds += "dye_the_world:${dyeName}_chalk_dust"
                chalkStickIds += "dye_the_world:${dyeName}_chalk_stick"
                paintbrushIds += "dye_the_world:${dyeName}_paintbrush"

                for (blockType in blockTypes) {
                    blockIds += "dye_the_world:${dyeName}_${blockType.cubeName}"
                    if (blockType.hasSlab) {
                        val id = "dye_the_world:${dyeName}_${blockType.baseName}_slab"
                        blockIds += id
                        slabIds += id
                    }
                    if (blockType.hasStairs) {
                        val id = "dye_the_world:${dyeName}_${blockType.baseName}_stairs"
                        blockIds += id
                        stairIds += id
                    }
                    if (blockType.hasWall) {
                        val id = "dye_the_world:${dyeName}_${blockType.baseName}_wall"
                        blockIds += id
                        wallIds += id
                    }
                }
            }

            mergeTag("data/minecraft/tags/block/mineable/pickaxe.json", blockIds)
            mergeTag("data/minecraft/tags/block/slabs.json", slabIds)
            mergeTag("data/minecraft/tags/block/stairs.json", stairIds)
            mergeTag("data/minecraft/tags/block/walls.json", wallIds)
            mergeTag("data/minecraft/tags/block/flower_pots.json", flowerPotIds)
            mergeTag("data/minecraft/tags/item/slabs.json", slabIds)
            mergeTag("data/minecraft/tags/item/stairs.json", stairIds)
            mergeTag("data/minecraft/tags/item/walls.json", wallIds)
            mergeTag("data/arts_and_crafts/tags/block/chalk_dust.json", chalkDustIds)
            mergeTag("data/arts_and_crafts/tags/item/chalk_sticks.json", chalkStickIds)
            mergeTag("data/arts_and_crafts/tags/item/paintbrushes.json", paintbrushIds)
        }
    }

// runData writes to src/generated/resources after classes compile; copy fresh output into
// build/resources/main so the jar picks up recipes/assets without a processResources ↔ runData cycle.
val syncGeneratedResources =
    tasks.register<Copy>("syncGeneratedResources") {
        dependsOn(patchArtsAndCraftsLang)
        from("src/generated/resources")
        into(layout.buildDirectory.dir("resources/main"))
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

tasks.named("jar") {
    dependsOn(syncGeneratedResources)
}

upload {
    maven {
        githubPackages()
        nexus()
    }

    curseforge {
        dependencies {
            optional("chalk")
            optional("openblocks-elevator")
        }
    }

    modrinth {
        dependencies {
            optional("chalk-mod")
        }

        syncBodyFromReadme()
    }

    forEach {
        dependencies {
            required("dye-depot")
            optional("create")
            optional("another-furniture")
            optional("comforts")
            optional("clayworks")
            optional("farmers-delight")
            optional("quark")
            // optional("domestication-innovation")
            optional("supplementaries")
            optional("supplementaries-squared")
            // optional("alexs-caves")
            optional("ars-nouveau")
            optional("create-deco")
            // optional("create-steam-n-rails")
            optional("upgrade-aquatic")
            optional("more-concrete")
            optional("waystones")
            optional("interiors")
            optional("snowy-spirit")
            optional("botany-pots")
            optional("connected-glass")
            optional("vanillabackport")
            optional("create-aeronautics")
        }
    }
}

idea {
    module {
        excludeDirs.add(file("polytone"))
    }
}

enableSonarQube()
enableSpotless()

env["MODRINTH_HOME"]?.let { home ->
    val task =
        tasks.register<Copy>("copyToLocalPack") {
            dependsOn(tasks.build)
            from(tasks.jar)
            destinationDir = file(home).resolve("profiles/dyes/mods")
        }

    tasks.publish {
        finalizedBy(task)
    }
}
