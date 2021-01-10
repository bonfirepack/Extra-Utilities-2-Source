package com.rwtema.extrautils2.quarry.enderquarry;

import com.google.common.collect.ImmutableList;
import com.rwtema.extrautils2.gui.backend.DynamicContainer;
import com.rwtema.extrautils2.gui.backend.DynamicContainerTile;
import com.rwtema.extrautils2.gui.backend.DynamicGui;
import com.rwtema.extrautils2.gui.backend.WidgetEnergyStorage;
import com.rwtema.extrautils2.gui.backend.WidgetSlotItemHandler;
import com.rwtema.extrautils2.gui.backend.WidgetTextData;
import com.rwtema.extrautils2.itemhandler.SingleStackHandlerFilter;
import com.rwtema.extrautils2.items.ItemIngredients;
import com.rwtema.extrautils2.network.XUPacketBuffer;
import com.rwtema.extrautils2.tile.RedstoneState;
import com.rwtema.extrautils2.utils.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ContainerEnderQuarry extends DynamicContainerTile {

    public ContainerEnderQuarry(TileEnderQuarry quarry, EntityPlayer player) {
        super(quarry);
        addTitle("Ender Quarry");
        addWidget(new SingleStackHandlerFilter.WidgetSlotFilter(quarry.filter, 4, 20) {

            @Override
            @SideOnly(Side.CLIENT)
            public List<String> getToolTip() {
                if (!getHasStack()) { // TODO actually use the filter
                    return Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(Lang.translate("If present, the quarry will auto-destroy any items that do NOT match the filter."), 120);
                }

                return null;
            }
        });
        addWidget(new WidgetSlotItemHandler(quarry.enchants, 0, 4, 20 + 18) {
            @Override
            @SideOnly(Side.CLIENT)
            public List<String> getToolTip() {
                if (!getHasStack()) {
                    return ImmutableList.of(Lang.translate("Enchanted Book"));
                }
                return null;
            }

            @Override
            @SideOnly(Side.CLIENT)
            public void renderBackground(TextureManager manager, DynamicGui gui, int guiLeft, int guiTop) {
                super.renderBackground(manager, gui, guiLeft, guiTop);
                if (!getHasStack()) {
                    ItemStack stack = ItemIngredients.Type.ENCHANTED_BOOK_SKELETON.newStack();
                    gui.renderStack(stack, guiLeft + getX() + 1, guiTop + getY() + 1, "");
                }
            }
        });
        addWidget(new WidgetSlotItemHandler(quarry.enchants, 0, 4, 20 + 18) {
            @Override
            @SideOnly(Side.CLIENT)
            public List<String> getToolTip() {
                if (!getHasStack()) {
                    return ImmutableList.of(Lang.translate("Enchanted Book"));
                }
                return null;
            }

            @Override
            @SideOnly(Side.CLIENT)
            public void renderBackground(TextureManager manager, DynamicGui gui, int guiLeft, int guiTop) {
                super.renderBackground(manager, gui, guiLeft, guiTop);
                if (!getHasStack()) {
                    ItemStack stack = ItemIngredients.Type.ENCHANTED_BOOK_SKELETON.newStack();
                    gui.renderStack(stack, guiLeft + getX() + 1, guiTop + getY() + 1, "");
                }
            }
        });
        addWidget(new WidgetEnergyStorage(DynamicContainer.playerInvWidth - 18 - 4, 20, quarry.getEnergyHandler(null)));

        addWidget(RedstoneState.getRSWidget(4, 20 + 37 + 18, quarry.redstoneState));

        crop();
        addWidget(new WidgetTextData(4 + 18 + 4, 20, (DynamicContainer.playerInvWidth - 18 * 2 - 16), 54, 1, 4210752) {

            @Override
            public void addToDescription(XUPacketBuffer packet) {
                packet.writeInt((int) quarry.progress);
                packet.writeString(quarry.status);
                // packet.writeLong(TileQuarry.this.blocksMined.value);
                // packet.writeInt(lastBiome != null ? Biome.REGISTRY.getIDForObject(lastBiome) : -1);
            }

            @Override
            protected String constructText(XUPacketBuffer packet) {
                int y = packet.readInt();
                    /*long mined = packet.readLong();
                    int biomeID = packet.readInt();
                    Biome biome = biomeID != -1 ? Biome.REGISTRY.getObjectById(biomeID) : null;*/

                return Lang.translateArgs("Scanned: %s", y) + "\n" + Lang.translateArgs("Status: %s", packet.readString());
                /*+ ((biome != null) ? ("\n" + Lang.translateArgs("Biome: %s", biome.getBiomeName())) : "");*/
            }
        });


        cropAndAddPlayerSlots(player.inventory);
        validate();
    }
}
