package com.rwtema.extrautils2.quarry;

import com.buuz135.industrial.api.recipe.LaserDrillEntry;
import com.buuz135.industrial.utils.ItemStackWeightedItem;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.rwtema.extrautils2.compatibility.StackHelper;
import com.rwtema.extrautils2.dimensions.workhousedim.WorldProviderSpecialDim;
import com.rwtema.extrautils2.eventhandlers.ItemCaptureHandler;
import com.rwtema.extrautils2.eventhandlers.XPCaptureHandler;
import com.rwtema.extrautils2.fakeplayer.XUFakePlayer;
import com.rwtema.extrautils2.gui.backend.*;
import com.rwtema.extrautils2.itemhandler.InventoryHelper;
import com.rwtema.extrautils2.itemhandler.SingleStackHandler;
import com.rwtema.extrautils2.itemhandler.SingleStackHandlerFilter;
import com.rwtema.extrautils2.itemhandler.StackDump;
import com.rwtema.extrautils2.items.ItemBiomeMarker;
import com.rwtema.extrautils2.items.ItemIngredients;
import com.rwtema.extrautils2.network.XUPacketBuffer;
import com.rwtema.extrautils2.power.energy.XUEnergyStorage;
import com.rwtema.extrautils2.tile.RedstoneState;
import com.rwtema.extrautils2.tile.TilePower;
import com.rwtema.extrautils2.utils.CapGetter;
import com.rwtema.extrautils2.utils.Lang;
import com.rwtema.extrautils2.utils.datastructures.ListRandomOffset;
import com.rwtema.extrautils2.utils.datastructures.NBTSerializable;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.WeightedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TileQuarry extends TilePower implements ITickable, IDynamicHandler {

	private static final Int2ObjectMap<ListMultimap<Biome, ItemStackWeightedItem>> BIOME_DEPTH_ORE_CACHE = new Int2ObjectOpenHashMap<>();
	static final ArrayList<BlockPos> offset = new ArrayList<>();
	static final HashMap<BlockPos, ArrayList<EnumFacing>> offset_sides = new HashMap<>();
	private final static ItemStack genericDigger = new ItemStack(Items.DIAMOND_PICKAXE, 1);
	public static int ENERGY_PER_OPERATION = 56000;

	static {
		BlockPos origin = BlockPos.ORIGIN;
		for (EnumFacing facing1 : EnumFacing.values()) {
			BlockPos offset1 = origin.offset(facing1);
			for (EnumFacing facing2 : EnumFacing.values()) {
				if (facing1 != facing2.getOpposite()) {
					BlockPos offset2 = offset1.offset(facing2);
					if (!offset.contains(offset2))
						offset.add(offset2);
					offset_sides.computeIfAbsent(offset2, t -> new ArrayList<>()).add(facing2.getOpposite());
				}
			}
		}
	}

	private final StackDump extraStacks = registerNBT("extrastacks", new StackDump());
	public boolean redstoneDirty = true;
	public boolean redstoneActive;
	protected SingleStackHandlerFilter.ItemFilter filter = registerNBT("filter", new SingleStackHandlerFilter.ItemFilter() {
		@Override
		protected void onContentsChanged() {
			markDirty();
		}
	});
	Biome lastBiome = null;
	XUEnergyStorage energy = registerNBT("energy", new XUEnergyStorage(ENERGY_PER_OPERATION * 10));
	ChunkPos posKey = null;
	ChunkPos chunkPos = null;
	NBTSerializable.Int curBlockLocation = registerNBT("location", new NBTSerializable.Int(0));
	NBTSerializable.Long blocksMined = registerNBT("mined", new NBTSerializable.Long());
	boolean needsToCheckNearbyBlocks = true;
	boolean hasNearbyBlocks = false;
	private int currentDepth = 0;

	private ItemBiomeMarker.ItemBiomeHandler biomeHandler = registerNBT("biome_marker", new ItemBiomeMarker.ItemBiomeHandler() {
		@Override
		protected void onContentsChanged() {
			Biome biome = getBiome();
			if (biome != null && lastBiome != null && biome != lastBiome && chunkPos != null && posKey != null) {
				lastBiome = biome;
			}
			markDirty();
		}
	});

	protected NBTSerializable.Int minDepth = registerNBT("min_depth", new NBTSerializable.Int());
	protected NBTSerializable.Int maxDepth = registerNBT("max_depth", new NBTSerializable.Int());
	private NBTSerializable.NBTEnum<RedstoneState> redstone_state = registerNBT("redstone_state", new NBTSerializable.NBTEnum<>(RedstoneState.OPERATE_ALWAYS));

	@Override
	protected Iterable<ItemStack> getDropHandler() {
		return Iterables.concat(
				InventoryHelper.getItemHandlerIterator(filter),
				InventoryHelper.getItemHandlerIterator(biomeHandler)
		);
	}

	@Override
	public float getPower() {
		return Float.NaN;
	}

	@Override
	public void onPowerChanged() {

	}

	public boolean hasNearbyBlocks() {
		if (needsToCheckNearbyBlocks || world.isRemote) {
			needsToCheckNearbyBlocks = false;
			hasNearbyBlocks = true;
			for (EnumFacing facing : EnumFacing.values()) {
				TileEntity tileEntity = world.getTileEntity(pos.offset(facing));

				if (!(tileEntity instanceof TileQuarryProxy) || ((TileQuarryProxy) tileEntity).facing.value != facing.getOpposite()) {
					hasNearbyBlocks = false;
					break;
				}
			}
		}
		return hasNearbyBlocks;
	}

	@Override
	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, ItemStack heldItem, EnumFacing side, float hitX, float hitY, float hitZ) {
		return hasNearbyBlocks() && super.onBlockActivated(worldIn, pos, state, playerIn, hand, heldItem, side, hitX, hitY, hitZ);
	}

	@Override
	public void update() {
		if (world.isRemote) {
			return;
		}

		if (world.getTotalWorldTime() % 200 != 0) {
			return;
		}

		if (!hasNearbyBlocks()) return;

		if (redstoneDirty) {
			redstoneDirty = false;
			redstoneActive = false;

			for (EnumFacing facing : EnumFacing.values()) {
				TileEntity tileEntity = world.getTileEntity(pos.offset(facing));

				if ((tileEntity instanceof TileQuarryProxy) && ((TileQuarryProxy) tileEntity).facing.value == facing.getOpposite() && ((TileQuarryProxy) tileEntity).isPowered()) {
					redstoneActive = true;
					break;
				}
			}
		}

		if (!redstone_state.value.acceptableValue(redstoneActive)) return;

		// Pick a random ore to "mine" (totally based on RNG)
		Biome biome = biomeHandler.getBiome();
		currentDepth = ThreadLocalRandom.current().nextInt(Math.min(minDepth.value, maxDepth.value), Math.max(minDepth.value, maxDepth.value) + 1);
		ListMultimap<Biome, ItemStackWeightedItem> resources = BIOME_DEPTH_ORE_CACHE.computeIfAbsent(currentDepth, i -> ArrayListMultimap.create());

		if (!resources.containsKey(biome)) {
			LaserDrillEntry.LASER_DRILL_ENTRIES[currentDepth].stream()
					.filter(entry -> entry.getWhitelist().isEmpty() || entry.getWhitelist().contains(biome))
					.filter(entry -> !entry.getBlacklist().contains(biome))
					.forEach(entry -> resources.put(biome, new ItemStackWeightedItem(entry.getStack(), entry.getWeight())));
		}

		List<ItemStackWeightedItem> biomeResources = resources.get(biome);

		if (biomeResources.size() < 1) {
			return;
		}

		ItemStack item = WeightedRandom.getRandomItem(world.rand, biomeResources).getStack().copy();
		blocksMined.value++;

		if (filter.matches(item)) {
			extraStacks.addStack(item);
		}

		energy.extractEnergy(ENERGY_PER_OPERATION, false);

		if (!extraStacks.stacks.isEmpty()) {
			for (BlockPos offset_pos : new ListRandomOffset<>(offset)) {
				TileEntity tile = world.getTileEntity(pos.add(offset_pos));
				if (tile != null) {
					for (EnumFacing facing : new ListRandomOffset<>(offset_sides.get(offset_pos))) {
						IItemHandler handler = CapGetter.ItemHandler.getInterface(tile, facing);
						if (handler != null) {
							extraStacks.attemptDump(handler);
						}
					}
				}
			}
		}
	}

	@Override
	public void onNeighborBlockChange(World worldIn, BlockPos pos, IBlockState state, Block neighborBlock) {
		super.onNeighborBlockChange(worldIn, pos, state, neighborBlock);
		needsToCheckNearbyBlocks = true;
	}

	@Override
	public DynamicContainer getDynamicContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerQuarry(player);
	}

	public class ContainerQuarry extends DynamicContainerTile {

		public ContainerQuarry(EntityPlayer player) {
			super(TileQuarry.this);
			addTitle("Quantum Quarry");
			addWidget(new SingleStackHandlerFilter.WidgetSlotFilter(filter, 4, 15) {
				@Override
				@SideOnly(Side.CLIENT)
				public List<String> getToolTip() {
					if (!getHasStack()) {
						return Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(Lang.translate("If present, the quarry will auto-destroy any items that do NOT match the filter."), 120);
					}
					return null;
				}
			});

			addWidget(biomeHandler.getSlot(24, 15));

			addWidget(new WidgetScrollBarServer(4, 4 + 9 + 18 + 4, 70, 0, 255) {

				@Override
				public int getValueServer() {
					return TileQuarry.this.minDepth.value;
				}

				@Override
				public void setValueServer(int level) {
					TileQuarry.this.minDepth.value = level;
				}

				@Override
				public List<String> getToolTip() {
					return ImmutableList.of(Lang.translateArgs("Minimum Depth: %s", scrollValue));
				}
			});

			addWidget(new WidgetScrollBarServer(24, 4 + 9 + 18 + 4, 70, 0, 255) {

				@Override
				public int getValueServer() {
					return TileQuarry.this.maxDepth.value;
				}

				@Override
				public void setValueServer(int level) {
					TileQuarry.this.maxDepth.value = level;
				}

				@Override
				public List<String> getToolTip() {
					return ImmutableList.of(Lang.translateArgs("Maximum Depth: %s", scrollValue));
				}
			});

			addWidget(new WidgetEnergyStorage(DynamicContainer.playerInvWidth - 18 - 4, 15, energy));

			addWidget(RedstoneState.getRSWidget(44, 15, redstone_state));

			crop();

			addWidget(new WidgetTextData(4 + 18 + 24, 36, (DynamicContainer.playerInvWidth - 18 * 2 - 16), 54, 1, 4210752) {
				@Override
				public void addToDescription(XUPacketBuffer packet) {
					packet.writeInt(TileQuarry.this.currentDepth);
					packet.writeLong(TileQuarry.this.blocksMined.value);
					packet.writeInt(lastBiome != null ? Biome.REGISTRY.getIDForObject(lastBiome) : -1);
				}

				@Override
				protected String constructText(XUPacketBuffer packet) {
					int depth = packet.readInt();
					long mined = packet.readLong();
					int biomeID = packet.readInt();
					Biome biome = biomeID != -1 ? Biome.REGISTRY.getObjectById(biomeID) : null;

					return Lang.translateArgs("Depth: %s", depth) + "\n" + Lang.translateArgs("Blocks Mined: %s", mined)
							+ ((biome != null) ? ("\n" + Lang.translateArgs("Biome: %s", biome.getBiomeName())) : "");
				}
			});


			cropAndAddPlayerSlots(player.inventory);
			validate();
		}
	}
}
