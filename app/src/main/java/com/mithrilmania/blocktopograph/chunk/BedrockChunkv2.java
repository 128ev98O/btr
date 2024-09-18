package com.mithrilmania.blocktopograph.chunk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mithrilmania.blocktopograph.WorldData;
import com.mithrilmania.blocktopograph.block.Block;
import com.mithrilmania.blocktopograph.block.BlockTemplate;
import com.mithrilmania.blocktopograph.block.BlockTemplates;
import com.mithrilmania.blocktopograph.chunk.terrain.TerrainSubChunk;
import com.mithrilmania.blocktopograph.map.Biome;
import com.mithrilmania.blocktopograph.map.Dimension;
import com.mithrilmania.blocktopograph.utils.ColorUtil;
import com.mithrilmania.blocktopograph.utils.Noise;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public final class BedrockChunkv2 extends Chunk {

    private static final int POS_HEIGHTMAP = 0;
    private static final int POS_BIOME_DATA = 0x200;
    public static final int DATA2D_LENGTH = 0x300;

    private boolean mHasBlockLight;
    private final Map<Byte, Boolean> mDirtyList;
    private final Map<Byte, Boolean> mVoidList;
    private final Map<Byte, Boolean> mErrorList;
    private final Map<Byte, TerrainSubChunk> mTerrainSubChunks;
    private volatile ByteBuffer blockData;

    BedrockChunkv2(WorldData worldData, Version version, int chunkX, int chunkZ, Dimension dimension,
                   boolean createIfMissing) {
        super(worldData, version, chunkX, chunkZ, dimension);
        int subchunkCount = version.subChunks;
        mVoidList = new HashMap<>(subchunkCount);
        mErrorList = new HashMap<>(subchunkCount);
        mDirtyList = new HashMap<>(subchunkCount);
        mTerrainSubChunks = new HashMap<>(subchunkCount);
        mHasBlockLight = true;
        loadSubchunks();
    }

    private void loadSubchunks() {
        var key = WorldData.getChunkDataKey(mChunkX, mChunkZ, ChunkTag.TERRAIN, mDimension);
        var l = mWorldData.get().getStartsWith(key);
        for (var k : l) {
            var value = mWorldData.get().db.get(k);
            var id = k[k.length - 1];
            var ret = TerrainSubChunk.create(value);
            mTerrainSubChunks.put(id, ret);
        }
    }

    @Nullable
    private TerrainSubChunk getSubChunk(byte id, boolean createIfMissing) {
        if (mIsError || Boolean.TRUE.equals(mVoidList.get(id))) return null;
        TerrainSubChunk ret = mTerrainSubChunks.get(id);
        if (ret == null) {
            byte[] raw;
            WorldData worldData = mWorldData.get();
            try {
                raw = worldData.getChunkData(mChunkX, mChunkZ, ChunkTag.TERRAIN, mDimension, id);
                if (raw == null && !createIfMissing) {
                    mVoidList.put(id, true);
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                mErrorList.put(id, true);
                mVoidList.put(id, true);
                return null;
            }
            ret = raw == null ?
                    TerrainSubChunk.createEmpty(9) :
                    TerrainSubChunk.create(raw);
            if (ret == null || ret.isError()) {
                mErrorList.put(id, true);
                mVoidList.put(id, true);
                ret = null;
            } else if (!ret.hasBlockLight()) mHasBlockLight = false;
            mTerrainSubChunks.put(id, ret);
        }
        return ret;
    }

    private int get2dOffset(int x, int z) {
        return (z << 4) | x;
    }

    @Override
    public boolean supportsBlockLightValues() {
        return mHasBlockLight;
    }

    @Override
    public boolean supportsHeightMap() {
        return true;
    }

    @Override
    public int getHeightLimit() {
        return 320 - (-64);
    }

    @Override
    public int getHeightMapValue(int x, int z) {
        return 320;
    }

    @Override
    public int getBiome(int x, int z) {
        return Biome.PLAINS.id;
    }

    @Override
    public void setBiome(int x, int z, int id) {

    }

    private int getNoise(int x, int z) {
        // noise values are between -1 and 1
        // 0.0001 is added to the coordinates because integer values result in 0
        double xval = (mChunkX << 4) | x;
        double zval = (mChunkZ << 4) | z;
        double oct1 = Noise.noise(
                (xval / 100.0) % 256 + 0.0001,
                (zval / 100.0) % 256 + 0.0001);
        double oct2 = Noise.noise(
                (xval / 20.0) % 256 + 0.0001,
                (zval / 20.0) % 256 + 0.0001);
        double oct3 = Noise.noise(
                (xval / 3.0) % 256 + 0.0001,
                (zval / 3.0) % 256 + 0.0001);
        return (int) (60 + (40 * oct1) + (14 * oct2) + (6 * oct3));
    }

    @Override
    public int getGrassColor(int x, int z) {
        Biome biome = Biome.PLAINS;
        int noise = getNoise(x, z);
        int r = 30 + (biome.color.red / 5) + noise;
        int g = 110 + (biome.color.green / 5) + noise;
        int b = 30 + (biome.color.blue / 5) + noise;
        return ColorUtil.truncateRgb(r, g, b);
    }

    @NonNull
    @Override
    public BlockTemplate getBlockTemplate(int x, int y, int z, int layer) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return BlockTemplates.getAirTemplate();
        TerrainSubChunk subChunk = getSubChunk((byte) (y >> 4), false);
        if (subChunk == null)
            return BlockTemplates.getAirTemplate();
        return subChunk.getBlockTemplate(x, y & 0xf, z, layer);
    }

    @NonNull
    @Override
    public Block getBlock(int x, int y, int z, int layer) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            throw new IllegalArgumentException();
        TerrainSubChunk subChunk = getSubChunk((byte) (y >> 4), false);
        if (subChunk == null)
            return BlockTemplates.getAirTemplate().getBlock();
        return subChunk.getBlock(x, y & 0xf, z, layer);
    }

    @Override
    public void setBlock(int x, int y, int z, int layer, @NonNull Block block) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return;
        int id = y >> 4;
        TerrainSubChunk subChunk = getSubChunk((byte) id, true);
        if (subChunk == null) return;
        subChunk.setBlock(x, y & 0xf, z, layer, block);
        mDirtyList.put((byte) id, true);
        BlockTemplate template = BlockTemplates.getBest(block);
    }

    @Override
    public int getBlockLightValue(int x, int y, int z) {
        if (!mHasBlockLight || x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return 0;
        TerrainSubChunk subChunk = getSubChunk((byte) (y >> 4), false);
        if (subChunk == null) return 0;
        return subChunk.getBlockLightValue(x, y & 0xf, z);
    }

    @Override
    public int getSkyLightValue(int x, int y, int z) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return 0;
        TerrainSubChunk subChunk = getSubChunk((byte) (y >> 4), false);
        if (subChunk == null) return 0;
        return subChunk.getSkyLightValue(x, y & 0xf, z);
    }

    @Override
    public int getHighestBlockYUnderAt(int x, int z, int y) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return -1;
        TerrainSubChunk subChunk;
        for (int which = y >> 4; which >= 0; which--) {
            subChunk = getSubChunk((byte) which, false);
            if (subChunk == null) continue;
            for (int innerY = (which == (y >> 4)) ? y & 0xf : 15; innerY >= 0; innerY--) {
                if (subChunk.getBlockTemplate(x, innerY, z, 0) != BlockTemplates.getAirTemplate())
                    return (which << 4) | innerY;
            }
        }
        return -1;
    }

    @Override
    public int getCaveYUnderAt(int x, int z, int y) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return -1;
        TerrainSubChunk subChunk;
        for (int which = y >> 4; which >= 0; which--) {
            subChunk = getSubChunk((byte) which, false);
            if (subChunk == null) continue;
            for (int innerY = (which == (y >> 4)) ? y & 0xf : 15; innerY >= 0; innerY--) {
                if (subChunk.getBlockTemplate(x, innerY, z, 0) == BlockTemplates.getAirTemplate())
                    return (which << 4) | innerY;
            }
        }
        return -1;
    }

    @Override
    public void save() throws WorldData.WorldDBException, IOException {

        if (mIsError || mIsVoid) return;

        WorldData worldData = mWorldData.get();
        if (worldData == null)
            throw new RuntimeException("World data is null.");

        // Save biome and hightmap.
//        if (mIs2dDirty)
//            worldData.writeChunkData(
//                    mChunkX, mChunkZ, ChunkTag.DATA_2D, mDimension, data2D.array());

        // Save subChunks.
        for (int i = -4, mTerrainSubChunksLength = mTerrainSubChunks.size(); i < mTerrainSubChunksLength; i++) {
            TerrainSubChunk subChunk = mTerrainSubChunks.get((byte) i);
            if (subChunk == null || Boolean.TRUE.equals(mVoidList.get((byte) i)) || Boolean.FALSE.equals(mDirtyList.get((byte) i)))
                continue;
            //Log.d(this,"Saving "+i);
            subChunk.save(worldData, mChunkX, mChunkZ, mDimension, i);
        }
    }
}
