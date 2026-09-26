package com.fullfud.fullfud.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ShahedLinkData extends SavedData {
    private static final String DATA_NAME = "fullfud_shahed_links";
    private final Map<UUID, UUID> droneOwners = new HashMap<>();
    private final Map<UUID, ChunkPos> droneChunks = new HashMap<>();

    public ShahedLinkData() {
    }

    public ShahedLinkData(final CompoundTag tag) {
        final ListTag list = tag.getList("Links", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Drone") && entry.hasUUID("Owner")) {
                droneOwners.put(entry.getUUID("Drone"), entry.getUUID("Owner"));
            }
        }
        final ListTag chunks = tag.getList("DroneChunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunks.size(); i++) {
            final CompoundTag entry = chunks.getCompound(i);
            if (entry.hasUUID("Drone")) {
                droneChunks.put(entry.getUUID("Drone"), new ChunkPos(entry.getInt("X"), entry.getInt("Z")));
            }
        }
    }

    public static ShahedLinkData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShahedLinkData::new, ShahedLinkData::new, DATA_NAME);
    }

    public void link(final UUID droneId, final UUID ownerId) {
        droneOwners.put(droneId, ownerId);
        setDirty();
    }

    public void unlink(final UUID droneId) {
        if (droneOwners.remove(droneId) != null) {
            setDirty();
        }
    }

    public Optional<UUID> owner(final UUID droneId) {
        return Optional.ofNullable(droneOwners.get(droneId));
    }

    public Optional<ChunkPos> lastChunk(final UUID droneId) {
        return Optional.ofNullable(droneChunks.get(droneId));
    }

    public void updateChunk(final UUID droneId, final ChunkPos pos) {
        if (!pos.equals(droneChunks.put(droneId, pos))) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(final CompoundTag tag) {
        final ListTag list = new ListTag();
        for (final Map.Entry<UUID, UUID> entry : droneOwners.entrySet()) {
            final CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Drone", entry.getKey());
            entryTag.putUUID("Owner", entry.getValue());
            list.add(entryTag);
        }
        tag.put("Links", list);
        final ListTag chunks = new ListTag();
        for (final Map.Entry<UUID, ChunkPos> entry : droneChunks.entrySet()) {
            final CompoundTag chunk = new CompoundTag();
            chunk.putUUID("Drone", entry.getKey());
            chunk.putInt("X", entry.getValue().x);
            chunk.putInt("Z", entry.getValue().z);
            chunks.add(chunk);
        }
        tag.put("DroneChunks", chunks);
        return tag;
    }
}
