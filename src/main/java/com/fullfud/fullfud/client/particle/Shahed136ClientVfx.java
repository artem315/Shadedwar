package com.fullfud.fullfud.client.particle;

import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class Shahed136ClientVfx {
    private static final Map<UUID, ShahedDroneEntity> TRACKED_DRONES = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_RENDER_ANCHORS = new ConcurrentHashMap<>();

    // Propeller / rear engine exhaust is 1.40m behind entity center and 0.10m down (scaled with model)
    private static final double EXHAUST_REAR_OFFSET = 1.40D * ShahedDroneEntity.SCALE;
    private static final double EXHAUST_DOWN_OFFSET = 0.10D * ShahedDroneEntity.SCALE;

    private Shahed136ClientVfx() {
    }

    public static Vec3 computeNozzleAnchor(final ShahedDroneEntity drone, final float partialTick) {
        if (drone == null) {
            return Vec3.ZERO;
        }
        final float yaw = drone.isOnLauncher() ? drone.getYRot() : Mth.rotLerp(partialTick, drone.yRotO, drone.getYRot());
        final float pitch = drone.isOnLauncher() ? drone.getXRot() : Mth.rotLerp(partialTick, drone.xRotO, drone.getXRot());
        final float roll = drone.isOnLauncher() ? 0.0F : drone.getVisualRoll(partialTick);

        final double posX = Mth.lerp((double) partialTick, drone.xo, drone.getX());
        final double posY = Mth.lerp((double) partialTick, drone.yo, drone.getY());
        final double posZ = Mth.lerp((double) partialTick, drone.zo, drone.getZ());
        final Vec3 dronePos = new Vec3(posX, posY, posZ);

        final Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();
        final Vec3 localUp = Vec3.directionFromRotation(pitch - 90.0F, yaw).normalize();
        final Vec3 localRight = forward.cross(localUp).normalize();

        final double rollRad = Math.toRadians((double) roll);
        final double cosRoll = Math.cos(rollRad);
        final double sinRoll = Math.sin(rollRad);
        final Vec3 rolledUp = localUp.scale(cosRoll).add(localRight.scale(sinRoll));
        final Vec3 rolledDown = rolledUp.scale(-1.0D);

        return dronePos.subtract(forward.scale(EXHAUST_REAR_OFFSET)).add(rolledDown.scale(EXHAUST_DOWN_OFFSET));
    }

    public static Vec3 computeNozzleAnchor(final ShahedDroneEntity drone) {
        return computeNozzleAnchor(drone, 1.0F);
    }

    public static void track(final ShahedDroneEntity drone) {
        if (drone != null && drone.isAlive() && !drone.isRemoved()) {
            TRACKED_DRONES.put(drone.getUUID(), drone);
        }
    }

    public static void tick(final ShahedDroneEntity drone) {
        if (drone == null || !drone.isAlive() || drone.isRemoved() || drone.isOnLauncher()) {
            if (drone != null) {
                remove(drone.getUUID());
            }
            return;
        }
        track(drone);
    }

    public static void emitExhaust(
        final UUID id,
        final Vec3 currAnchor,
        final Vec3 forward,
        final float speed,
        final boolean damaged
    ) {
        if (id == null || currAnchor == null || forward == null) {
            return;
        }
        final Vec3 prevAnchor = LAST_RENDER_ANCHORS.get(id);
        LAST_RENDER_ANCHORS.put(id, currAnchor);

        if (prevAnchor == null) {
            return;
        }

        final double dist = prevAnchor.distanceTo(currAnchor);
        if (dist > 64.0D || dist < 0.005D) {
            return;
        }

        DroneParticleManager.spawnShahed136Exhaust(
            prevAnchor,
            currAnchor,
            forward,
            speed,
            damaged
        );
    }

    public static void renderFrame(final float partialTick) {
        renderFrame(partialTick, null);
    }

    public static void renderFrame(final float partialTick, @javax.annotation.Nullable final java.util.Set<UUID> outProcessedIds) {
        if (TRACKED_DRONES.isEmpty()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }

        final Iterator<Map.Entry<UUID, ShahedDroneEntity>> it = TRACKED_DRONES.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, ShahedDroneEntity> entry = it.next();
            final UUID id = entry.getKey();
            final ShahedDroneEntity drone = entry.getValue();

            if (drone == null || !drone.isAlive() || drone.isRemoved() || drone.level() != mc.level) {
                it.remove();
                continue;
            }

            if (!mc.level.hasChunkAt(drone.blockPosition())) {
                // Drone left loaded chunks: let ghost particle emitter seamlessly take over without losing anchor
                it.remove();
                continue;
            }

            if (drone.isOnLauncher()) {
                LAST_RENDER_ANCHORS.remove(id);
                continue;
            }

            final Vec3 currAnchor = computeNozzleAnchor(drone, partialTick);
            final float yaw = Mth.rotLerp(partialTick, drone.yRotO, drone.getYRot());
            final float pitch = Mth.rotLerp(partialTick, drone.xRotO, drone.getXRot());
            final Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();
            final float speed = (float) drone.getDeltaMovement().length();
            final boolean damaged = drone.isDamaged();

            emitExhaust(id, currAnchor, forward, speed, damaged);
            if (outProcessedIds != null) {
                outProcessedIds.add(id);
            }
        }
    }

    public static void remove(final UUID id) {
        if (id != null) {
            TRACKED_DRONES.remove(id);
        }
    }

    public static void forget(final UUID id) {
        remove(id);
        if (id != null) {
            LAST_RENDER_ANCHORS.remove(id);
        }
    }
}
