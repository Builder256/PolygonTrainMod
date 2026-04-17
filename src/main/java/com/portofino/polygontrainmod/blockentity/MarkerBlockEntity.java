package com.portofino.polygontrainmod.blockentity;

import com.portofino.polygontrainmod.PolygonTrainModBlockEntities;
import com.portofino.polygontrainmod.block.MarkerBlock;
import com.portofino.polygontrainmod.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MarkerBlockEntity extends BlockEntity {
    public MarkerBlockEntity(BlockPos pos, BlockState state) {
        super(PolygonTrainModBlockEntities.MARKER.get(), pos, state);
    }

    public RailPosition getMarkerRP() {
        if (level == null) return null;
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof MarkerBlock)) return null;
        int facing = st.getValue(MarkerBlock.FACING);
        int dir = MarkerBlock.getMarkerDir(facing);
        boolean sw = ((MarkerBlock) st.getBlock()).isSwitch;
        return new RailPosition(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), dir, sw ? 1 : 0);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, MarkerBlockEntity be) {
    }
}
