package com.rwtema.extrautils2.quarry.enderquarry;

import com.rwtema.extrautils2.ExtraUtils2;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.ForgeChunkManager;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class QuarryChunkManager {

    private final TileEnderQuarry quarry;
    private final ChunkPos quarryPos;
    private ForgeChunkManager.Ticket chunkTicket;

    public QuarryChunkManager(@Nonnull TileEnderQuarry quarry) {
        this.quarry = quarry;
        this.quarryPos = new ChunkPos(quarry.getPos());
    }

    public ChunkPos getQuarryPos() {
        return quarryPos;
    }

    public void loadMiningChunk() {
        checkPos(new ChunkPos(quarry.chunk_x, quarry.chunk_z), pos -> ForgeChunkManager.forceChunk(chunkTicket, pos));
    }

    public void unloadMiningChunk() {
        checkPos(new ChunkPos(quarry.chunk_x, quarry.chunk_z), pos -> ForgeChunkManager.unforceChunk(chunkTicket, pos));
    }

    public void release() {
        if (chunkTicket != null) {
            ForgeChunkManager.releaseTicket(chunkTicket);
        }
    }

    public void update() {
        if (chunkTicket == null) {
            chunkTicket = ForgeChunkManager.requestTicket(ExtraUtils2.instance, quarry.getWorld(), ForgeChunkManager.Type.NORMAL);
        }

        if (chunkTicket == null) {
            quarry.setFinished(true);
            return;
        }

        NBTTagCompound data = this.chunkTicket.getModData();
        data.setString("id", "ender_quarry");
        data.setLong("pos", quarry.getPos().toLong());
        ForgeChunkManager.forceChunk(chunkTicket, quarryPos);
        loadMiningChunk();
    }

    private void checkPos(ChunkPos pos, Consumer<ChunkPos> consumer) {
        if (pos != null && !pos.equals(quarryPos)) {
            consumer.accept(pos);
        }
    }
}
