@file:Suppress("ktlint:standard:max-line-length")

package com.possible_triangle.dye_the_world.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.possible_triangle.dye_the_world.Constants
import com.possible_triangle.dye_the_world.Constants.Mods.ARTS_AND_CRAFTS
import com.possible_triangle.dye_the_world.DEPOT_DYES
import com.possible_triangle.dye_the_world.VANILLA_DYES
import com.possible_triangle.dye_the_world.dyesFor
import com.possible_triangle.dye_the_world.namespace
import com.tterrag.registrate.providers.ProviderType
import com.tterrag.registrate.providers.RegistrateProvider
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.neoforged.fml.LogicalSide
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Generates blockstate and model JSONs for Arts and Crafts blocks for Dye Depot colors.
 * Blockstates are written to both dye_the_world and arts_and_crafts (multipart format
 * so wall/stairs work; arts_and_crafts copy overrides broken variant-format from A&C if present).
 * Models are written to arts_and_crafts namespace (textures come from A&C).
 */
class ArtsAndCraftsAssetProvider(
    private val output: PackOutput,
) : DataProvider,
    RegistrateProvider {
    override fun run(cachedOutput: CachedOutput): CompletableFuture<*> {
        val resourceRoot = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
        val dataRoot = output.getOutputFolder(PackOutput.Target.DATA_PACK)
        val modelRoot = resourceRoot.resolve(ARTS_AND_CRAFTS)
        val dtwRoot = resourceRoot.resolve(Constants.MOD_ID)
        val blockstateRootAc = resourceRoot.resolve(ARTS_AND_CRAFTS)
        val itemRootAc = resourceRoot.resolve(ARTS_AND_CRAFTS)

        val futures = mutableListOf<CompletableFuture<*>>()
        val dyes = dyesFor(ARTS_AND_CRAFTS)

        for (dye in dyes) {
            val dyeName = dye.serializedName
            for (blockType in BLOCK_TYPES) {
                for ((filename, content) in blockType.blockstates(dyeName)) {
                    futures.add(
                        DataProvider.saveStable(
                            cachedOutput,
                            JsonParser.parseString(content),
                            dtwRoot.resolve("blockstates").resolve("$filename.json"),
                        ),
                    )
                    futures.add(
                        DataProvider.saveStable(
                            cachedOutput,
                            JsonParser.parseString(content),
                            blockstateRootAc.resolve("blockstates").resolve("$filename.json"),
                        ),
                    )
                }
                for ((filename, content) in blockType.models(dyeName)) {
                    val path = modelRoot.resolve("models").resolve("block").resolve("$filename.json")
                    futures.add(DataProvider.saveStable(cachedOutput, JsonParser.parseString(content), path))
                }
                for ((filename, content) in blockType.itemModels(dyeName)) {
                    futures.add(
                        DataProvider.saveStable(
                            cachedOutput,
                            JsonParser.parseString(content),
                            dtwRoot.resolve("models").resolve("item").resolve("$filename.json"),
                        ),
                    )
                    futures.add(
                        DataProvider.saveStable(
                            cachedOutput,
                            JsonParser.parseString(content),
                            itemRootAc.resolve("models").resolve("item").resolve("$filename.json"),
                        ),
                    )
                }
            }

            for ((filename, content) in allDepotColorRecipes(dyeName, "${dye.namespace}:${dyeName}_dye")) {
                futures.add(
                    DataProvider.saveStable(
                        cachedOutput,
                        JsonParser.parseString(content),
                        recipePath(dataRoot, filename),
                    ),
                )
            }
        }

        val depotDyeNames = dyes.map { it.serializedName }
        futures.addAll(generatePaintbrushPalettes(cachedOutput, dataRoot, depotDyeNames))
        futures.addAll(generatePaintableTagExtensions(cachedOutput, dataRoot, depotDyeNames))
        futures.addAll(generatePaintbrushItemTagExtension(cachedOutput, dataRoot, depotDyeNames))
        futures.add(generateLang(cachedOutput, resourceRoot, depotDyeNames))

        // Arts & Crafts chalk blockstates for custom colors reference color-specific models
        // (e.g. maroon_dot, indigo_plus). Generate those model files here so runtime does not
        // fail with missing model variants for *_chalk_dust and *_chalk blockstates.
        for (dye in dyes) {
            val dyeName = dye.serializedName

            // Chalk full block blockstate (registry id is dye_the_world:* — needs both namespaces).
            val chalkBlockstate = """{"variants":{"":{"model":"arts_and_crafts:block/${dyeName}_chalk"}}}"""
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkBlockstate),
                    dtwRoot.resolve("blockstates").resolve("${dyeName}_chalk.json"),
                ),
            )
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkBlockstate),
                    blockstateRootAc.resolve("blockstates").resolve("${dyeName}_chalk.json"),
                ),
            )

            // Chalk dust blockstate with all pattern + facing variants.
            val chalkDustBlockstateJson = chalkDustBlockstate(dyeName)
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkDustBlockstateJson),
                    dtwRoot.resolve("blockstates").resolve("${dyeName}_chalk_dust.json"),
                ),
            )
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkDustBlockstateJson),
                    blockstateRootAc.resolve("blockstates").resolve("${dyeName}_chalk_dust.json"),
                ),
            )

            // Base chalk block model (used by arts_and_crafts:blockstates/<dye>_chalk.json).
            val chalkBlockModel =
                """{"parent":"minecraft:block/cube_all","textures":{"all":"arts_and_crafts:block/${dyeName}_chalk"}}"""
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkBlockModel),
                    modelRoot.resolve("models").resolve("block").resolve("${dyeName}_chalk.json"),
                ),
            )

            // Matching item model for chalk item (registry id is dye_the_world:*).
            val chalkItemModel = """{"parent":"arts_and_crafts:block/${dyeName}_chalk"}"""
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkItemModel),
                    dtwRoot.resolve("models").resolve("item").resolve("${dyeName}_chalk.json"),
                ),
            )
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkItemModel),
                    itemRootAc.resolve("models").resolve("item").resolve("${dyeName}_chalk.json"),
                ),
            )

            val paintbrushItemModel =
                """{"parent":"minecraft:item/handheld","textures":{"layer0":"arts_and_crafts:item/${dyeName}_paintbrush"}}"""
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(paintbrushItemModel),
                    dtwRoot.resolve("models").resolve("item").resolve("${dyeName}_paintbrush.json"),
                ),
            )
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(paintbrushItemModel),
                    itemRootAc.resolve("models").resolve("item").resolve("${dyeName}_paintbrush.json"),
                ),
            )

            // Chalk stick item model (item used for drawing chalk dust patterns).
            val chalkStickItemModel =
                """{"parent":"minecraft:item/handheld","textures":{"layer0":"arts_and_crafts:item/${dyeName}_chalk_stick"}}"""
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkStickItemModel),
                    dtwRoot.resolve("models").resolve("item").resolve("${dyeName}_chalk_stick.json"),
                ),
            )
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(chalkStickItemModel),
                    itemRootAc.resolve("models").resolve("item").resolve("${dyeName}_chalk_stick.json"),
                ),
            )

            // Colorized chalk-dust glyph models (dot/plus/x/etc), all based on chalk_dust parent.
            for (suffix in CHALK_DUST_PATTERN_SUFFIXES) {
                val modelName = "${dyeName}_$suffix"
                val glyphModel =
                    """{"parent":"arts_and_crafts:block/chalk_dust","textures":{"particle":"arts_and_crafts:block/$modelName","dust":"arts_and_crafts:block/$modelName"}}"""
                futures.add(
                    DataProvider.saveStable(
                        cachedOutput,
                        JsonParser.parseString(glyphModel),
                        modelRoot.resolve("models").resolve("block").resolve("$modelName.json"),
                    ),
                )
            }

            // Flower pot base block + item model.
            val flowerPotName = "${dyeName}_flower_pot"
            val flowerPotBlockstate = """{"variants":{"":{"model":"arts_and_crafts:block/$flowerPotName"}}}"""
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(flowerPotBlockstate),
                    blockstateRootAc.resolve("blockstates").resolve("$flowerPotName.json"),
                ),
            )
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(flowerPotBlockstate),
                    dtwRoot.resolve("blockstates").resolve("$flowerPotName.json"),
                ),
            )
            val flowerPotBlockModel =
                """{"parent":"minecraft:block/flower_pot","textures":{"flowerpot":"arts_and_crafts:block/$flowerPotName","particle":"arts_and_crafts:block/$flowerPotName"}}"""
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(flowerPotBlockModel),
                    modelRoot.resolve("models").resolve("block").resolve("$flowerPotName.json"),
                ),
            )
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(flowerPotBlockModel),
                    dtwRoot.resolve("models").resolve("block").resolve("$flowerPotName.json"),
                ),
            )
            val flowerPotItemModel =
                """{"parent":"minecraft:item/generated","textures":{"layer0":"arts_and_crafts:item/$flowerPotName"}}"""
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(flowerPotItemModel),
                    itemRootAc.resolve("models").resolve("item").resolve("$flowerPotName.json"),
                ),
            )
            futures.add(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(flowerPotItemModel),
                    dtwRoot.resolve("models").resolve("item").resolve("$flowerPotName.json"),
                ),
            )

            // Potted plant variants for colored flower pots.
            for (variant in POTTED_VARIANTS) {
                val blockstateName = "${dyeName}_${variant.blockstateSuffix}"
                val modelName = "${dyeName}_${variant.modelSuffix}"
                val pottedBlockstate = """{"variants":{"":{"model":"arts_and_crafts:block/$modelName"}}}"""
                futures.add(
                    DataProvider.saveStable(
                        cachedOutput,
                        JsonParser.parseString(pottedBlockstate),
                        blockstateRootAc.resolve("blockstates").resolve("$blockstateName.json"),
                    ),
                )
                futures.add(
                    DataProvider.saveStable(
                        cachedOutput,
                        JsonParser.parseString(pottedBlockstate),
                        dtwRoot.resolve("blockstates").resolve("$blockstateName.json"),
                    ),
                )
                val textures =
                    buildString {
                        append("\"flowerpot\":\"arts_and_crafts:block/$flowerPotName\"")
                        append(",\"particle\":\"arts_and_crafts:block/$flowerPotName\"")
                        variant.plant?.let { append(",\"plant\":\"$it\"") }
                    }
                val pottedModel =
                    """{"parent":"${variant.parent}","textures":{$textures}}"""
                futures.add(
                    DataProvider.saveStable(
                        cachedOutput,
                        JsonParser.parseString(pottedModel),
                        modelRoot.resolve("models").resolve("block").resolve("$modelName.json"),
                    ),
                )
                futures.add(
                    DataProvider.saveStable(
                        cachedOutput,
                        JsonParser.parseString(pottedModel),
                        dtwRoot.resolve("models").resolve("block").resolve("$modelName.json"),
                    ),
                )
            }
        }
        return CompletableFuture.allOf(*futures.toTypedArray())
    }

    override fun getName() = "Arts and Crafts (Dye Depot) assets"

    override fun getSide() = LogicalSide.CLIENT

    private data class BlockType(
        val baseName: String,
        val cubeName: String,
        val textureName: String,
        val slabDoubleModel: String,
        val hasStairs: Boolean = true,
        val hasSlab: Boolean = true,
        val hasWall: Boolean = true,
        val plasterStyle: Boolean = false,
    ) {
        private fun textureFor(dye: String): String =
            if (textureName.startsWith("cyan_")) {
                "${dye}_${textureName.removePrefix("cyan_")}"
            } else {
                textureName
            }

        fun blockstates(dye: String): List<Pair<String, String>> {
            val prefix = "${dye}_"
            val list = mutableListOf<Pair<String, String>>()
            if (plasterStyle) {
                val model = "arts_and_crafts:block/${prefix}$baseName"
                val entries =
                    listOf(
                        Triple("down", "\"x\":270", ""),
                        Triple("east", "\"y\":270", ""),
                        Triple("north", "\"y\":180", ""),
                        Triple("south", "", ""),
                        Triple("up", "\"x\":90", ""),
                        Triple("west", "\"y\":90", ""),
                    ).flatMap { (facing, rotA, rotB) ->
                        listOf(false, true).map { waterlogged ->
                            val key = "\"facing=$facing,waterlogged=$waterlogged\""
                            val parts = mutableListOf("\"model\":\"$model\"")
                            if (rotA.isNotEmpty()) parts.add(rotA)
                            if (rotB.isNotEmpty()) parts.add(rotB)
                            "$key:{${parts.joinToString(",")}}"
                        }
                    }
                list.add("${prefix}$baseName" to """{"variants":{${entries.joinToString(",")}}}""")
            } else {
                list.add("${prefix}$cubeName" to """{"variants":{"":{"model":"arts_and_crafts:block/${prefix}$cubeName"}}}""")
            }
            if (hasStairs) {
                val s = STAIRS_TEMPLATE.replace("PREFIX_", "${dye}_${baseName}_")
                list.add("${prefix}${baseName}_stairs" to s)
            }
            if (hasSlab) {
                list.add(
                    "${prefix}${baseName}_slab" to
                        """{"variants":{"type=bottom":{"model":"arts_and_crafts:block/${prefix}${baseName}_slab"},"type=double":{"model":"arts_and_crafts:block/${prefix}$slabDoubleModel"},"type=top":{"model":"arts_and_crafts:block/${prefix}${baseName}_slab_top"}}}""",
                )
            }
            if (hasWall) {
                val w = WALL_TEMPLATE.replace("PREFIX_", "${dye}_${baseName}_")
                list.add("${prefix}${baseName}_wall" to w)
            }
            return list
        }

        fun models(dye: String): List<Pair<String, String>> {
            val prefix = "${dye}_"
            val tex = "arts_and_crafts:block/${textureFor(dye)}"
            val list = mutableListOf<Pair<String, String>>()
            if (!plasterStyle) {
                list.add("${prefix}$cubeName" to """{"parent":"minecraft:block/cube_all","textures":{"all":"$tex"}}""")
            } else {
                list.add(
                    "${prefix}$baseName" to
                        """{"parent":"arts_and_crafts:block/plaster_model","textures":{"particle":"$tex","plaster":"$tex"}}""",
                )
            }
            if (hasStairs) {
                list.add(
                    "${prefix}${baseName}_stairs" to
                        """{"parent":"minecraft:block/stairs","textures":{"bottom":"$tex","side":"$tex","top":"$tex"}}""",
                )
                list.add(
                    "${prefix}${baseName}_stairs_inner" to
                        """{"parent":"minecraft:block/inner_stairs","textures":{"bottom":"$tex","side":"$tex","top":"$tex"}}""",
                )
                list.add(
                    "${prefix}${baseName}_stairs_outer" to
                        """{"parent":"minecraft:block/outer_stairs","textures":{"bottom":"$tex","side":"$tex","top":"$tex"}}""",
                )
            }
            if (hasSlab) {
                list.add(
                    "${prefix}${baseName}_slab" to
                        """{"parent":"minecraft:block/slab","textures":{"bottom":"$tex","side":"$tex","top":"$tex"}}""",
                )
                list.add(
                    "${prefix}${baseName}_slab_top" to
                        """{"parent":"minecraft:block/slab_top","textures":{"bottom":"$tex","side":"$tex","top":"$tex"}}""",
                )
            }
            if (hasWall) {
                list.add(
                    "${prefix}${baseName}_wall_post" to """{"parent":"minecraft:block/template_wall_post","textures":{"wall":"$tex"}}""",
                )
                list.add(
                    "${prefix}${baseName}_wall_side" to """{"parent":"minecraft:block/template_wall_side","textures":{"wall":"$tex"}}""",
                )
                list.add(
                    "${prefix}${baseName}_wall_side_tall" to
                        """{"parent":"minecraft:block/template_wall_side_tall","textures":{"wall":"$tex"}}""",
                )
            }
            return list
        }

        /** Item models for inventory/hand (dye_the_world namespace); walls use wall_inventory. */
        fun itemModels(dye: String): List<Pair<String, String>> {
            val prefix = "${dye}_"
            val tex = "arts_and_crafts:block/${textureFor(dye)}"
            val list = mutableListOf<Pair<String, String>>()
            if (!plasterStyle) {
                list.add("${prefix}$cubeName" to """{"parent":"arts_and_crafts:block/${prefix}$cubeName"}""")
            }
            if (hasStairs) {
                list.add("${prefix}${baseName}_stairs" to """{"parent":"arts_and_crafts:block/${prefix}${baseName}_stairs"}""")
            }
            if (hasSlab) {
                list.add("${prefix}${baseName}_slab" to """{"parent":"arts_and_crafts:block/${prefix}${baseName}_slab"}""")
            }
            if (hasWall) {
                list.add("${prefix}${baseName}_wall" to """{"parent":"minecraft:block/wall_inventory","textures":{"wall":"$tex"}}""")
            }
            if (plasterStyle) {
                list.add("${prefix}$baseName" to """{"parent":"arts_and_crafts:block/${prefix}$baseName"}""")
            }
            return list
        }
    }

    companion object {
        private data class PottedVariant(
            val blockstateSuffix: String,
            val modelSuffix: String,
            val parent: String,
            val plant: String? = null,
        )

        private fun potted(
            name: String,
            plant: String,
        ): PottedVariant = PottedVariant(name, name, "minecraft:block/flower_pot_cross", plant)

        private fun potted(
            name: String,
            modelSuffix: String,
            plant: String,
        ): PottedVariant = PottedVariant(name, modelSuffix, "minecraft:block/flower_pot_cross", plant)

        private val POTTED_VARIANTS =
            listOf(
                potted("potted_acacia_sapling", "minecraft:block/acacia_sapling"),
                potted("potted_allium", "minecraft:block/allium"),
                potted("potted_azure_bluet", "minecraft:block/azure_bluet"),
                PottedVariant("potted_bamboo", "potted_bamboo", "minecraft:block/potted_bamboo"),
                potted("potted_birch_sapling", "minecraft:block/birch_sapling"),
                potted("potted_blue_orchid", "minecraft:block/blue_orchid"),
                potted("potted_brown_mushroom", "minecraft:block/brown_mushroom"),
                PottedVariant("potted_cactus", "potted_cactus", "minecraft:block/potted_cactus"),
                potted("potted_cherry_sapling", "minecraft:block/cherry_sapling"),
                potted("potted_cork_sapling", "arts_and_crafts:block/cork_sapling"),
                potted("potted_cornflower", "minecraft:block/cornflower"),
                potted("potted_crimson_fungus", "minecraft:block/crimson_fungus"),
                potted("potted_crimson_roots", "potted_crimson_roots_pot", "minecraft:block/crimson_roots_pot"),
                potted("potted_dandelion", "minecraft:block/dandelion"),
                potted("potted_dark_oak_sapling", "minecraft:block/dark_oak_sapling"),
                potted("potted_dead_bush", "minecraft:block/dead_bush"),
                PottedVariant("potted_fern", "potted_fern", "minecraft:block/tinted_flower_pot_cross", "minecraft:block/fern"),
                potted("potted_jungle_sapling", "minecraft:block/jungle_sapling"),
                potted("potted_lily_of_the_valley", "minecraft:block/lily_of_the_valley"),
                PottedVariant(
                    "potted_mangrove_propagule",
                    "potted_mangrove_propagule",
                    "minecraft:block/potted_mangrove_propagule",
                    "minecraft:block/mangrove_propagule",
                ),
                potted("potted_oak_sapling", "minecraft:block/oak_sapling"),
                potted("potted_orange_tulip", "minecraft:block/orange_tulip"),
                potted("potted_oxeye_daisy", "minecraft:block/oxeye_daisy"),
                potted("potted_pink_tulip", "minecraft:block/pink_tulip"),
                potted("potted_poppy", "minecraft:block/poppy"),
                PottedVariant("potted_azalea_bush", "potted_azalea_bush", "minecraft:block/potted_azalea_bush"),
                PottedVariant(
                    "potted_flowering_azalea_bush",
                    "potted_flowering_azalea_bush",
                    "minecraft:block/potted_flowering_azalea_bush",
                ),
                potted("potted_red_mushroom", "minecraft:block/red_mushroom"),
                potted("potted_red_tulip", "minecraft:block/red_tulip"),
                potted("potted_spruce_sapling", "minecraft:block/spruce_sapling"),
                potted("potted_torchflower", "minecraft:block/torchflower"),
                potted("potted_warped_fungus", "minecraft:block/warped_fungus"),
                potted("potted_warped_roots", "potted_warped_roots_pot", "minecraft:block/warped_roots_pot"),
                potted("potted_white_tulip", "minecraft:block/white_tulip"),
                potted("potted_wither_rose", "minecraft:block/wither_rose"),
            )

        private val CHALK_DUST_FACING_ROTATIONS =
            listOf(
                Triple("down", "\"x\":270", ""),
                Triple("east", "\"y\":270", ""),
                Triple("north", "\"y\":180", ""),
                Triple("south", "", ""),
                Triple("up", "\"x\":90", ""),
                Triple("west", "\"y\":90", ""),
            )

        private fun chalkDustBlockstate(dyeName: String): String {
            val entries = mutableListOf<String>()
            for ((patternIndex, suffix) in CHALK_DUST_PATTERN_SUFFIXES.withIndex()) {
                val model = "${dyeName}_$suffix"
                for ((facing, rotA, rotB) in CHALK_DUST_FACING_ROTATIONS) {
                    val key = "\"chalk_pattern=$patternIndex,facing=$facing\""
                    val parts = mutableListOf("\"model\":\"arts_and_crafts:block/$model\"")
                    if (rotA.isNotEmpty()) parts.add(rotA)
                    if (rotB.isNotEmpty()) parts.add(rotB)
                    entries.add("$key:{${parts.joinToString(",")}}")
                }
            }
            return """{"variants":{${entries.joinToString(",")}}}"""
        }

        private val WALL_TEMPLATE = """{"multipart":[{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_post"},"when":{"up":true}},{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_side","uvlock":true,"y":90},"when":{"east":"low"}},{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_side_tall","uvlock":true,"y":90},"when":{"east":"tall"}},{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_side","uvlock":true},"when":{"north":"low"}},{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_side_tall","uvlock":true},"when":{"north":"tall"}},{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_side","uvlock":true,"y":180},"when":{"south":"low"}},{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_side_tall","uvlock":true,"y":180},"when":{"south":"tall"}},{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_side","uvlock":true,"y":270},"when":{"west":"low"}},{"apply":{"model":"arts_and_crafts:block/PREFIX_wall_side_tall","uvlock":true,"y":270},"when":{"west":"tall"}}]}"""
        private val STAIRS_TEMPLATE = """{"variants":{"facing=east,half=bottom,shape=inner_left":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"y":270},"facing=east,half=bottom,shape=inner_right":{"model":"arts_and_crafts:block/PREFIX_stairs_inner"},"facing=east,half=bottom,shape=outer_left":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"y":270},"facing=east,half=bottom,shape=outer_right":{"model":"arts_and_crafts:block/PREFIX_stairs_outer"},"facing=east,half=bottom,shape=straight":{"model":"arts_and_crafts:block/PREFIX_stairs"},"facing=east,half=top,shape=inner_left":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"x":180},"facing=east,half=top,shape=inner_right":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"x":180,"y":90},"facing=east,half=top,shape=outer_left":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"x":180},"facing=east,half=top,shape=outer_right":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"x":180,"y":90},"facing=east,half=top,shape=straight":{"model":"arts_and_crafts:block/PREFIX_stairs","uvlock":true,"x":180},"facing=north,half=bottom,shape=inner_left":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"y":180},"facing=north,half=bottom,shape=inner_right":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"y":270},"facing=north,half=bottom,shape=outer_left":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"y":180},"facing=north,half=bottom,shape=outer_right":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"y":270},"facing=north,half=bottom,shape=straight":{"model":"arts_and_crafts:block/PREFIX_stairs","uvlock":true,"y":270},"facing=north,half=top,shape=inner_left":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"x":180,"y":270},"facing=north,half=top,shape=inner_right":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"x":180},"facing=north,half=top,shape=outer_left":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"x":180,"y":270},"facing=north,half=top,shape=outer_right":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"x":180},"facing=north,half=top,shape=straight":{"model":"arts_and_crafts:block/PREFIX_stairs","uvlock":true,"x":180,"y":270},"facing=south,half=bottom,shape=inner_left":{"model":"arts_and_crafts:block/PREFIX_stairs_inner"},"facing=south,half=bottom,shape=inner_right":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"y":90},"facing=south,half=bottom,shape=outer_left":{"model":"arts_and_crafts:block/PREFIX_stairs_outer"},"facing=south,half=bottom,shape=outer_right":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"y":90},"facing=south,half=bottom,shape=straight":{"model":"arts_and_crafts:block/PREFIX_stairs","uvlock":true,"y":90},"facing=south,half=top,shape=inner_left":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"x":180,"y":90},"facing=south,half=top,shape=inner_right":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"x":180,"y":180},"facing=south,half=top,shape=outer_left":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"x":180,"y":90},"facing=south,half=top,shape=outer_right":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"x":180,"y":180},"facing=south,half=top,shape=straight":{"model":"arts_and_crafts:block/PREFIX_stairs","uvlock":true,"x":180,"y":90},"facing=west,half=bottom,shape=inner_left":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"y":90},"facing=west,half=bottom,shape=inner_right":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"y":180},"facing=west,half=bottom,shape=outer_left":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"y":90},"facing=west,half=bottom,shape=outer_right":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"y":180},"facing=west,half=bottom,shape=straight":{"model":"arts_and_crafts:block/PREFIX_stairs","uvlock":true,"y":180},"facing=west,half=top,shape=inner_left":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"x":180,"y":180},"facing=west,half=top,shape=inner_right":{"model":"arts_and_crafts:block/PREFIX_stairs_inner","uvlock":true,"x":180,"y":270},"facing=west,half=top,shape=outer_left":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"x":180,"y":180},"facing=west,half=top,shape=outer_right":{"model":"arts_and_crafts:block/PREFIX_stairs_outer","uvlock":true,"x":180,"y":270},"facing=west,half=top,shape=straight":{"model":"arts_and_crafts:block/PREFIX_stairs","uvlock":true,"x":180,"y":180}}}"""

        private val BLOCK_TYPES =
            listOf(
                BlockType("mud_brick", "mud_bricks", "cyan_mud_bricks", "mud_bricks"),
                BlockType("terracotta_shingle", "terracotta_shingles", "cyan_terracotta_shingles", "terracotta_shingles"),
                BlockType("soapstone", "soapstone", "cyan_soapstone", "soapstone"),
                BlockType("polished_soapstone", "polished_soapstone", "cyan_polished_soapstone", "polished_soapstone"),
                BlockType("soapstone_brick", "soapstone_bricks", "cyan_soapstone_bricks", "soapstone_bricks"),
                BlockType(
                    "plaster",
                    "plaster",
                    "cyan_plaster",
                    "plaster",
                    hasStairs = false,
                    hasSlab = false,
                    hasWall = false,
                    plasterStyle = true,
                ),
            )

        private fun recipePath(
            dataRoot: Path,
            filename: String,
        ): Path = dataRoot.resolve(ARTS_AND_CRAFTS).resolve("recipe").resolve("$filename.json")

        private fun allDepotColorRecipes(
            dyeName: String,
            dyeItemId: String,
        ): List<Pair<String, String>> =
            soapstoneRecipes(dyeName, dyeItemId) +
                blockFamilyRecipes(dyeName, dyeItemId, MUD_BRICK_FAMILY) +
                blockFamilyRecipes(dyeName, dyeItemId, TERRACOTTA_SHINGLE_FAMILY) +
                plasterRecipes(dyeName, dyeItemId) +
                flowerPotRecipes(dyeName, dyeItemId) +
                chalkRecipes(dyeName, dyeItemId)

        private data class BlockFamilyRecipeConfig(
            val familyPrefix: String,
            val cubeBlockName: String,
            val cubeBaseItem: String,
            val slabBaseItem: String,
            val stairsBaseItem: String,
            val wallBaseItem: String,
            val groupCube: String,
            val groupSlab: String,
            val groupStairs: String,
            val groupWall: String,
        )

        private val MUD_BRICK_FAMILY =
            BlockFamilyRecipeConfig(
                familyPrefix = "mud_brick",
                cubeBlockName = "mud_bricks",
                cubeBaseItem = "minecraft:mud_bricks",
                slabBaseItem = "minecraft:mud_brick_slab",
                stairsBaseItem = "minecraft:mud_brick_stairs",
                wallBaseItem = "minecraft:mud_brick_wall",
                groupCube = "mud_bricks",
                groupSlab = "mud_brick_slab",
                groupStairs = "mud_brick_stairs",
                groupWall = "mud_brick_wall",
            )

        private val TERRACOTTA_SHINGLE_FAMILY =
            BlockFamilyRecipeConfig(
                familyPrefix = "terracotta_shingle",
                cubeBlockName = "terracotta_shingles",
                cubeBaseItem = "arts_and_crafts:terracotta_shingles",
                slabBaseItem = "arts_and_crafts:terracotta_shingle_slab",
                stairsBaseItem = "arts_and_crafts:terracotta_shingle_stairs",
                wallBaseItem = "arts_and_crafts:terracotta_shingle_wall",
                groupCube = "terracotta_shingle",
                groupSlab = "terracotta_shingle_slab",
                groupStairs = "terracotta_shingle_stairs",
                groupWall = "terracotta_shingle_wall",
            )

        private fun shapedDyeRecipe(
            fileName: String,
            resultId: String,
            baseItemId: String,
            dyeItemId: String,
            group: String,
            count: Int = 8,
        ): Pair<String, String> {
            val mod = Constants.MOD_ID
            return fileName to
                """{"type":"minecraft:crafting_shaped","category":"building","group":"$group","key":{"K":{"item":"$baseItemId"},"Q":{"item":"$dyeItemId"}},"pattern":["KKK","KQK","KKK"],"result":{"count":$count,"id":"$mod:$resultId"}}"""
        }

        private fun slabCraftRecipe(
            name: String,
            base: String,
        ): Pair<String, String> {
            val mod = Constants.MOD_ID
            return name to
                """{"type":"minecraft:crafting_shaped","category":"building","key":{"K":{"item":"$mod:$base"}},"pattern":["KKK"],"result":{"count":6,"id":"$mod:$name"}}"""
        }

        private fun stairsCraftRecipe(
            name: String,
            base: String,
        ): Pair<String, String> {
            val mod = Constants.MOD_ID
            return name to
                """{"type":"minecraft:crafting_shaped","category":"building","key":{"K":{"item":"$mod:$base"}},"pattern":["K  ","KK ","KKK"],"result":{"count":4,"id":"$mod:$name"}}"""
        }

        private fun wallCraftRecipe(
            name: String,
            base: String,
        ): Pair<String, String> {
            val mod = Constants.MOD_ID
            return name to
                """{"type":"minecraft:crafting_shaped","category":"building","key":{"K":{"item":"$mod:$base"}},"pattern":["KKK","KKK"],"result":{"count":6,"id":"$mod:$name"}}"""
        }

        private fun blockFamilyRecipes(
            dyeName: String,
            dyeItemId: String,
            config: BlockFamilyRecipeConfig,
        ): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            val cube = "${dyeName}_${config.cubeBlockName}"
            val prefix = config.familyPrefix

            out +=
                shapedDyeRecipe(
                    "${dyeName}_${config.cubeBlockName}_dye_recipe",
                    cube,
                    config.cubeBaseItem,
                    dyeItemId,
                    config.groupCube,
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_${prefix}_slab_dye_recipe",
                    "${dyeName}_${prefix}_slab",
                    config.slabBaseItem,
                    dyeItemId,
                    config.groupSlab,
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_${prefix}_stairs_dye_recipe",
                    "${dyeName}_${prefix}_stairs",
                    config.stairsBaseItem,
                    dyeItemId,
                    config.groupStairs,
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_${prefix}_wall_dye_recipe",
                    "${dyeName}_${prefix}_wall",
                    config.wallBaseItem,
                    dyeItemId,
                    config.groupWall,
                )

            out += slabCraftRecipe("${dyeName}_${prefix}_slab", cube)
            out += stairsCraftRecipe("${dyeName}_${prefix}_stairs", cube)
            out += wallCraftRecipe("${dyeName}_${prefix}_wall", cube)
            return out
        }

        private fun plasterRecipes(
            dyeName: String,
            dyeItemId: String,
        ): List<Pair<String, String>> =
            listOf(
                shapedDyeRecipe(
                    "${dyeName}_plaster_dye_recipe",
                    "${dyeName}_plaster",
                    "arts_and_crafts:plaster",
                    dyeItemId,
                    "plaster",
                ),
            )

        private fun soapstoneRecipes(
            dyeName: String,
            dyeItemId: String,
        ): List<Pair<String, String>> {
            val mod = Constants.MOD_ID
            val out = mutableListOf<Pair<String, String>>()

            val soap = "${dyeName}_soapstone"
            val polished = "${dyeName}_polished_soapstone"
            val bricks = "${dyeName}_soapstone_bricks"

            out += shapedDyeRecipe("${dyeName}_soapstone_dye_recipe", soap, "arts_and_crafts:soapstone", dyeItemId, "soapstone")
            out +=
                shapedDyeRecipe(
                    "${dyeName}_polished_soapstone_dye_recipe",
                    polished,
                    "arts_and_crafts:polished_soapstone",
                    dyeItemId,
                    "polished_soapstone",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_soapstone_bricks_dye_recipe",
                    bricks,
                    "arts_and_crafts:soapstone_bricks",
                    dyeItemId,
                    "soapstone_brick",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_soapstone_slab_dye_recipe",
                    "${dyeName}_soapstone_slab",
                    "arts_and_crafts:soapstone_slab",
                    dyeItemId,
                    "soapstone_slab",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_soapstone_stairs_dye_recipe",
                    "${dyeName}_soapstone_stairs",
                    "arts_and_crafts:soapstone_stairs",
                    dyeItemId,
                    "soapstone_stairs",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_soapstone_wall_dye_recipe",
                    "${dyeName}_soapstone_wall",
                    "arts_and_crafts:soapstone_wall",
                    dyeItemId,
                    "soapstone_wall",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_polished_soapstone_slab_dye_recipe",
                    "${dyeName}_polished_soapstone_slab",
                    "arts_and_crafts:polished_soapstone_slab",
                    dyeItemId,
                    "polished_soapstone_slab",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_polished_soapstone_stairs_dye_recipe",
                    "${dyeName}_polished_soapstone_stairs",
                    "arts_and_crafts:polished_soapstone_stairs",
                    dyeItemId,
                    "polished_soapstone_stairs",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_polished_soapstone_wall_dye_recipe",
                    "${dyeName}_polished_soapstone_wall",
                    "arts_and_crafts:polished_soapstone_wall",
                    dyeItemId,
                    "polished_soapstone_wall",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_soapstone_brick_slab_dye_recipe",
                    "${dyeName}_soapstone_brick_slab",
                    "arts_and_crafts:soapstone_brick_slab",
                    dyeItemId,
                    "soapstone_brick_slab",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_soapstone_brick_stairs_dye_recipe",
                    "${dyeName}_soapstone_brick_stairs",
                    "arts_and_crafts:soapstone_brick_stairs",
                    dyeItemId,
                    "soapstone_brick_stairs",
                )
            out +=
                shapedDyeRecipe(
                    "${dyeName}_soapstone_brick_wall_dye_recipe",
                    "${dyeName}_soapstone_brick_wall",
                    "arts_and_crafts:soapstone_brick_wall",
                    dyeItemId,
                    "soapstone_brick_wall",
                )

            out +=
                polished to
                """{"type":"minecraft:crafting_shaped","category":"building","key":{"K":{"item":"$mod:$soap"}},"pattern":["KK","KK"],"result":{"count":4,"id":"$mod:$polished"}}"""
            out +=
                bricks to
                """{"type":"minecraft:crafting_shaped","category":"building","key":{"K":{"item":"$mod:$polished"}},"pattern":["KK","KK"],"result":{"count":4,"id":"$mod:$bricks"}}"""

            out += slabCraftRecipe("${dyeName}_soapstone_slab", soap)
            out += stairsCraftRecipe("${dyeName}_soapstone_stairs", soap)
            out += wallCraftRecipe("${dyeName}_soapstone_wall", soap)

            out += slabCraftRecipe("${dyeName}_polished_soapstone_slab", polished)
            out += stairsCraftRecipe("${dyeName}_polished_soapstone_stairs", polished)
            out += wallCraftRecipe("${dyeName}_polished_soapstone_wall", polished)

            out += slabCraftRecipe("${dyeName}_soapstone_brick_slab", bricks)
            out += stairsCraftRecipe("${dyeName}_soapstone_brick_stairs", bricks)
            out += wallCraftRecipe("${dyeName}_soapstone_brick_wall", bricks)
            return out
        }

        private fun flowerPotItemAlternatives(excludeDyeName: String): String {
            val vanilla =
                VANILLA_DYES
                    .filter { it.serializedName != excludeDyeName }
                    .map { """{"item":"arts_and_crafts:${it.serializedName}_flower_pot"}""" }
            val depot =
                DEPOT_DYES
                    .filter { it != excludeDyeName }
                    .map { """{"item":"${Constants.MOD_ID}:${it}_flower_pot"}""" }
            return (vanilla + depot).joinToString(",")
        }

        private fun flowerPotRecipes(
            dyeName: String,
            dyeItemId: String,
        ): List<Pair<String, String>> {
            val mod = Constants.MOD_ID
            val flowerPotAlts = flowerPotItemAlternatives(dyeName)
            return listOf(
                "dye_${dyeName}_flower_pot" to
                    """
                    {"type":"minecraft:crafting_shapeless","category":"building","group":"flower_pots","ingredients":[{"item":"$dyeItemId"},[$flowerPotAlts]],"result":{"count":1,"id":"$mod:${dyeName}_flower_pot"}}
                    """.trimIndent(),
                "dye_${dyeName}_flower_pot_with_bleached_flower_pots" to
                    """
                    {"type":"minecraft:crafting_shapeless","category":"building","group":"flower_pots","ingredients":[{"item":"$dyeItemId"},{"item":"minecraft:flower_pot"}],"result":{"count":1,"id":"$mod:${dyeName}_flower_pot"}}
                    """.trimIndent(),
            )
        }

        private fun chalkItemAlternatives(suffix: String): String {
            val vanilla = VANILLA_DYES.map { """{"item":"arts_and_crafts:${it.serializedName}_$suffix"}""" }
            val depot = DEPOT_DYES.map { """{"item":"${Constants.MOD_ID}:${it}_$suffix"}""" }
            val bleached = if (suffix == "chalk") """{"item":"arts_and_crafts:bleached_chalk"}""" else null
            return (vanilla + depot + listOfNotNull(bleached)).distinct().joinToString(",")
        }

        private fun chalkRecipes(
            dyeName: String,
            dyeItemId: String,
        ): List<Pair<String, String>> {
            val mod = Constants.MOD_ID
            val chalkAlts = chalkItemAlternatives("chalk")
            val stickAlts = chalkItemAlternatives("chalk_stick")
            return listOf(
                "dye_${dyeName}_chalk" to
                    """
                    {"type":"minecraft:crafting_shapeless","category":"building","group":"chalk","ingredients":[{"item":"$dyeItemId"},[$chalkAlts]],"result":{"count":1,"id":"$mod:${dyeName}_chalk"}}
                    """.trimIndent(),
                "dye_${dyeName}_chalk_stick" to
                    """
                    {"type":"minecraft:crafting_shapeless","category":"building","group":"chalk_sticks","ingredients":[{"item":"$dyeItemId"},[$stickAlts]],"result":{"count":1,"id":"$mod:${dyeName}_chalk_stick"}}
                    """.trimIndent(),
                "${dyeName}_paintbrush" to
                    """
                    {"type":"minecraft:crafting_shaped","category":"equipment","group":"paintbrush","key":{"K":{"item":"$dyeItemId"},"Q":{"item":"minecraft:brush"}},"pattern":["KK","KQ"],"result":{"count":1,"id":"$mod:${dyeName}_paintbrush"}}
                    """.trimIndent(),
                "stonecutting/${dyeName}_chalk_stick_from_${dyeName}_chalk_stonecutting" to
                    """
                    {"type":"minecraft:stonecutting","ingredient":{"item":"$mod:${dyeName}_chalk"},"result":{"count":1,"id":"$mod:${dyeName}_chalk_stick"}}
                    """.trimIndent(),
            )
        }

        private val CHALK_DUST_PATTERN_SUFFIXES =
            listOf(
                "dot",
                "vertical_line_middle",
                "rotated_line_middle",
                "corner_0",
                "corner_1",
                "corner_2",
                "corner_3",
                "t_cross_0",
                "t_cross_1",
                "t_cross_2",
                "t_cross_3",
                "plus",
                "diagonal_line_right",
                "diagonal_line_left",
                "x",
                "line_edge_0",
                "line_edge_1",
                "line_edge_2",
                "line_edge_3",
                "triangle_0",
                "triangle_1",
                "triangle_2",
                "triangle_3",
                "circle",
                "square",
                "block_corner_0",
                "block_corner_1",
                "block_corner_2",
                "block_corner_3",
                "arch_0",
                "arch_1",
                "arch_2",
                "arch_3",
            )

        /**
         * Arts & Crafts paintbrush palette id -> dyed block suffix registered by DyedArtsAndCrafts.
         * Keys must match files under arts_and_crafts/paintbrush_palette/ in Arts-and-Crafts.
         */
        private val PAINTBRUSH_PALETTE_BLOCK_SUFFIX =
            mapOf(
                "mud_bricks" to "mud_bricks",
                "mud_brick_slab" to "mud_brick_slab",
                "mud_brick_stairs" to "mud_brick_stairs",
                "mud_brick_wall" to "mud_brick_wall",
                "terracotta_shingles" to "terracotta_shingles",
                "terracotta_shingle_slab" to "terracotta_shingle_slab",
                "terracotta_shingle_stairs" to "terracotta_shingle_stairs",
                "terracotta_shingle_wall" to "terracotta_shingle_wall",
                "soapstone" to "soapstone",
                "soapstone_slab" to "soapstone_slab",
                "soapstone_stairs" to "soapstone_stairs",
                "soapstone_wall" to "soapstone_wall",
                "polished_soapstone" to "polished_soapstone",
                "polished_soapstone_slab" to "polished_soapstone_slab",
                "polished_soapstone_stairs" to "polished_soapstone_stairs",
                "polished_soapstone_wall" to "polished_soapstone_wall",
                "soapstone_bricks" to "soapstone_bricks",
                "soapstone_brick_slab" to "soapstone_brick_slab",
                "soapstone_brick_stairs" to "soapstone_brick_stairs",
                "soapstone_brick_wall" to "soapstone_brick_wall",
                "plaster" to "plaster",
                "flower_pot" to "flower_pot",
                "chalk" to "chalk",
                "chalk_dust" to "chalk_dust",
            )

        private const val PALETTE_CLASSPATH_PREFIX =
            "data/arts_and_crafts/arts_and_crafts/paintbrush_palette/"

        private fun artsAndCraftsPaletteSourceFile(
            paletteName: String,
            dataRoot: Path,
        ): Path? {
            val fileName = "$paletteName.json"
            val relativeTail =
                listOf(
                    "common",
                    "src",
                    "main",
                    "resources",
                    "data",
                    "arts_and_crafts",
                    "arts_and_crafts",
                    "paintbrush_palette",
                    fileName,
                )
            // dataRoot -> .../src/generated/resources/data; project dir is four levels up.
            val projectDir = dataRoot.parent.parent.parent.parent
            val modsDir = projectDir.parent
            val candidates =
                listOf(
                    modsDir.resolve("Arts-and-Crafts").resolve(relativeTail.joinToString("/")),
                    projectDir.resolve("../Arts-and-Crafts").resolve(relativeTail.joinToString("/")),
                    Path.of("..", "Arts-and-Crafts", *relativeTail.toTypedArray()),
                    Path.of("Arts-and-Crafts", *relativeTail.toTypedArray()),
                    Path.of("mods", "Arts-and-Crafts", *relativeTail.toTypedArray()),
                ).map { it.toAbsolutePath().normalize() }
            return candidates.firstOrNull { Files.isRegularFile(it) }
        }

        private fun loadArtsAndCraftsPalette(
            paletteName: String,
            dataRoot: Path,
        ): JsonObject? {
            artsAndCraftsPaletteSourceFile(paletteName, dataRoot)?.let { sourceFile ->
                return JsonParser.parseString(Files.readString(sourceFile)).asJsonObject
            }
            val resourcePath = "$PALETTE_CLASSPATH_PREFIX$paletteName.json"
            ArtsAndCraftsAssetProvider::class.java.getResourceAsStream(resourcePath)?.use { stream ->
                InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                    return JsonParser.parseReader(reader).asJsonObject
                }
            }
            return null
        }

        private fun generatePaintbrushPalettes(
            cachedOutput: CachedOutput,
            dataRoot: Path,
            dyeNames: List<String>,
        ): List<CompletableFuture<*>> {
            val futures = mutableListOf<CompletableFuture<*>>()
            val mod = Constants.MOD_ID

            for ((paletteName, blockSuffix) in PAINTBRUSH_PALETTE_BLOCK_SUFFIX) {
                val palette = loadArtsAndCraftsPalette(paletteName, dataRoot) ?: continue
                val mappings = palette.getAsJsonObject("mappings")
                for (dyeName in dyeNames) {
                    mappings.addProperty(
                        "$mod:${dyeName}_paintbrush",
                        "$mod:${dyeName}_$blockSuffix",
                    )
                }

                futures.add(
                    DataProvider.saveStable(
                        cachedOutput,
                        palette,
                        dataRoot
                            .resolve(
                                ARTS_AND_CRAFTS,
                            ).resolve("arts_and_crafts")
                            .resolve("paintbrush_palette")
                            .resolve("$paletteName.json"),
                    ),
                )
            }
            return futures
        }

        private fun generatePaintableTagExtensions(
            cachedOutput: CachedOutput,
            dataRoot: Path,
            dyeNames: List<String>,
        ): List<CompletableFuture<*>> {
            val futures = mutableListOf<CompletableFuture<*>>()
            val mod = Constants.MOD_ID

            for ((tagName, blockSuffix) in PAINTBRUSH_PALETTE_BLOCK_SUFFIX) {
                val values = dyeNames.joinToString(",") { dyeName -> "\"$mod:${dyeName}_$blockSuffix\"" }
                val tagJson = """{"replace":false,"values":[$values]}"""
                futures.add(
                    DataProvider.saveStable(
                        cachedOutput,
                        JsonParser.parseString(tagJson),
                        dataRoot
                            .resolve(ARTS_AND_CRAFTS)
                            .resolve("tags")
                            .resolve("block")
                            .resolve("paintable")
                            .resolve("$tagName.json"),
                    ),
                )
            }
            return futures
        }

        private fun generatePaintbrushItemTagExtension(
            cachedOutput: CachedOutput,
            dataRoot: Path,
            dyeNames: List<String>,
        ): List<CompletableFuture<*>> {
            val mod = Constants.MOD_ID
            val values = dyeNames.joinToString(",") { dyeName -> "\"$mod:${dyeName}_paintbrush\"" }
            val tagJson = """{"replace":false,"values":[$values]}"""
            return listOf(
                DataProvider.saveStable(
                    cachedOutput,
                    JsonParser.parseString(tagJson),
                    dataRoot
                        .resolve(ARTS_AND_CRAFTS)
                        .resolve("tags")
                        .resolve("item")
                        .resolve("paintbrushes.json"),
                ),
            )
        }

        private fun generateLang(
            cachedOutput: CachedOutput,
            resourceRoot: Path,
            dyeNames: List<String>,
        ): CompletableFuture<*> {
            val langPath =
                resourceRoot
                    .resolve(Constants.MOD_ID)
                    .resolve("lang")
                    .resolve("en_us.json")
            val lang =
                if (Files.isRegularFile(langPath)) {
                    JsonParser.parseString(Files.readString(langPath)).asJsonObject
                } else {
                    JsonObject()
                }

            for (dyeName in dyeNames) {
                val dyeTitle = titleCase(dyeName)
                lang.addProperty("block.${Constants.MOD_ID}.${dyeName}_chalk", "$dyeTitle Chalk")
                lang.addProperty("block.${Constants.MOD_ID}.${dyeName}_chalk_dust", "$dyeTitle Chalk Dust")
                lang.addProperty("item.${Constants.MOD_ID}.${dyeName}_chalk_stick", "$dyeTitle Chalk Stick")
                lang.addProperty("item.${Constants.MOD_ID}.${dyeName}_paintbrush", "$dyeTitle Paintbrush")
                lang.addProperty("block.${Constants.MOD_ID}.${dyeName}_plaster", "$dyeTitle Plaster")
                lang.addProperty("block.${Constants.MOD_ID}.${dyeName}_flower_pot", "$dyeTitle Flower Pot")

                for (blockType in BLOCK_TYPES) {
                    val cubeTitle = titleCase(blockType.cubeName)
                    val baseTitle = titleCase(blockType.baseName)
                    lang.addProperty(
                        "block.${Constants.MOD_ID}.${dyeName}_${blockType.cubeName}",
                        "$dyeTitle $cubeTitle",
                    )
                    if (blockType.hasSlab) {
                        lang.addProperty(
                            "block.${Constants.MOD_ID}.${dyeName}_${blockType.baseName}_slab",
                            "$dyeTitle $baseTitle Slab",
                        )
                    }
                    if (blockType.hasStairs) {
                        lang.addProperty(
                            "block.${Constants.MOD_ID}.${dyeName}_${blockType.baseName}_stairs",
                            "$dyeTitle $baseTitle Stairs",
                        )
                    }
                    if (blockType.hasWall) {
                        lang.addProperty(
                            "block.${Constants.MOD_ID}.${dyeName}_${blockType.baseName}_wall",
                            "$dyeTitle $baseTitle Wall",
                        )
                    }
                }
            }

            return DataProvider.saveStable(cachedOutput, lang, langPath)
        }

        private fun titleCase(name: String): String =
            name
                .split("_")
                .joinToString(" ") { part ->
                    part.replaceFirstChar { char -> char.uppercase() }
                }

        val TYPE =
            ProviderType.registerProvider("Arts and Crafts assets") { context ->
                ArtsAndCraftsAssetProvider(context.output)
            }
    }
}
