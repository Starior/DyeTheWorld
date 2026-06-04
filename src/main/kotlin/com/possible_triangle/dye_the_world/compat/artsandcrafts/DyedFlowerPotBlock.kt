package com.possible_triangle.dye_the_world.compat.artsandcrafts

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class DyedFlowerPotBlock(properties: Properties) : Block(properties) {
    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPE

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun isPathfindable(
        state: BlockState,
        pathComputationType: PathComputationType,
    ): Boolean = false

    companion object {
        private val SHAPE = box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0)
    }
}
