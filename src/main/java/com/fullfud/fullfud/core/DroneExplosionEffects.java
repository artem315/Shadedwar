package com.fullfud.fullfud.core;

import com.fullfud.fullfud.common.entity.ExplosionShrapnelEntity;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.entity.drone.DronePreset;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.DroneExplosionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class DroneExplosionEffects {
    public static final float SBW_VEHICLE_DIRECT_IMPACT_DAMAGE = 4500.0F;
    private static final float SBW_VEHICLE_EXPLOSION_DAMAGE = 80.0F;
    private static final float SBW_VEHICLE_EXPLOSION_RADIUS = 5.0F;
    private static final double SHRAPNEL_BASE_SPAWN_Y_OFFSET = 0.65D;
    private static final double SHRAPNEL_SPAWN_CLEARANCE = 0.08D;
    private static final double[] SHRAPNEL_FORWARD_SPAWN_OFFSETS = { 0.35D, 0.75D, 1.15D };
    private static final double[] SHRAPNEL_VERTICAL_SPAWN_OFFSETS = { 0.0D, 0.25D, 0.55D, 0.9D };

    private static final BlastProfile FPV_PROFILE = new BlastProfile(DroneExplosionPacket.TYPE_FPV_STANDARD, 1.0F, 180, 18.0F, 25.0D, 4.0F, 12.0F, 4.2F, ShrapnelPattern.HORIZONTAL_RING, 0.0F);
    private static final BlastProfile FPV_STRIKE_PROFILE = new BlastProfile(DroneExplosionPacket.TYPE_FPV_STRIKE, 1.15F, 180, 18.0F, 25.0D, 4.0F, 12.0F, 4.2F, ShrapnelPattern.FORWARD_CONE, 16.0F);
    private static final BlastProfile SHAHED_PROFILE = new BlastProfile(DroneExplosionPacket.TYPE_SHAHED, 4.8F, 800, 24.0F, 380.0D, 20.0F, 90.0F, 4.6F, ShrapnelPattern.SPHERICAL, 0.0F);
    private static final BlastProfile FLAMINGO_PROFILE = new BlastProfile(DroneExplosionPacket.TYPE_FLAMINGO, 8.0F, 1200, 42.0F, 360.0D, 32.0F, 110.0F, 4.0F, ShrapnelPattern.SPHERICAL, 0.0F);

    private static final float DISTANT_CLOSE_RADIUS = 24.0F;
    private static final float DISTANT_MEDIUM_RADIUS = 80.0F;
    private static final float DISTANT_FAR_RADIUS = 280.0F;

    private DroneExplosionEffects() {
    }

    public static void afterFpvExplosion(final ServerLevel level, final Entity source, @Nullable final LivingEntity attacker) {
        applyExplosionEffects(level, source, attacker, FPV_PROFILE, null);
    }

    public static void afterFpvExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final DronePreset preset,
        @Nullable final Vec3 impactDirection
    ) {
        applyExplosionEffects(level, source, attacker, fpvProfile(preset), impactDirection);
    }

    public static ImpactPresentation presentFpvExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final DronePreset preset,
        @Nullable final Vec3 impactDirection
    ) {
        return presentExplosionEffects(level, source, attacker, fpvProfile(preset), impactDirection, null);
    }

    public static void finishFpvExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final DronePreset preset,
        @Nullable final Vec3 impactDirection,
        final ImpactPresentation presentation
    ) {
        applyExplosionDamage(level, source, attacker, presentation, fpvProfile(preset), impactDirection);
    }

    private static BlastProfile fpvProfile(final DronePreset preset) {
        return preset == DronePreset.STRIKE_7INCH ? FPV_STRIKE_PROFILE : FPV_PROFILE;
    }

    public static void afterShahedExplosion(final ServerLevel level, final Entity source, @Nullable final LivingEntity attacker) {
        applyExplosionEffects(level, source, attacker, SHAHED_PROFILE, null);
    }

    public static void afterShahedExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        @Nullable final Vec3 facingDirection
    ) {
        applyExplosionEffects(level, source, attacker, SHAHED_PROFILE, facingDirection);
    }

    public static ImpactPresentation presentShahedExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        @Nullable final Vec3 facingDirection
    ) {
        return presentExplosionEffects(level, source, attacker, SHAHED_PROFILE, facingDirection, null);
    }

    public static void finishShahedExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        @Nullable final Vec3 facingDirection,
        final ImpactPresentation presentation
    ) {
        applyExplosionDamage(level, source, attacker, presentation, SHAHED_PROFILE, facingDirection);
    }

    public record ImpactPresentation(Vec3 origin, Vec3 normal) { }

    public static void afterFlamingoExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        @Nullable final Vec3 facingDirection
    ) {
        afterFlamingoExplosion(level, source, attacker, facingDirection, null);
    }

    public static void afterFlamingoExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        @Nullable final Vec3 facingDirection,
        @Nullable final Vec3 explicitNormal
    ) {
        applyExplosionEffects(level, source, attacker, FLAMINGO_PROFILE, facingDirection, explicitNormal);
    }

    public static void applyDirectImpactVehicleDamage(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Entity target
    ) {
        applyDirectImpactVehicleDamage(level, source, attacker, target, SBW_VEHICLE_DIRECT_IMPACT_DAMAGE);
    }

    public static void applyDirectImpactVehicleDamage(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Entity target,
        final float damage
    ) {
        if (!isSuperbWarfareVehicle(target)) {
            return;
        }

        target.hurt(superbWarfareExplosionDamageSource(level, source, attacker), damage);
    }

    public static void applyFlamingoVehicleDemolition(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Entity target
    ) {
        applyDirectImpactVehicleDamage(level, source, attacker, target, SBW_VEHICLE_DIRECT_IMPACT_DAMAGE);
    }

    private static void applyExplosionEffects(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final BlastProfile profile,
        @Nullable final Vec3 impactDirection
    ) {
        applyExplosionEffects(level, source, attacker, profile, impactDirection, null);
    }

    private static void applyExplosionEffects(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final BlastProfile profile,
        @Nullable final Vec3 impactDirection,
        @Nullable final Vec3 explicitNormal
    ) {
        final ImpactPresentation presentation = presentExplosionEffects(
            level, source, attacker, profile, impactDirection, explicitNormal);
        applyExplosionDamage(level, source, attacker, presentation, profile, impactDirection);
    }

    private static ImpactPresentation presentExplosionEffects(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final BlastProfile profile,
        @Nullable final Vec3 impactDirection,
        @Nullable final Vec3 explicitNormal
    ) {
        final Vec3 origin = source.position();
        final Vec3 normal = explicitNormal != null
            ? (explicitNormal.lengthSqr() > 1.0E-4D ? explicitNormal.normalize() : Vec3.ZERO)
            : resolveImpactNormal(level, origin, impactDirection);
        final byte matType = resolveMaterialType(level, origin);

        // Send cinematic visual/particle packet to all clients in range
        final DroneExplosionPacket packet = new DroneExplosionPacket(
            origin.x, origin.y, origin.z,
            (float) normal.x, (float) normal.y, (float) normal.z,
            profile.explosionType(),
            matType,
            profile.power()
        );
        FullfudNetwork.getChannel().send(
            PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(origin.x, origin.y, origin.z, 512.0D, level.dimension())),
            packet
        );

        ServerPlayer resolvedPilot = null;
        if (attacker instanceof ServerPlayer serverPlayer) {
            resolvedPilot = serverPlayer;
        } else if (source instanceof FpvDroneEntity drone && drone.getControllerId() != null && level.getServer() != null) {
            resolvedPilot = level.getServer().getPlayerList().getPlayer(drone.getControllerId());
        }

        if (resolvedPilot != null) {
            final ServerPlayer pilot = resolvedPilot;
            final double distSq = pilot.distanceToSqr(origin.x, origin.y, origin.z);
            if (pilot.level().dimension() == level.dimension() && distSq > 512.0D * 512.0D) {
                FullfudNetwork.getChannel().send(
                    PacketDistributor.PLAYER.with(() -> pilot),
                    packet
                );
            }
        }

        playLayeredDistanceSounds(level, origin);
        return new ImpactPresentation(origin, normal);
    }

    private static void applyExplosionDamage(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final ImpactPresentation presentation,
        final BlastProfile profile,
        @Nullable final Vec3 impactDirection
    ) {
        applyWarbornBlastDamage(level, source, attacker, presentation.origin(), presentation.normal(), profile);
        applySuperbWarfareExplosionDamage(level, source, attacker, presentation.origin(), profile);
        spawnShrapnel(level, source, attacker, presentation.origin(), profile, impactDirection);
    }

    public static byte resolveMaterialType(final ServerLevel level, final Vec3 origin) {
        final BlockPos pos = BlockPos.containing(origin);
        if (level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            return DroneExplosionPacket.MAT_WATER;
        }

        final BlockState state = level.getBlockState(pos).isAir() ? level.getBlockState(pos.below()) : level.getBlockState(pos);

        if (state.is(BlockTags.SAND)) {
            return DroneExplosionPacket.MAT_SAND;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) ||
            state.is(BlockTags.STONE_ORE_REPLACEABLES) ||
            state.is(Blocks.GRAVEL) ||
            state.is(Blocks.COBBLESTONE)) {
            return DroneExplosionPacket.MAT_STONE;
        }
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS)) {
            return DroneExplosionPacket.MAT_WOOD;
        }
        if (state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK)) {
            return DroneExplosionPacket.MAT_DIRT;
        }

        return DroneExplosionPacket.MAT_GENERIC;
    }

    public static Vec3 resolveImpactNormal(final ServerLevel level, final Vec3 origin, @Nullable final Vec3 velocity) {
        if (velocity != null && velocity.lengthSqr() > 1.0E-4D) {
            final Vec3 rayDir = velocity.normalize();
            final Vec3 rayStart = origin.subtract(rayDir.scale(0.5D));
            final Vec3 rayEnd = origin.add(rayDir.scale(1.5D));
            final HitResult hit = level.clip(new ClipContext(rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
            if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
                final Direction dir = blockHit.getDirection();
                return new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
        // Downward raycast (5.0m) to check for ground contact
        final Vec3 groundRayEnd = origin.subtract(0.0D, 5.0D, 0.0D);
        final HitResult groundHit = level.clip(new ClipContext(origin, groundRayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
        if (groundHit instanceof BlockHitResult blockHit && groundHit.getType() != HitResult.Type.MISS) {
            final Direction dir = blockHit.getDirection();
            return new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        }
        return Vec3.ZERO;
    }

    private static void applyWarbornBlastDamage(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Vec3 origin,
        final Vec3 normal,
        final BlastProfile profile
    ) {
        final float baseDamage = profile.baseBlastDamage();
        final float lethalRadius = profile.blastLethalRadius();
        final float maxRadius = profile.blastMaxRadius();
        // Offset origin along impact normal so blast origin is in open space, not inside solid walls/ground/ceiling
        final Vec3 explosionOrigin = origin.add(normal.scale(0.2D));
        final AABB explosionArea = new AABB(
            explosionOrigin.x - maxRadius, explosionOrigin.y - maxRadius, explosionOrigin.z - maxRadius,
            explosionOrigin.x + maxRadius, explosionOrigin.y + maxRadius, explosionOrigin.z + maxRadius
        );
        final List<Entity> entities = new ArrayList<>(level.getEntities(source, explosionArea));
        final DamageSource damageSource = level.damageSources().explosion(source, attacker != null ? attacker : source);

        for (final Entity target : entities) {
            if (target.isSpectator() || isSuperbWarfareVehicle(target)) {
                continue;
            }

            final Vec3 center = target.getBoundingBox().getCenter();
            final double distance = center.distanceTo(explosionOrigin);
            if (distance > maxRadius) {
                continue;
            }

            if (!hasWarbornLineOfSight(level, source, explosionOrigin, target, center)) {
                continue;
            }

            final float damage;
            if (distance <= lethalRadius) {
                damage = baseDamage;
            } else {
                final float falloff = (float) ((distance - lethalRadius) / Math.max(0.001D, maxRadius - lethalRadius));
                damage = baseDamage * (1.0F - falloff);
            }
            if (damage <= 0.0F) {
                continue;
            }

            target.invulnerableTime = 0;
            final boolean hurt = target.hurt(damageSource, damage);
            if (!hurt) {
                target.hurt(level.damageSources().generic(), damage);
            }
        }
    }

    private static void applySuperbWarfareExplosionDamage(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Vec3 origin,
        final BlastProfile profile
    ) {
        final float radius = Math.max(SBW_VEHICLE_EXPLOSION_RADIUS, profile.blastLethalRadius());
        final float diameter = radius * 2.0F;
        final AABB area = new AABB(
            origin.x - diameter - 1.0D,
            origin.y - diameter - 1.0D,
            origin.z - diameter - 1.0D,
            origin.x + diameter + 1.0D,
            origin.y + diameter + 1.0D,
            origin.z + diameter + 1.0D
        );
        final DamageSource damageSource = superbWarfareExplosionDamageSource(level, source, attacker);
        final List<Entity> vehicles = level.getEntities(source, area, DroneExplosionEffects::isSuperbWarfareVehicle);

        final float baseDamage = SBW_VEHICLE_EXPLOSION_DAMAGE * profile.power();
        for (final Entity vehicle : vehicles) {
            final double distanceRatio = Math.sqrt(vehicle.distanceToSqr(origin)) / diameter;
            if (distanceRatio > 1.0D) {
                continue;
            }

            final double seenPercent = Mth.clamp(net.minecraft.world.level.Explosion.getSeenPercent(origin, vehicle), 0.01D, 1.0D);
            final double damagePercent = (1.0D - distanceRatio) * seenPercent;
            final float damage = (float) (((damagePercent * damagePercent + damagePercent) / 2.0D) * baseDamage);
            if (damage <= 0.0F) {
                continue;
            }

            vehicle.hurt(damageSource, damage);
        }
    }

    private static void spawnShrapnel(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Vec3 origin,
        final BlastProfile profile,
        @Nullable final Vec3 impactDirection
    ) {
        final Vec3 forwardDirection = impactDirection != null && impactDirection.lengthSqr() > 1.0E-6D
            ? impactDirection.normalize()
            : null;
        final int spawnCount = ExplosionShrapnelEntity.allowedSpawnCount(level, profile.shrapnelCount());
        Vec3 safeSpawnPosition = origin;
        for (int i = 0; i < spawnCount; i++) {
            final Vec3 direction = switch (profile.shrapnelPattern()) {
                case FORWARD_CONE -> forwardDirection != null
                    ? resolveForwardConeDirection(forwardDirection, profile.coneHalfAngleDeg(), level)
                    : randomSphericalDirection(level);
                case SPHERICAL -> randomSphericalDirection(level);
                case HORIZONTAL_RING -> randomHorizontalDirection(level);
            };
            // Reuse a checked origin for a small group of fragments.
            if (i % 8 == 0) {
                safeSpawnPosition = resolveShrapnelSpawnPosition(level, origin, direction);
            }
            final Vec3 spawnPosition = safeSpawnPosition;

            final ExplosionShrapnelEntity shrapnel = new ExplosionShrapnelEntity(
                FullfudRegistries.EXPLOSION_SHRAPNEL_ENTITY.get(),
                spawnPosition.x,
                spawnPosition.y,
                spawnPosition.z,
                level
            );
            shrapnel.setOwner(attacker != null ? attacker : source);
            RemotePlayerProtection.copyHazardTag(shrapnel, source);
            shrapnel.setDamage(profile.shrapnelDamage());
            shrapnel.setMaxRange(profile.shrapnelRange());
            shrapnel.setStartPos(spawnPosition);
            shrapnel.shoot(direction.x, direction.y, direction.z, Math.max(0.4F, profile.shrapnelSpeedCap()), 5.0F);
            level.addFreshEntity(shrapnel);
        }
    }

    private static Vec3 resolveShrapnelSpawnPosition(final ServerLevel level, final Vec3 origin, final Vec3 direction) {
        final Vec3 base = origin.add(0.0D, SHRAPNEL_BASE_SPAWN_Y_OFFSET, 0.0D);
        final Vec3 normalizedDirection = direction.lengthSqr() > 1.0E-6D ? direction.normalize() : new Vec3(0.0D, 1.0D, 0.0D);

        for (final double verticalOffset : SHRAPNEL_VERTICAL_SPAWN_OFFSETS) {
            final Vec3 liftedBase = base.add(0.0D, verticalOffset, 0.0D);
            if (isClearShrapnelSpawnPosition(level, liftedBase)) {
                return liftedBase;
            }

            for (final double forwardOffset : SHRAPNEL_FORWARD_SPAWN_OFFSETS) {
                final Vec3 candidate = liftedBase.add(normalizedDirection.scale(forwardOffset));
                if (isClearShrapnelSpawnPosition(level, candidate)) {
                    return candidate;
                }
            }
        }

        return base;
    }

    private static boolean isClearShrapnelSpawnPosition(final ServerLevel level, final Vec3 position) {
        final AABB bounds = new AABB(
            position.x - SHRAPNEL_SPAWN_CLEARANCE,
            position.y - SHRAPNEL_SPAWN_CLEARANCE,
            position.z - SHRAPNEL_SPAWN_CLEARANCE,
            position.x + SHRAPNEL_SPAWN_CLEARANCE,
            position.y + SHRAPNEL_SPAWN_CLEARANCE,
            position.z + SHRAPNEL_SPAWN_CLEARANCE
        );
        return level.noCollision(bounds);
    }

    private static Vec3 randomHorizontalDirection(final ServerLevel level) {
        final double theta = Math.PI * 2.0D * level.random.nextDouble();
        final double horizontalX = Math.cos(theta);
        final double horizontalZ = Math.sin(theta);
        final double vertical = (level.random.nextDouble() - 0.5D) * 0.7D;
        return new Vec3(horizontalX, vertical, horizontalZ).normalize();
    }

    private static Vec3 randomSphericalDirection(final ServerLevel level) {
        final double u = level.random.nextDouble();
        final double v = level.random.nextDouble();
        final double theta = Math.PI * 2.0D * u;
        final double phi = Math.acos(2.0D * v - 1.0D);
        final double dx = Math.sin(phi) * Math.cos(theta);
        final double dy = Math.sin(phi) * Math.sin(theta);
        final double dz = Math.cos(phi);
        return new Vec3(dx, dy, dz).normalize();
    }

    private static Vec3 randomDirectionInCone(final Vec3 forward, final float coneHalfAngleDeg, final ServerLevel level) {
        final Vec3 normalizedForward = forward.normalize();
        final Vec3 referenceUp = Math.abs(normalizedForward.y) > 0.98D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        final Vec3 basisRight = normalizedForward.cross(referenceUp).normalize();
        final Vec3 basisUp = basisRight.cross(normalizedForward).normalize();

        final double coneHalfAngleRad = Math.toRadians(coneHalfAngleDeg);
        final double azimuth = Math.PI * 2.0D * level.random.nextDouble();
        final double cosTheta = Mth.lerp(level.random.nextDouble(), Math.cos(coneHalfAngleRad), 1.0D);
        final double sinTheta = Math.sqrt(Math.max(0.0D, 1.0D - cosTheta * cosTheta));

        final Vec3 lateral = basisRight.scale(Math.cos(azimuth)).add(basisUp.scale(Math.sin(azimuth)));
        return normalizedForward.scale(cosTheta).add(lateral.scale(sinTheta)).normalize();
    }

    private static Vec3 resolveForwardConeDirection(final Vec3 forward, final float coneHalfAngleDeg, final ServerLevel level) {
        final Vec3 normalizedForward = forward.normalize();
        if (normalizedForward.y < -0.9D) {
            final Vec3 horizontalBias = new Vec3(normalizedForward.x, 0.0D, normalizedForward.z);
            if (horizontalBias.lengthSqr() > 1.0E-6D) {
                return randomDirectionInCone(horizontalBias.normalize().add(0.0D, -0.18D, 0.0D).normalize(), coneHalfAngleDeg, level);
            }
            return randomHorizontalDirection(level);
        }
        return randomDirectionInCone(normalizedForward, coneHalfAngleDeg, level);
    }

    private static void playLayeredDistanceSounds(final ServerLevel level, final Vec3 origin) {
        for (final ServerPlayer player : level.players()) {
            final double distanceSqr = player.distanceToSqr(origin);
            if (distanceSqr < DISTANT_CLOSE_RADIUS * DISTANT_CLOSE_RADIUS) {
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_OCP_HEAVY.getHolder(), DISTANT_CLOSE_RADIUS / 16.0F);
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_CLOSE.getHolder(), DISTANT_CLOSE_RADIUS / 16.0F);
            } else if (distanceSqr < DISTANT_MEDIUM_RADIUS * DISTANT_MEDIUM_RADIUS) {
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_OCP_HEAVY.getHolder(), DISTANT_MEDIUM_RADIUS / 16.0F);
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_MEDIUM.getHolder(), DISTANT_MEDIUM_RADIUS / 16.0F);
            } else if (distanceSqr < DISTANT_FAR_RADIUS * DISTANT_FAR_RADIUS) {
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_OCP_DISTANT.getHolder(), DISTANT_FAR_RADIUS / 16.0F);
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_FAR.getHolder(), DISTANT_FAR_RADIUS / 16.0F);
            } else if (distanceSqr < 400.0F * 400.0F) {
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_OCP_DISTANT.getHolder(), 25.0F);
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_VERYFAR.getHolder(), 20.0F);
            }
        }
    }

    private static void sendDistanceSound(
        final ServerLevel level,
        final ServerPlayer player,
        final Vec3 origin,
        final java.util.Optional<Holder<SoundEvent>> soundHolder,
        final float volume
    ) {
        if (soundHolder.isEmpty()) {
            return;
        }

        player.connection.send(new ClientboundSoundPacket(
            soundHolder.get(),
            SoundSource.NEUTRAL,
            origin.x,
            origin.y,
            origin.z,
            volume,
            1.0F,
            level.random.nextLong()
        ));
    }

    private static DamageSource superbWarfareExplosionDamageSource(
        final ServerLevel level,
        final Entity directEntity,
        @Nullable final LivingEntity attacker
    ) {
        return SuperbWarfareCompat.explosionDamageSource(level, directEntity, attacker != null ? attacker : directEntity);
    }

    private static boolean hasWarbornLineOfSight(
        final ServerLevel level,
        final Entity source,
        final Vec3 explosionOrigin,
        final Entity target,
        final Vec3 center
    ) {
        final Vec3[] targetPoints;
        if (target instanceof LivingEntity living) {
            final Vec3 basePos = living.position();
            targetPoints = new Vec3[] {
                basePos.add(0.0D, 0.1D, 0.0D),
                center,
                basePos.add(0.0D, living.getBbHeight(), 0.0D)
            };
        } else {
            targetPoints = new Vec3[] { center };
        }

        for (final Vec3 targetPoint : targetPoints) {
            final ClipContext clipContext = new ClipContext(explosionOrigin, targetPoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source);
            final HitResult hit = level.clip(clipContext);
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (hit instanceof BlockHitResult blockHit) {
                if (blockHit.getLocation().distanceToSqr(targetPoint) < 0.35D) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isSuperbWarfareVehicle(final Entity entity) {
        return SuperbWarfareCompat.isVehicle(entity);
    }

    private record BlastProfile(
        byte explosionType,
        float power,
        int shrapnelCount,
        float shrapnelDamage,
        double shrapnelRange,
        float blastLethalRadius,
        float blastMaxRadius,
        float shrapnelSpeedCap,
        ShrapnelPattern shrapnelPattern,
        float coneHalfAngleDeg
    ) {
        private float baseBlastDamage() {
            return Math.max(120.0F, this.blastLethalRadius * 60.0F);
        }
    }

    private enum ShrapnelPattern {
        SPHERICAL,
        HORIZONTAL_RING,
        FORWARD_CONE
    }
}
