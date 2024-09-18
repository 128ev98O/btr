package com.mithrilmania.blocktopograph.chunk;

import androidx.annotation.NonNull;

import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.WorldData;
import com.mithrilmania.blocktopograph.block.Block;
import com.mithrilmania.blocktopograph.block.BlockTemplate;
import com.mithrilmania.blocktopograph.map.Dimension;

import java.io.IOException;
import java.lang.ref.WeakReference;

public abstract class Chunk {

    protected final WeakReference<WorldData> mWorldData;
    protected final Version mVersion;
    public final int mChunkX;
    public final int mChunkZ;
    public final Dimension mDimension;
    protected NBTChunkData mEntity;
    protected NBTChunkData mTileEntity;
    boolean mIsVoid;
    boolean mIsError;

    Chunk(WorldData worldData, Version version, int chunkX, int chunkZ, Dimension dimension) {
        mWorldData = new WeakReference<>(worldData);
        mVersion = version;
        mChunkX = chunkX;
        mChunkZ = chunkZ;
        mDimension = dimension;
        mIsVoid = false;
        mIsError = false;
        try {
            mEntity = version.createEntityChunkData(this);
            mTileEntity = version.createBlockEntityChunkData(this);
        } catch (Version.VersionException e) {
            Log.d(this, e);
        }
    }

    public static Chunk createEmpty(@NonNull WorldData worldData, int chunkX, int chunkZ, Dimension dimension,
                                    @NonNull Version createOfVersion) {
        Chunk chunk;
        switch (createOfVersion) {
            case V1_2_PLUS:
                try {
                    worldData.writeChunkData(chunkX, chunkZ, ChunkTag.GENERATOR_STAGE, dimension, new byte[]{2, 0, 0, 0});
                    worldData.writeChunkData(chunkX, chunkZ, ChunkTag.VERSION_PRE16, dimension, new byte[]{0xf});
                    chunk = new BedrockChunk(worldData, createOfVersion, chunkX, chunkZ, dimension, true);
                } catch (Exception e) {
                    Log.d(Chunk.class, e);
                    chunk = new VoidChunk(worldData, createOfVersion, chunkX, chunkZ, dimension);
                }
                break;
            case V1_16_PLUS:
                try {
                    worldData.writeChunkData(chunkX, chunkZ, ChunkTag.GENERATOR_STAGE, dimension, new byte[]{2, 0, 0, 0});
                    worldData.writeChunkData(chunkX, chunkZ, ChunkTag.VERSION, dimension, new byte[]{0x15});
                    chunk = new BedrockChunk(worldData, createOfVersion, chunkX, chunkZ, dimension, true);
                } catch (Exception e) {
                    Log.d(Chunk.class, e);
                    chunk = new VoidChunk(worldData, createOfVersion, chunkX, chunkZ, dimension);
                }
            case V1_18_30_PLUS:
                try {
                    worldData.writeChunkData(chunkX, chunkZ, ChunkTag.GENERATOR_STAGE, dimension, new byte[]{2, 0, 0, 0});
                    worldData.writeChunkData(chunkX, chunkZ, ChunkTag.VERSION, dimension, new byte[]{40});
                    chunk = new BedrockChunkv2(worldData, createOfVersion, chunkX, chunkZ, dimension, true);
                } catch (Exception e) {
                    Log.d(Chunk.class, e);
                    chunk = new VoidChunk(worldData, createOfVersion, chunkX, chunkZ, dimension);
                }
            default:
                chunk = new VoidChunk(worldData, createOfVersion, chunkX, chunkZ, dimension);
        }
        return chunk;
    }

    public static Chunk create(@NonNull WorldData worldData, int chunkX, int chunkZ, Dimension dimension,
                               boolean createIfMissing, Version createOfVersion) {
        Version version = Version.LATEST_SUPPORTED_VERSION;
//        try {
//            byte[] data = worldData.getChunkData(chunkX, chunkZ, ChunkTag.VERSION_PRE16, dimension);
//            if (data == null) data = worldData.getChunkData(chunkX, chunkZ, ChunkTag.VERSION, dimension);
//        } catch (Exception e) {
//            e.printStackTrace();
//            version = Version.ERROR;
//        }
        Chunk chunk;
        switch (version) {
            case V1_2_PLUS:
            case V1_16_PLUS:
                chunk = new BedrockChunk(worldData, version, chunkX, chunkZ, dimension, false);
                break;
            case V1_18_30_PLUS:
                chunk = new BedrockChunkv2(worldData, version, chunkX, chunkZ, dimension, false);
                break;
            case NULL:
            default:
                chunk = new VoidChunk(worldData, version, chunkX, chunkZ, dimension);
        }
//        try {
//            throw new RuntimeException("Called Here");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        return chunk;
    }

    public final WorldData getWorldData() {
        return mWorldData.get();
    }

    public final boolean isVoid() {
        return mIsVoid;
    }

    public final boolean isError() {
        return mIsError;
    }

    abstract public boolean supportsBlockLightValues();

    abstract public boolean supportsHeightMap();

    abstract public int getHeightLimit();

    abstract public int getHeightMapValue(int x, int z);

    abstract public int getBiome(int x, int z);

    abstract public void setBiome(int x, int z, int id);

    abstract public int getGrassColor(int x, int z);

    @NonNull
    public BlockTemplate getBlockTemplate(int x, int y, int z) {
        return getBlockTemplate(x, y, z, 0);
    }

    @NonNull
    abstract public BlockTemplate getBlockTemplate(int x, int y, int z, int layer);

    @NonNull
    public Block getBlock(int x, int y, int z) {
        return getBlock(x, y, z, 0);
    }

    @NonNull
    abstract public Block getBlock(int x, int y, int z, int layer);

    abstract public void setBlock(int x, int y, int z, int layer, @NonNull Block block);

    abstract public int getBlockLightValue(int x, int y, int z);

    abstract public int getSkyLightValue(int x, int y, int z);

    abstract public int getHighestBlockYUnderAt(int x, int z, int y);

    abstract public int getCaveYUnderAt(int x, int z, int y);

    abstract public void save() throws WorldData.WorldDBException, IOException;

    public void deleteThis() {
        // TODO: delete all with given prefix
        WorldData worldData = mWorldData.get();
        if (worldData == null) throw new RuntimeException("World data is null.");
        worldData.removeFullChunk(mChunkX, mChunkZ, mDimension);
        // Prevent saving.
        mIsError = true;
    }

    public final NBTChunkData getEntity() {
        return mEntity;
    }

    public final NBTChunkData getBlockEntity() {
        return mTileEntity;
    }
}
