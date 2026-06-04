package com.possible_triangle.dye_the_world.compat.artsandcrafts

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class DyedFlowerPotBlock(
    private val dyeName: String,
    private val contentId: ResourceLocation?,
    properties: Properties,
) : Block(properties) {
    init {
        if (contentId == null) {
            EMPTY_POTS[dyeName] = this
        } else {
            POTTED_BLOCKS.getOrPut(dyeName, ::hashMapOf)[contentId] = this
        }
    }

    private fun isEmpty() = contentId == null

    private fun contentBlock(): Block = contentId?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(Blocks.AIR) } ?: Blocks.AIR

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult,
    ): ItemInteractionResult {
        val plantId = (stack.item as? BlockItem)?.block?.let { BuiltInRegistries.BLOCK.getKey(it) }
        val pottedBlock = plantId?.let { POTTED_BLOCKS[dyeName]?.get(it) } ?: Blocks.AIR

        if (!level.isClientSide && tryPaint(state, level, pos, player, stack, hand)) {
            return ItemInteractionResult.SUCCESS
        }

        val pottedState = pottedBlock.defaultBlockState()
        return when {
            pottedState.isAir -> {
                ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            }

            !isEmpty() -> {
                ItemInteractionResult.CONSUME
            }

            else -> {
                level.setBlock(pos, pottedState, 3)
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos)
                player.awardStat(Stats.POT_FLOWER)
                stack.consume(1, player)
                ItemInteractionResult.sidedSuccess(level.isClientSide)
            }
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult,
    ): InteractionResult {
        if (isEmpty()) return InteractionResult.CONSUME

        val stack = ItemStack(contentBlock())
        if (!player.addItem(stack)) {
            player.drop(stack, false)
        }

        level.setBlock(pos, EMPTY_POTS[dyeName]?.defaultBlockState() ?: Blocks.AIR.defaultBlockState(), 3)
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPE

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getCloneItemStack(
        level: LevelReader,
        pos: BlockPos,
        state: BlockState,
    ): ItemStack =
        if (isEmpty()) {
            super.getCloneItemStack(level, pos, state)
        } else {
            ItemStack(contentBlock())
        }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos,
    ): BlockState {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState()
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
    }

    override fun isPathfindable(
        state: BlockState,
        pathComputationType: PathComputationType,
    ): Boolean = false

    companion object {
        private val SHAPE = box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0)
        private val EMPTY_POTS = hashMapOf<String, Block>()
        private val POTTED_BLOCKS = hashMapOf<String, HashMap<ResourceLocation, Block>>()

        private val PAINTBRUSH_ITEM_CLASS by lazy {
            Class.forName("com.kekecreations.arts_and_crafts.common.item.PaintbrushItem")
        }
        private val PAINTBRUSH_UTILS_CLASS by lazy {
            Class.forName("com.kekecreations.arts_and_crafts.common.util.PaintbrushUtils")
        }
        private val GET_FINAL_BLOCK by lazy {
            PAINTBRUSH_UTILS_CLASS.methods.single { it.name == "getFinalBlock" && it.parameterCount == 3 }
        }
        private val PAINT_BLOCK by lazy {
            PAINTBRUSH_UTILS_CLASS.methods.single { it.name == "paintBlock" && it.parameterCount == 6 }
        }

        private fun tryPaint(
            state: BlockState,
            level: Level,
            pos: BlockPos,
            player: Player,
            stack: ItemStack,
            hand: InteractionHand,
        ): Boolean {
            if (!PAINTBRUSH_ITEM_CLASS.isInstance(stack.item)) return false
            val finalBlock = GET_FINAL_BLOCK.invoke(null, level.registryAccess(), state, stack) as? Block ?: return false
            if (finalBlock == state.block) return false
            PAINT_BLOCK.invoke(null, level, finalBlock.defaultBlockState(), pos, player, stack, hand)
            return true
        }
    }
}
