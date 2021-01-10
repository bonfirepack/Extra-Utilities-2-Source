package com.rwtema.extrautils2.quarry.enderquarry;

import com.rwtema.extrautils2.backend.XUBlockStatic;
import com.rwtema.extrautils2.backend.model.BoxModel;
import com.rwtema.extrautils2.utils.Lang;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.List;

public class BlockEnderMarker extends XUBlockStatic {

    private static final int[] DX = new int[]{0, 0, 1, -1};
    private static final int[] DZ = new int[]{1, -1, 0, 0};

    public BlockEnderMarker() {
        super(Material.CIRCUITS);
        setLightLevel(1);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityEnderMarker();
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer playerIn, List<String> tooltip, boolean advanced) {
        tooltip.add(Lang.translate("Used to designate a mining zone for the Ender Quarry"));
        tooltip.add(Lang.translate("4 are required to make a cuboid area"));
    }

    @Override
    public BoxModel getModel(IBlockState state) {
        BoxModel model = new BoxModel();
        model.addBoxI(7, 0, 7, 9, 13, 9).setTextureSides(
                0, "ender_marker",
                1, "ender_marker",
                2, "ender_marker",
                3, "ender_marker",
                4, "ender_marker",
                5, "ender_marker"
        );
        return model;
    }

    /*
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        for (int i = 0; i < 4; ++i) {
            if ((stateIn.getValue(FLAG) & 1 << i) != 0x0) {
                for (int l = 0; l < 3; ++l) {
                    worldIn.spawnParticle(
                            EnumParticleTypes.REDSTONE,
                            pos.getX() + 0.5 + DX[i] * rand.nextDouble() * rand.nextDouble(),
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5 + DZ[i] * rand.nextDouble() * rand.nextDouble(),
                            0.501, 0.0, 1.0
                    );
                }
            }
        }
    }
     */
}
