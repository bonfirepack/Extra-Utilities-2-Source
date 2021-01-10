package com.rwtema.extrautils2.quarry.enderquarry;

import com.google.common.base.Throwables;
import com.rwtema.extrautils2.compatibility.StackHelper;
import com.rwtema.extrautils2.eventhandlers.ItemCaptureHandler;
import com.rwtema.extrautils2.eventhandlers.XPCaptureHandler;
import com.rwtema.extrautils2.fakeplayer.XUFakePlayer;
import com.rwtema.extrautils2.gui.backend.DynamicContainer;
import com.rwtema.extrautils2.gui.backend.IDynamicHandler;
import com.rwtema.extrautils2.itemhandler.SingleStackHandler;
import com.rwtema.extrautils2.itemhandler.SingleStackHandlerFilter;
import com.rwtema.extrautils2.itemhandler.StackDump;
import com.rwtema.extrautils2.power.energy.XUEnergyStorage;
import com.rwtema.extrautils2.quarry.BlockBreakingRegistry;
import com.rwtema.extrautils2.tile.RedstoneState;
import com.rwtema.extrautils2.tile.TilePower;
import com.rwtema.extrautils2.utils.CapGetter;
import com.rwtema.extrautils2.utils.datastructures.NBTSerializable;
import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Random;

public class TileEnderQuarry extends TilePower implements ITickable, IDynamicHandler {

    private final static ItemStack GENERIC_TOOL = new ItemStack(Items.DIAMOND_PICKAXE, 1);
    private static final Random RANDOM = new Random();
    private static final int BASE_POWER_DRAIN = 1800;
    private static final int HARDNESS_POWER_DRAIN = 200;

    private final XUEnergyStorage energy;
    private final StackDump extraStacks;
    private final QuarryChunkManager chunkManager;
    private XUFakePlayer fakePlayer;
    private ItemStack diggingTool;
    public String status = "Idle.";

    public int dx;
    public int dy;
    public int dz;
    public boolean started;
    public float progress;
    int chunk_x;
    int chunk_z;
    int chunk_y;
    int min_x;
    int max_x;
    int min_z;
    int max_z;
    int fence_x;
    int fence_y;
    int fence_z;
    private boolean finished;
    private boolean searching;
    private int neededEnergy;

    public SingleStackHandlerFilter.ItemFilter filter = registerNBT("filter", new SingleStackHandlerFilter.ItemFilter() {
        @Override
        protected void onContentsChanged() {
            markDirty();
        }
    });
    public final SingleStackHandler enchants = registerNBT("enchants", new SingleStackHandler() {
        @Override
        protected int getStackLimit(@Nonnull ItemStack stack) {
            if (stack.getItem() != Items.ENCHANTED_BOOK) {
                return 0;
            }

            Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(stack);
            if (map.isEmpty())
                return 0;

            for (Enchantment enchantment : map.keySet()) {
                if (enchantment.canApply(GENERIC_TOOL)) {
                    return 1;
                }
            }

            return 0;
        }

        @Override
        protected void onContentsChanged() {
            markDirty();
            diggingTool = null;
        }
    });
    public final NBTSerializable.NBTEnum<RedstoneState> redstoneState = registerNBT("redstone_state", new NBTSerializable.NBTEnum<>(RedstoneState.OPERATE_ALWAYS));

    public TileEnderQuarry() {
        this.energy = registerNBT("energy", new XUEnergyStorage(10000000));
        this.extraStacks = registerNBT("extrastacks", new StackDump());
        this.chunkManager = new QuarryChunkManager(this);
        this.dx = 1;
        this.min_x = pos.getX();
        this.max_x = pos.getX();
        this.min_z = pos.getZ();
        this.max_z = pos.getZ();
        this.fence_x = pos.getX();
        this.fence_y = pos.getY();
        this.fence_z = pos.getZ();
        this.neededEnergy = -1;
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }

        if (!redstoneState.value.acceptableValue(world.isBlockIndirectlyGettingPowered(pos) > 0)) {
            status = "Idle.";
            return;
        }

        if (!started) {
            tryStartMining();
            return;
        }

        if (finished) {
            status = "Idle.";
            return;
        }

        if (!searching) {
            status = "Mining.";
        }

        chunkManager.update();

        if (neededEnergy > 0 && world.getTotalWorldTime() % 100L == 0L) {
            neededEnergy = -1;
        }

        if (fakePlayer == null) {
            fakePlayer = new XUFakePlayer((WorldServer) world, owner, "ender_quarry");
        }

        if (StackHelper.isNull(diggingTool)) {
            diggingTool = GENERIC_TOOL.copy();

            ItemStack enchantsStack = enchants.getStack();
            if (StackHelper.isNonNull(enchantsStack)) {
                EnchantmentHelper.setEnchantments(EnchantmentHelper.getEnchantments(enchantsStack), diggingTool);
            }

            diggingTool.setItemDamage(-1);
            fakePlayer.setHeldItem(EnumHand.MAIN_HAND, diggingTool);
        }

        for (int i = getSpeedStack(), j = 0; j < i; ++j) {
            if (extraStacks.hasStacks()) { // TODO fluids as well, but im not porting the pump upgrade rn
                energy.extractEnergy(BASE_POWER_DRAIN, false);
            } else if (energy.getEnergyStored() >= neededEnergy && energy.extractEnergy(BASE_POWER_DRAIN, true) == BASE_POWER_DRAIN) {
                final int x = (this.chunk_x << 4) + this.dx;
                final int z = (this.chunk_z << 4) + this.dz;
                final int y = this.dy;

                if (y >= 0) {
                    if (mineBlock(new BlockPos(x, y, z), true)) { // TODO true for production
                        neededEnergy = -1;
                        nextBlock();
                    }
                } else {
                    nextBlock();
                }
            }

            if (extraStacks.hasStacks()) {
                for (EnumFacing facing : EnumFacing.values()) {
                    TileEntity tile = world.getTileEntity(pos.offset(facing));

                    if (tile == null) {
                        continue;
                    }

                    IItemHandler handler = CapGetter.ItemHandler.getInterface(tile, facing);

                    if (handler != null) {
                        extraStacks.attemptDump(handler);
                    }
                }
            }

            // we're not doing fluids- for now, at least
        }
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        super.breakBlock(worldIn, pos, state);
        chunkManager.release();
    }

    @Override
    public void onPowerChanged() {
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, @Nonnull IBlockState oldState, @Nonnull IBlockState newState) {
        return oldState != newState;
    }

    @Override
    public float getPower() {
        return Float.NaN;
    }

    @Override
    public IEnergyStorage getEnergyHandler(EnumFacing facing) {
        return energy;
    }

    @Override
    public DynamicContainer getDynamicContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return new ContainerEnderQuarry(this, player);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.finished = compound.getBoolean("finished");

        if (this.finished) {
            return;
        }

        if (!(this.started = compound.getBoolean("started"))) {
            return;
        }

        this.min_x = compound.getInteger("min_x");
        this.min_z = compound.getInteger("min_z");
        this.max_x = compound.getInteger("max_x");
        this.max_z = compound.getInteger("max_z");
        this.chunk_x = compound.getInteger("chunk_x");
        this.chunk_y = compound.getInteger("chunk_y");
        this.chunk_z = compound.getInteger("chunk_z");
        this.dx = compound.getInteger("dx");
        this.dy = compound.getInteger("dy");
        this.dz = compound.getInteger("dz");
        this.progress = compound.getFloat("progress");
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        if (finished) {
            compound.setBoolean("finished", true);
        } else if (started) {
            compound.setBoolean("started", true);
            compound.setInteger("min_x", this.min_x);
            compound.setInteger("max_x", this.max_x);
            compound.setInteger("min_z", this.min_z);
            compound.setInteger("max_z", this.max_z);
            compound.setInteger("chunk_x", this.chunk_x);
            compound.setInteger("chunk_y", this.chunk_y);
            compound.setInteger("chunk_z", this.chunk_z);
            compound.setInteger("dx", this.dx);
            compound.setInteger("dy", this.dy);
            compound.setInteger("dz", this.dz);
            compound.setFloat("progress", progress);
        }

        return compound;
    }

    private boolean mineBlock(BlockPos pos, boolean replaceWithDirt) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.AIR || world.isAirBlock(pos)) {
            energy.extractEnergy(BASE_POWER_DRAIN, false);
            return true;
        }

        if (block instanceof IFluidBlock) {
            ((IFluidBlock) block).drain(world, pos, false);
            return true;
        }

        if (false && BlockBreakingRegistry.blackList(block)) { // TODO
            // drain fluid eventually
            energy.extractEnergy(BASE_POWER_DRAIN, false);
            return true;
        }

        if (replaceWithDirt && (block.isLeaves(state, world, pos) || block.isFoliage(world, pos) || block.isWood(world, pos) || block instanceof IPlantable || block instanceof IGrowable)) {
            energy.extractEnergy(BASE_POWER_DRAIN, false);
            return true;
        }

        float hardness = block.getBlockHardness(state, world, pos);

        if (hardness < 0f) {
            energy.extractEnergy(BASE_POWER_DRAIN, false);
            return true;
        }

        int drainAmount = (int) Math.ceil(BASE_POWER_DRAIN + hardness * HARDNESS_POWER_DRAIN * getPowerMultiplier());

        if (drainAmount > energy.getMaxEnergyStored()) {
            drainAmount = energy.getMaxEnergyStored();
        }

        if (energy.extractEnergy(drainAmount, true) < drainAmount) {
            neededEnergy = drainAmount;
            return false;
        }

        energy.extractEnergy(drainAmount, false);

        if (replaceWithDirt && (block == Blocks.GRASS || block == Blocks.DIRT)) {
            if (world.canBlockSeeSky(pos.up())) {
                world.setBlockState(pos, Blocks.GRASS.getDefaultState(), 3);
            }

            if (RANDOM.nextInt(16) == 0 && world.isAirBlock(pos.up())) {
                if (RANDOM.nextInt(5) == 0) {
                    world.getBiomeForCoordsBody(pos).plantFlower(world, RANDOM, pos.up());
                } else if (RANDOM.nextInt(2) == 0) {
                    world.setBlockState(pos.up(), Blocks.YELLOW_FLOWER.getDefaultState(), 3);
                } else {
                    world.setBlockState(pos.up(), Blocks.RED_FLOWER.getDefaultState(), 3);
                }
            }

            return true;
        }

        return harvestBlock(state, block, pos, replaceWithDirt);
    }

    public boolean harvestBlock(IBlockState state, Block block, BlockPos pos, boolean replaceWithDirt) {
        boolean isOpaque = block.isOpaqueCube(state);
        boolean canSeeSky = replaceWithDirt && isOpaque && world.canBlockSeeSky(pos.up());

        ItemCaptureHandler.startCapturing();
        XPCaptureHandler.startCapturing();

        try {
            fakePlayer.interactionManager.tryHarvestBlock(pos);
        } catch (Throwable err) {
            XPCaptureHandler.stopCapturing();
            ItemCaptureHandler.stopCapturing();
            throw Throwables.propagate(err);
        }

        XPCaptureHandler.stopCapturing();
        for (ItemStack stack : ItemCaptureHandler.stopCapturing()) {
            if (filter.matches(stack)) {
                extraStacks.addStack(stack);
            }
        }

        // TODO particle effect

        if (canSeeSky && RANDOM.nextInt(16) == 0 && world.isAirBlock(pos.up())) {
            if (RANDOM.nextInt(5) == 0) {
                world.getBiomeForCoordsBody(pos).plantFlower(world, RANDOM, pos.up());
            } else if (RANDOM.nextInt(2) == 0) {
                world.setBlockState(pos.up(), Blocks.YELLOW_FLOWER.getDefaultState(), 3);
            } else {
                world.setBlockState(pos.up(), Blocks.RED_FLOWER.getDefaultState(), 3);
            }
        }

        return true;
    }

    public void tryStartMining() {
        if (finished) {
            status = "Finished mining.";
            return;
        }

        if (started) {
            status = "Started mining.";
            return;
        }

        if (searching) {
            status = "Searching fence boundary.";
            return;
        }

        checkForMarkers();
    }

    public boolean checkForMarkers() {
        EnumFacing[] directions = {EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH};
        int length = directions.length;

        int i = 0;
        while (i < length) { // This is mostly untouched. While it can be written better, I don't want to break any logic
            EnumFacing direction = directions[i];
            BlockPos offsetPos = this.pos.offset(direction);
            int[] test = {world.provider.getDimension(), offsetPos.getX(), offsetPos.getY(), offsetPos.getZ()};
            int[] testForward = null;
            int[] testSide = null;
            boolean flag = true;

            for (final int[] a : TileEntityEnderMarker.MARKERS) {
                if (isEqual(a, test)) {
                    flag = false;
                    break;
                }
            }

            if (flag) {
                ++i;
            } else {
                for (int[] a : TileEntityEnderMarker.MARKERS) {
                    if (a[0] == test[0] && a[2] == test[2] && (a[1] != test[1] || a[3] != test[3])) {
                        if (sign(a[1] - test[1]) == direction.getFrontOffsetX() && sign(a[3] - test[3]) == direction.getFrontOffsetZ()) {
                            if (testForward == null) {
                                testForward = a;
                            } else if (!isEqual(a, testForward)) {
                                status = "Ambigous marker square.";
                            }
                        }

                        if ((direction.getFrontOffsetX() != 0 || a[3] != test[3]) && (direction.getFrontOffsetZ() != 0 || a[1] != test[1])) {
                            continue;
                        }

                        if (testSide == null) {
                            testSide = a;
                        } else {
                            if (isEqual(a, testSide)) {
                                continue;
                            }

                            status = "Ambigous marker square.";
                        }
                    }
                }

                if (testForward == null) {
                    status = "Incomplete marker square.";
                    return false;
                }

                if (testSide == null) {
                    status = "Incomplete marker square.";;
                    return false;
                }

                final int amin_x = Math.min(Math.min(test[1], testForward[1]), testSide[1]);
                final int amax_x = Math.max(Math.max(test[1], testForward[1]), testSide[1]);
                final int amin_z = Math.min(Math.min(test[3], testForward[3]), testSide[3]);
                final int amax_z = Math.max(Math.max(test[3], testForward[3]), testSide[3]);

                if (amax_x - amin_x <= 2 || amax_z - amin_z <= 2) {
                    stopFencing("Region created by ender markers is too small", false);
                    return false;
                } else if (amax_x - amin_x >= 64 || amax_z - amin_z >= 64) {
                    stopFencing("Region created by ender markers is too large (64x64 max)", false);
                    return false;
                }

                status = "Established boundary.";

                this.chunk_y = this.pos.getY();
                this.min_x = amin_x;
                this.max_x = amax_x;
                this.min_z = amin_z;
                this.max_z = amax_z;
                this.searching = false;
                this.startDig();
                return true;
            }
        }

        return false;
    }

    public void stopFencing(String reason, final boolean sendLocation) {
        this.searching = false;

        if (sendLocation) {
            reason = reason + ": (" + this.fence_x + "," + this.fence_y + "," + this.fence_z + ")";
        }

        status = reason;
        /*
        if (this.owner != null) {
            this.owner.addChatComponentMessage((IChatComponent)new ChatComponentText(reason));
        }
         */
    }

    public void startDig() {
        status = "Mining.";
        this.started = true;
        this.chunk_y += 5;
        this.chunk_x = this.min_x + 1 >> 4;
        this.chunk_z = this.min_z + 1 >> 4;
        this.dx = Math.max(0, this.min_x + 1 - (this.chunk_x << 4));
        this.dy = this.chunk_y;
        this.dz = Math.max(0, this.min_z + 1 - (this.chunk_z << 4));

        if (!this.stopHere()) {
            this.nextBlock();
        }

        // world.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, 1, 2);
    }

    public void nextBlock() {
        this.nextSubBlock();

        while (!this.stopHere()) {
            this.nextSubBlock();
        }
    }

    public void nextSubBlock() {
        ++this.progress;
        --this.dy;
        if (this.dy <= 0) {
            ++this.dx;
            if (this.dx >= 16 || (this.chunk_x << 4) + this.dx >= this.max_x) {
                this.dx = Math.max(0, this.min_x + 1 - (this.chunk_x << 4));
                ++this.dz;
                if (this.dz >= 16 || (this.chunk_z << 4) + this.dz >= this.max_z) {
                    this.nextChunk();
                    this.dx = Math.max(0, this.min_x + 1 - (this.chunk_x << 4));
                    this.dz = Math.max(0, this.min_z + 1 - (this.chunk_z << 4));
                }
            }
            this.dy = this.chunk_y;
        }
    }

    public void nextChunk() {
        chunkManager.unloadMiningChunk();
        ++this.chunk_x;
        if (this.chunk_x << 4 >= this.max_x) {
            this.chunk_x = this.min_x + 1 >> 4;
            ++this.chunk_z;
            if (this.chunk_z << 4 >= this.max_z) {
                this.finished = true;
                // this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, 2, 2);
                chunkManager.release();
                return;
            }
        }
        this.dy = this.chunk_y;
        chunkManager.loadMiningChunk();
    }

    private boolean stopHere() {
        return finished || isValid((chunk_x << 4) + dx, (chunk_z << 4) + dz);
    }

    private boolean isValid(int x, int z) {
        return min_x < x && x < max_x && min_z < z && z < max_z;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    private int getPowerMultiplier() {
        return 1;
    }

    private int getSpeedMultiplier() {
        return 1;
    }

    private int getSpeedStack() {
        return 1;
    }

    private int sign(int value) {
        return Integer.compare(value, 0);
    }

    private boolean isEqual(int[] a, int[] b) {
        if (a == b) {
            return true;
        }

        for (int i = 0; i < 4; ++i) {
            if (a[i] != b[i]) {
                return false;
            }
        }

        return true;
    }
}
