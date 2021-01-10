package com.rwtema.extrautils2.quarry.enderquarry;

import com.rwtema.extrautils2.backend.XUBlockStaticRotation;
import com.rwtema.extrautils2.backend.model.BoxModel;
import com.rwtema.extrautils2.utils.Lang;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.List;

public class BlockEnderQuarry extends XUBlockStaticRotation {

    public BlockEnderQuarry() {
        super(Material.ROCK);
        setLightLevel(1);
    }

    @Override
    protected BoxModel createBaseModel(IBlockState baseState) {
        BoxModel model = BoxModel.newStandardBlock("ender_quarry_bottom");
        model.setTextures(2, "ender_quarry");
        model.setTextures(3, "ender_quarry");
        model.setTextures(4, "ender_quarry");
        model.setTextures(5, "ender_quarry");
        return model;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer playerIn, List<String> tooltip, boolean advanced) {
        tooltip.add(Lang.translate("A lightning fast, lag-free quarry that runs on RF."));
        tooltip.add(Lang.translate("Automatically pumps any fluids it comes across."));
    }

    @Override
    public boolean hasTileEntity(@Nonnull IBlockState state) {
        return true;
    }

    @Override
    @Nonnull
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new TileEnderQuarry();
    }
}
