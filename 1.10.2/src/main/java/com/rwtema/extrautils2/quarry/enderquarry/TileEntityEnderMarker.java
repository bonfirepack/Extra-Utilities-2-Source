package com.rwtema.extrautils2.quarry.enderquarry;

import com.rwtema.extrautils2.tile.XUTile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class TileEntityEnderMarker extends XUTile implements ITickable, IChunkLoad {

    public static List<int[]> MARKERS = new ArrayList<>();
    public boolean init;

    public TileEntityEnderMarker() {
        this.init = false;
    }

    public static int[] getCoord(final TileEntity tile) {
        return new int[]{tile.getWorld().provider.getDimension(), tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ()};
    }

    public int[] getCoord() {
        return getCoord(this);
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newSate) {
        return oldState == newSate;
    }

    @Override
    public void update() {
        if (!init) {
            onChunkLoad();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();

        if (world.isRemote) {
            return;
        }

        int[] myCoord = this.getCoord();
        List<int[]> toUpdate = new ArrayList<>();

        for (int i = 0; i < MARKERS.size(); ++i) {
            int[] coord = MARKERS.get(i);

            if (myCoord[0] == coord[0] && myCoord[2] == coord[2]) {
                if (myCoord[3] == coord[3] && myCoord[1] == coord[1]) {
                    MARKERS.remove(i);
                    --i;
                } else if (myCoord[3] == coord[3] || myCoord[1] == coord[1]) {
                    toUpdate.add(coord);
                }
            }
        }

        for (int[] coord : toUpdate) {
            TileEntity tile = world.getTileEntity(new BlockPos(coord[1], coord[2], coord[3]));

            if (tile instanceof TileEntityEnderMarker) {
                ((TileEntityEnderMarker) tile).recheck();
            }
        }
    }

    @Override
    public void onChunkLoad() {
        if (init) {
            return;
        }

        init = true;

        if (world == null || world.isRemote) {
            return;
        }

        int[] myCoord = getCoord();
        List<int[]> toUpdate = new ArrayList<>();

        for (int[] coord : MARKERS) {
            if (myCoord[0] == coord[0] && myCoord[2] == coord[2]) {
                if (myCoord[3] == coord[3] && myCoord[1] == coord[1]) {
                    return;
                }

                if (myCoord[3] != coord[3] && myCoord[1] != coord[1]) {
                    continue;
                }

                toUpdate.add(coord);
            }
        }

        MARKERS.add(myCoord);
        this.recheck();

        for (final int[] coord : toUpdate) {
            final TileEntity tile = world.getTileEntity(new BlockPos(coord[1], coord[2], coord[3]));

            if (tile instanceof TileEntityEnderMarker) {
                ((TileEntityEnderMarker) tile).recheck();
            }
        }
    }

    public void recheck() {
        int[] myCoord = this.getCoord();
        int flag = 0;

        for (int[] coord : MARKERS) {
            if (myCoord[0] == coord[0] && myCoord[2] == coord[2]) {
                if (myCoord[1] == coord[1] && myCoord[3] == coord[3]) {
                    continue;
                }

                if (myCoord[1] == coord[1]) {
                    flag |= ((myCoord[3] < coord[3]) ? 1 : 2);
                } else {
                    if (myCoord[3] != coord[3]) {
                        continue;
                    }

                    flag |= ((myCoord[1] < coord[1]) ? 4 : 8);
                }
            }
        }

        // world.setBlockState(pos, getBlockState().withProperty(BlockEnderMarker.FLAG, flag), 2);
    }
}
