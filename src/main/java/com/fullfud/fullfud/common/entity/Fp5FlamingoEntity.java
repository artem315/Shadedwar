package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.core.ChunkLoadManager;
import com.fullfud.fullfud.core.DroneExplosionEffects;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.data.ShahedLinkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Fp5FlamingoEntity extends Entity implements GeoEntity {
    public static final float SCALE = 2.25F;

    private static final EntityDataAccessor<Boolean> DATA_ON_LAUNCHER = SynchedEntityData.defineId(Fp5FlamingoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_LAUNCHER_ID = SynchedEntityData.defineId(Fp5FlamingoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SERVER_YAW = SynchedEntityData.defineId(Fp5FlamingoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SERVER_PITCH = SynchedEntityData.defineId(Fp5FlamingoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SERVER_ROLL = SynchedEntityData.defineId(Fp5FlamingoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LAUNCHED = SynchedEntityData.defineId(Fp5FlamingoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BOOSTER_ACTIVE = SynchedEntityData.defineId(Fp5FlamingoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDimensions FLAMINGO_SIZE = EntityDimensions.scalable(1.9375F * SCALE, 1.2F * SCALE);

    private static final String TAG_ON_LAUNCHER = "OnLauncher";
    private static final String TAG_LAUNCHER_UUID = "Launcher";
    private static final String TAG_LAUNCHED = "Launched";
    private static final String TAG_HAS_TARGET = "HasTarget";
    private static final String TAG_TARGET_X = "TargetX";
    private static final String TAG_TARGET_Y = "TargetY";
    private static final String TAG_TARGET_Z = "TargetZ";
    private static final String TAG_FLIGHT_PHASE = "FlightPhase";
    private static final String TAG_CRUISE_ALTITUDE = "CruiseAltitude";
    private static final String TAG_CLIMB_X = "ClimbWaypointX";
    private static final String TAG_CLIMB_Z = "ClimbWaypointZ";
    private static final String TAG_FLIGHT_SPEED = "FlightSpeed";
    private static final String TAG_LAUNCH_ORIGIN_X = "LaunchOriginX";
    private static final String TAG_LAUNCH_ORIGIN_Z = "LaunchOriginZ";
    private static final String TAG_LAUNCH_LOCKED_YAW = "LaunchLockedYaw";
    private static final String TAG_LAUNCH_LOCKED_PITCH = "LaunchLockedPitch";
    private static final String TAG_BODY_ROLL = "BodyRoll";
    private static final String TAG_YAW_RATE = "YawRate";
    private static final String TAG_LAUNCH_TICKS = "LaunchTicks";
    private static final String TAG_PITCH_RATE = "PitchRate";
    private static final String TAG_LATERAL_SLIP = "LateralSlip";

    private static final float HITBOX_WIDTH = 1.9375F * SCALE;
    private static final float HITBOX_LENGTH = 5.5F * SCALE;
    private static final float HITBOX_HEIGHT = 1.2F * SCALE;
    private static final double LAUNCHER_MOUNT_VERTICAL_OFFSET = 0.0D;
    private static final double LAUNCHER_MOUNT_FORWARD_OFFSET = 0.15D * SCALE;
    private static final float MODEL_FORWARD_YAW_OFFSET = 180.0F;
    private static final float MODEL_BASE_PITCH_DEGREES = 12.5F;

    private static final int FLIGHT_PHASE_IDLE = 0;
    private static final int FLIGHT_PHASE_CLIMB = 1;
    private static final int FLIGHT_PHASE_CRUISE = 2;
    private static final int FLIGHT_PHASE_TERMINAL = 3;

    public static final int LAUNCH_RAIL_LOCK_TICKS = 70;
    public static final int LAUNCHER_CLEARANCE_TICKS = 20;
    public static final double LAUNCHER_CLEARANCE_DISTANCE = 16.0D;
    public static final double MIN_LAUNCH_DISTANCE = 400.0D;

    private static final double CLIMB_TARGET_FORWARD = 24.0D;
    private static final double INITIAL_STRAIGHT_DISTANCE = 200.0D;
    private static final double CLIMB_ALTITUDE_MIN = 20.0D;
    private static final double CLIMB_ALTITUDE_RANDOM = 31.0D;
    private static final double TARGET_ALTITUDE_BUFFER = 8.0D;
    private static final double CLIMB_ALTITUDE_RESPONSE_DISTANCE = 18.0D;
    private static final double CRUISE_ALTITUDE_RESPONSE_DISTANCE = 40.0D;
    public static final int BOOSTER_KICK_TICKS = 15;
    public static final int BOOSTER_BURN_TICKS = 70;
    public static final int MAX_FLIGHT_TICKS = 6000;
    public static final int BOOSTER_TRANSITION_TICKS = 45;
    public static final double BOOSTER_PEAK_SPEED = 5.2D;
    public static final double BOOSTER_SUSTAIN_SPEED = 4.2D;
    public static final double BOOSTER_ACCEL_STEP = 0.38D;
    public static final double BOOSTER_SUSTAIN_STEP = 0.08D;
    public static final double BOOSTER_TRANSITION_STEP = 0.08D;
    public static final double CRUISE_SPEED_TARGET = 2.95D;
    public static final double TERMINAL_SPEED_TARGET = 3.65D;
    private static final double MIN_SPEED_STEP = 0.02D;
    private static final double MAX_SPEED_STEP = 0.09D;
    private static final float CLIMB_PITCH_RATE_LIMIT = 0.85F;
    private static final float CRUISE_PITCH_RATE_LIMIT = 0.28F;
    private static final float TERMINAL_PITCH_RATE_LIMIT = 0.45F;
    private static final float PITCH_RATE_RESPONSE = 0.045F;
    public static final float CLIMB_BANK_LIMIT = 15.0F;
    public static final float CRUISE_BANK_LIMIT = 30.0F;
    public static final float TERMINAL_BANK_LIMIT = 40.0F;
    public static final float BANK_RESPONSE_STEP = 0.40F;
    private static final double CLIMB_LATERAL_SLIP_LIMIT = 0.025D;
    private static final double CRUISE_LATERAL_SLIP_LIMIT = 0.075D;
    private static final double TERMINAL_LATERAL_SLIP_LIMIT = 0.050D;
    private static final double SLIP_BUILD_STEP = 0.0035D;
    private static final double SLIP_DECAY_STEP = 0.0050D;
    private static final double CRUISE_STEER_BLEND = 0.045D;
    private static final double TERMINAL_STEER_BLEND = 0.09D;
    private static final float MAX_CLIMB_YAW_RATE = 0.45F;
    private static final float MAX_CRUISE_YAW_RATE = 0.38F;
    private static final float MAX_TERMINAL_YAW_RATE = 0.48F;
    private static final float CLIMB_YAW_ACCEL = 0.038F;
    private static final float CRUISE_YAW_ACCEL = 0.030F;
    private static final float TERMINAL_YAW_ACCEL = 0.040F;
    private static final float CLIMB_YAW_DAMPING = 0.018F;
    private static final float CRUISE_YAW_DAMPING = 0.015F;
    private static final float TERMINAL_YAW_DAMPING = 0.020F;
    private static final float YAW_MISMATCH_BRAKE = 0.06F;
    private static final double CLIMB_PHASE_THRESHOLD = 3.0D;
    public static final double CRUISE_TERRAIN_CLEARANCE = 100.0D;
    public static final double TERMINAL_APPROACH_DISTANCE = 350.0D;
    private static final double TERMINAL_HORIZONTAL_THRESHOLD = 14.0D;
    private static final double TERMINAL_DISTANCE_THRESHOLD = 1.2D;
    private static final float TERMINAL_DIVE_PITCH = 16.0F;
    private static final float TERMINAL_MAX_PITCH = 35.0F;
    private static final double TERMINAL_GROUND_OFFSET = 0.15D;
    private static final double TERRAIN_IMPACT_BUFFER = 0.35D;
    private static final float TNT_POWER = 4.0F;
    public static final int GUIDANCE_RAMP_TICKS = 45;
    private static final double EXHAUST_REAR_OFFSET = 2.45D * SCALE;
    private static final double EXHAUST_VERTICAL_OFFSET = 0.16D * SCALE;
    private static final double EXHAUST_SIDE_SPREAD = 0.18D * SCALE;
    private static final Vector3f BOOSTER_SMOKE_COLOR = new Vector3f(0.96F, 0.96F, 0.96F);
    private static final DustParticleOptions BOOSTER_SMOKE_PARTICLE = new DustParticleOptions(BOOSTER_SMOKE_COLOR, 2.5F);

    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int mountedLauncherId = -1;
    private UUID mountedLauncherUuid;
    private boolean dropItemOnRemove = true;
    private boolean launched;
    private boolean hasTarget;
    private BlockPos targetPos = BlockPos.ZERO;
    private int flightPhase = FLIGHT_PHASE_IDLE;
    private double cruiseAltitude;
    private double climbWaypointX;
    private double climbWaypointZ;
    private double flightSpeed;
    private double launchOriginX;
    private double launchOriginZ;
    private float launchLockedYaw;
    private float launchLockedPitch = -MODEL_BASE_PITCH_DEGREES;
    private float bodyRoll;
    private float bodyRollO;
    private float yawRate;
    private float pitchRate;
    private double lateralSlip;
    private int launchTicks;
    private int stationaryTicks;
    private static final int STATIONARY_UNLOAD_THRESHOLD_TICKS = 200;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    @Override
    public boolean shouldRenderAtSqrDistance(final double distance) {
        return distance < 4096.0D * 4096.0D;
    }

    public Fp5FlamingoEntity(final EntityType<? extends Fp5FlamingoEntity> type, final Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.noPhysics = true;
        this.setNoGravity(true);
        updateBoundingBox();
    }

    public static Optional<Fp5FlamingoEntity> find(final ServerLevel level, final UUID flamingoId) {
        if (level == null || flamingoId == null) {
            return Optional.empty();
        }
        Entity entity = level.getEntity(flamingoId);
        if (entity == null) {
            final Optional<ChunkPos> lastChunk = ShahedLinkData.get(level).lastChunk(flamingoId);
            if (lastChunk.isPresent()) {
                final ChunkPos pos = lastChunk.get();
                level.getChunk(pos.x, pos.z);
                entity = level.getEntity(flamingoId);
            }
        }
        return entity instanceof Fp5FlamingoEntity flamingo && flamingo.isAlive()
            ? Optional.of(flamingo)
            : Optional.empty();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ON_LAUNCHER, false);
        entityData.define(DATA_LAUNCHER_ID, -1);
        entityData.define(DATA_SERVER_YAW, getYRot());
        entityData.define(DATA_SERVER_PITCH, getXRot());
        entityData.define(DATA_SERVER_ROLL, 0.0F);
        entityData.define(DATA_LAUNCHED, false);
        entityData.define(DATA_BOOSTER_ACTIVE, false);
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        final boolean onLauncher = tag.getBoolean(TAG_ON_LAUNCHER);
        entityData.set(DATA_ON_LAUNCHER, onLauncher);
        if (onLauncher && tag.hasUUID(TAG_LAUNCHER_UUID)) {
            mountedLauncherUuid = tag.getUUID(TAG_LAUNCHER_UUID);
            mountedLauncherId = -1;
        } else {
            mountedLauncherId = -1;
            mountedLauncherUuid = null;
        }

        launched = tag.getBoolean(TAG_LAUNCHED);
        hasTarget = tag.getBoolean(TAG_HAS_TARGET);
        if (hasTarget) {
            targetPos = new BlockPos(tag.getInt(TAG_TARGET_X), tag.getInt(TAG_TARGET_Y), tag.getInt(TAG_TARGET_Z));
        } else {
            targetPos = BlockPos.ZERO;
        }
        flightPhase = tag.getInt(TAG_FLIGHT_PHASE);
        cruiseAltitude = tag.getDouble(TAG_CRUISE_ALTITUDE);
        climbWaypointX = tag.getDouble(TAG_CLIMB_X);
        climbWaypointZ = tag.getDouble(TAG_CLIMB_Z);
        flightSpeed = tag.getDouble(TAG_FLIGHT_SPEED);
        launchOriginX = tag.getDouble(TAG_LAUNCH_ORIGIN_X);
        launchOriginZ = tag.getDouble(TAG_LAUNCH_ORIGIN_Z);
        launchLockedYaw = tag.getFloat(TAG_LAUNCH_LOCKED_YAW);
        if (tag.contains(TAG_LAUNCH_LOCKED_PITCH)) {
            launchLockedPitch = tag.getFloat(TAG_LAUNCH_LOCKED_PITCH);
        } else {
            launchLockedPitch = -MODEL_BASE_PITCH_DEGREES;
        }
        bodyRoll = tag.getFloat(TAG_BODY_ROLL);
        bodyRollO = bodyRoll;
        yawRate = tag.getFloat(TAG_YAW_RATE);
        launchTicks = tag.getInt(TAG_LAUNCH_TICKS);
        pitchRate = tag.getFloat(TAG_PITCH_RATE);
        lateralSlip = tag.getDouble(TAG_LATERAL_SLIP);

        entityData.set(DATA_LAUNCHED, launched);
        entityData.set(DATA_BOOSTER_ACTIVE, launched && launchTicks <= LAUNCH_RAIL_LOCK_TICKS);

        if (isOnLauncher()) {
            noPhysics = true;
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
        } else if (launched) {
            noPhysics = launchTicks <= LAUNCHER_CLEARANCE_TICKS;
            setNoGravity(true);
        }
        updateBoundingBox();
    }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {
        tag.putBoolean(TAG_ON_LAUNCHER, isOnLauncher());
        if (mountedLauncherUuid != null) {
            tag.putUUID(TAG_LAUNCHER_UUID, mountedLauncherUuid);
        }
        tag.putBoolean(TAG_LAUNCHED, launched);
        tag.putBoolean(TAG_HAS_TARGET, hasTarget);
        if (hasTarget) {
            tag.putInt(TAG_TARGET_X, targetPos.getX());
            tag.putInt(TAG_TARGET_Y, targetPos.getY());
            tag.putInt(TAG_TARGET_Z, targetPos.getZ());
        }
        tag.putInt(TAG_FLIGHT_PHASE, flightPhase);
        tag.putDouble(TAG_CRUISE_ALTITUDE, cruiseAltitude);
        tag.putDouble(TAG_CLIMB_X, climbWaypointX);
        tag.putDouble(TAG_CLIMB_Z, climbWaypointZ);
        tag.putDouble(TAG_FLIGHT_SPEED, flightSpeed);
        tag.putDouble(TAG_LAUNCH_ORIGIN_X, launchOriginX);
        tag.putDouble(TAG_LAUNCH_ORIGIN_Z, launchOriginZ);
        tag.putFloat(TAG_LAUNCH_LOCKED_YAW, launchLockedYaw);
        tag.putFloat(TAG_LAUNCH_LOCKED_PITCH, launchLockedPitch);
        tag.putFloat(TAG_BODY_ROLL, bodyRoll);
        tag.putFloat(TAG_YAW_RATE, yawRate);
        tag.putInt(TAG_LAUNCH_TICKS, launchTicks);
        tag.putFloat(TAG_PITCH_RATE, pitchRate);
        tag.putDouble(TAG_LATERAL_SLIP, lateralSlip);
    }

    @Override
    public void tick() {
        super.tick();
        yRotO = getYRot();
        xRotO = getXRot();
        bodyRollO = bodyRoll;

        if (level().isClientSide()) {
            handleClientSync();
            bodyRoll = entityData.get(DATA_SERVER_ROLL);
            if (isLaunched()) {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                    com.fullfud.fullfud.client.particle.Fp5ClientVfx.tick(this);
                });
            }
        }

        if (isOnLauncher()) {
            setDeltaMovement(Vec3.ZERO);
            final Fp5LauncherEntity launcher = resolveLauncher();
            if (launcher != null) {
                keepMountedOnLauncher(launcher);
            } else if (!level().isClientSide()) {
                dropAsItem();
                dropItemOnRemove = false;
                discard();
                return;
            }
            if (!level().isClientSide()) {
                handleClientSync();
                if (level() instanceof ServerLevel serverLevel) {
                    ShahedLinkData.get(serverLevel).updateChunk(getUUID(), chunkPosition());
                    ChunkLoadManager.ensureChunksLoaded(serverLevel, getId(), chunkPosition(), 1);
                }
            }
            updateBoundingBox();
            broadcastGhostState();
            return;
        }

        if (!level().isClientSide()) {
            final boolean hasMovement = getDeltaMovement().lengthSqr() > 0.0004D;
            final boolean activeFlight = launched || hasTarget;
            if (hasMovement || activeFlight) {
                stationaryTicks = 0;
            } else {
                stationaryTicks++;
            }
            if (level() instanceof ServerLevel serverLevel) {
                ShahedLinkData.get(serverLevel).updateChunk(getUUID(), chunkPosition());
                if (stationaryTicks < STATIONARY_UNLOAD_THRESHOLD_TICKS) {
                    ChunkLoadManager.ensureChunksLoaded(serverLevel, getId(), chunkPosition(), 3);
                } else {
                    ChunkLoadManager.releaseChunks(serverLevel, getId());
                }
            }
        }

        if (!level().isClientSide() && launched) {
            tickAutopilot();
            handleClientSync();
        }

        updateBoundingBox();
        broadcastGhostState();
    }

    private void broadcastGhostState() {
        if (!level().isClientSide() && tickCount % 4 == 0 && level() instanceof ServerLevel serverLevel) {
            final var packet = new com.fullfud.fullfud.core.network.packet.Fp5GhostUpdatePacket(
                getUUID(), getX(), getY(), getZ(), getYRot(), getXRot(), bodyRoll,
                isOnLauncher(), launched, isBoosterActive());
            for (final ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(this) <= 4096.0D * 4096.0D) {
                    com.fullfud.fullfud.core.network.FullfudNetwork.getChannel().send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
        }
    }

    public void applyClientGhostState(final double x, final double y, final double z,
                                      final float yaw, final float pitch, final float roll,
                                      final boolean onLauncher, final boolean launched, final boolean booster) {
        setPos(x, y, z);
        setRot(yaw, pitch);
        yRotO = yaw;
        xRotO = pitch;
        bodyRoll = roll;
        bodyRollO = roll;
        entityData.set(DATA_ON_LAUNCHER, onLauncher);
        entityData.set(DATA_LAUNCHED, launched);
        entityData.set(DATA_BOOSTER_ACTIVE, booster);
        updateBoundingBox();
    }

    @Override
    public void lerpTo(final double x,
                       final double y,
                       final double z,
                       final float yaw,
                       final float pitch,
                       final int posRotationIncrements,
                       final boolean teleport) {
        if (isOnLauncher()) {
            return;
        }
        if (teleport) {
            setPos(x, y, z);
            this.lerpSteps = 0;
        } else {
            this.lerpX = x;
            this.lerpY = y;
            this.lerpZ = z;
            this.lerpSteps = Math.max(1, posRotationIncrements);
        }
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand) {
        final ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof MonitorItem) {
            if (level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                MonitorItem.linkAndOpenFp5Monitor(serverPlayer, held, this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!isOnLauncher()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final Fp5LauncherEntity launcher = resolveLauncher();
        return launcher != null ? launcher.interact(player, hand) : InteractionResult.FAIL;
    }

    @Override
    public boolean hurt(final DamageSource source, final float amount) {
        if (level().isClientSide || !isAlive()) {
            return false;
        }
        if (launched) {
            detonate(position(), Vec3.ZERO);
            return true;
        }
        final Fp5LauncherEntity launcher = resolveLauncher();
        if (launcher != null) {
            launcher.onStoredFlamingoRemoved(this);
        }
        dropAsItem();
        dropItemOnRemove = false;
        discard();
        return true;
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            ChunkLoadManager.releaseChunks(serverLevel, getId());
        }
        if (!level().isClientSide && reason.shouldDestroy() && dropItemOnRemove) {
            final Fp5LauncherEntity launcher = resolveLauncher();
            if (launcher != null) {
                launcher.onStoredFlamingoRemoved(this);
            }
            dropAsItem();
            dropItemOnRemove = false;
        }
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    protected void checkFallDamage(final double y, final boolean onGround, final BlockState state, final BlockPos pos) {
    }

    public boolean isOnLauncher() {
        return entityData.get(DATA_ON_LAUNCHER);
    }

    public boolean isLaunched() {
        return entityData.get(DATA_LAUNCHED);
    }

    public boolean isBoosterActive() {
        return entityData.get(DATA_BOOSTER_ACTIVE);
    }

    public UUID getLauncherUuid() {
        return mountedLauncherUuid;
    }

    public BlockPos getMonitorTarget() {
        return hasTarget ? targetPos : blockPosition();
    }

    public void setMonitorTarget(final BlockPos target) {
        if (!launched && target != null) {
            targetPos = target.immutable();
            hasTarget = true;
        }
    }

    public void detonateMounted() {
        if (!level().isClientSide() && isOnLauncher() && isAlive()) {
            detonate(position());
        }
    }

    public void mountLauncher(final Fp5LauncherEntity launcher) {
        mountedLauncherId = launcher.getId();
        mountedLauncherUuid = launcher.getUUID();
        entityData.set(DATA_ON_LAUNCHER, true);
        entityData.set(DATA_LAUNCHER_ID, launcher.getId());
        entityData.set(DATA_LAUNCHED, false);
        entityData.set(DATA_BOOSTER_ACTIVE, false);
        launched = false;
        hasTarget = false;
        targetPos = BlockPos.ZERO;
        flightPhase = FLIGHT_PHASE_IDLE;
        cruiseAltitude = 0.0D;
        climbWaypointX = 0.0D;
        climbWaypointZ = 0.0D;
        flightSpeed = 0.0D;
        launchOriginX = getX();
        launchOriginZ = getZ();
        launchLockedYaw = getYRot();
        launchLockedPitch = -MODEL_BASE_PITCH_DEGREES;
        bodyRoll = 0.0F;
        bodyRollO = 0.0F;
        yawRate = 0.0F;
        pitchRate = 0.0F;
        lateralSlip = 0.0D;
        launchTicks = 0;
        keepMountedOnLauncher(launcher);
        handleClientSync();
    }

    public boolean launchToCoordinates(final BlockPos targetPos) {
        if (level().isClientSide() || !isAlive() || launched || !isOnLauncher() || targetPos == null) {
            return false;
        }

        final double dx = targetPos.getX() + 0.5D - getX();
        final double dz = targetPos.getZ() + 0.5D - getZ();
        if (dx * dx + dz * dz < MIN_LAUNCH_DISTANCE * MIN_LAUNCH_DISTANCE) {
            return false;
        }

        final Fp5LauncherEntity launcher = resolveLauncher();
        if (launcher != null) {
            launcher.onStoredFlamingoRemoved(this);
        }

        entityData.set(DATA_ON_LAUNCHER, false);
        entityData.set(DATA_LAUNCHER_ID, -1);
        entityData.set(DATA_LAUNCHED, true);
        entityData.set(DATA_BOOSTER_ACTIVE, true);
        mountedLauncherId = -1;
        mountedLauncherUuid = null;
        launched = true;
        hasTarget = true;
        this.targetPos = targetPos.immutable();
        launchOriginX = getX();
        launchOriginZ = getZ();
        launchLockedYaw = getYRot();
        launchLockedPitch = -MODEL_BASE_PITCH_DEGREES;
        bodyRoll = 0.0F;
        bodyRollO = 0.0F;
        yawRate = 0.0F;
        pitchRate = 0.0F;
        lateralSlip = 0.0D;
        launchTicks = 0;
        final double currentGroundY = sampleSurfaceY(getX(), getZ());
        flightPhase = FLIGHT_PHASE_CLIMB;
        cruiseAltitude = currentGroundY + CRUISE_TERRAIN_CLEARANCE;

        final Vec3 launchDirection = getCourseDirection();
        climbWaypointX = getX() + launchDirection.x * CLIMB_TARGET_FORWARD;
        climbWaypointZ = getZ() + launchDirection.z * CLIMB_TARGET_FORWARD;
        flightSpeed = 0.3D;

        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);

        level().playSound(
            null,
            getX(),
            getY(),
            getZ(),
            FullfudRegistries.FP5_LAUNCH_BOOST.get(),
            SoundSource.NEUTRAL,
            5.0F,
            1.0F
        );
        return true;
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        if (level().isClientSide()) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                com.fullfud.fullfud.client.sound.Fp5SoundHandler.stopForFlamingo(getUUID());
                com.fullfud.fullfud.client.particle.Fp5ClientVfx.remove(getUUID());
            });
        }
    }

    public ItemStack createItemStack() {
        return new ItemStack(FullfudRegistries.FP5_FLAMINGO_ITEM.get());
    }

    public void suppressItemDrop() {
        dropItemOnRemove = false;
    }

    private void keepMountedOnLauncher(final Fp5LauncherEntity launcher) {
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        bodyRoll = 0.0F;
        bodyRollO = 0.0F;
        yawRate = 0.0F;
        pitchRate = 0.0F;
        lateralSlip = 0.0D;
        launchTicks = 0;
        updateLauncherPose(launcher);
    }

    private void handleClientSync() {
        if (level() instanceof ServerLevel) {
            entityData.set(DATA_SERVER_YAW, getYRot());
            entityData.set(DATA_SERVER_PITCH, getXRot());
            entityData.set(DATA_SERVER_ROLL, bodyRoll);
            entityData.set(DATA_LAUNCHED, launched);
            entityData.set(DATA_BOOSTER_ACTIVE, launched && launchTicks <= LAUNCH_RAIL_LOCK_TICKS);
            return;
        }
        if (isOnLauncher()) {
            final Fp5LauncherEntity launcher = resolveLauncher();
            if (launcher != null) {
                updateLauncherPose(launcher);
            } else {
                final float serverYaw = entityData.get(DATA_SERVER_YAW);
                final float serverPitch = entityData.get(DATA_SERVER_PITCH);
                setYRot(serverYaw);
                setXRot(serverPitch);
                setYBodyRot(serverYaw);
                setYHeadRot(serverYaw);
                setOldPosAndRot();
            }
            bodyRoll = 0.0F;
            bodyRollO = 0.0F;
            lerpSteps = 0;
            return;
        }

        final float targetYaw = entityData.get(DATA_SERVER_YAW);
        final float targetPitch = entityData.get(DATA_SERVER_PITCH);
        final float diffY = Mth.wrapDegrees(targetYaw - getYRot());
        final float diffX = Mth.wrapDegrees(targetPitch - getXRot());

        if (Math.abs(diffY) > 90.0F || Math.abs(diffX) > 90.0F) {
            setYRot(targetYaw);
            setXRot(targetPitch);
        } else {
            setYRot(getYRot() + diffY * 0.45F);
            setXRot(getXRot() + diffX * 0.45F);
        }
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());

        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
            return;
        }
        if (lerpSteps <= 0) {
            return;
        }

        final double interpolatedX = getX() + (lerpX - getX()) / (double) lerpSteps;
        final double interpolatedY = getY() + (lerpY - getY()) / (double) lerpSteps;
        final double interpolatedZ = getZ() + (lerpZ - getZ()) / (double) lerpSteps;
        setPos(interpolatedX, interpolatedY, interpolatedZ);

        --lerpSteps;
    }

    @Override
    public void onSyncedDataUpdated(final EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide()) {
            if (DATA_SERVER_YAW.equals(key) || DATA_SERVER_PITCH.equals(key)) {
                if (isOnLauncher()) {
                    final float sy = entityData.get(DATA_SERVER_YAW);
                    final float sp = entityData.get(DATA_SERVER_PITCH);
                    setYRot(sy);
                    setXRot(sp);
                    setYBodyRot(sy);
                    setYHeadRot(sy);
                    yRotO = sy;
                    xRotO = sp;
                }
            }
        }
    }

    private Fp5LauncherEntity resolveLauncher() {
        final int syncedLauncherId = entityData.get(DATA_LAUNCHER_ID);
        if (syncedLauncherId > 0) {
            final Entity entity = level().getEntity(syncedLauncherId);
            if (entity instanceof Fp5LauncherEntity launcher && launcher.isAlive()) {
                mountedLauncherId = syncedLauncherId;
                return launcher;
            }
        }
        if (mountedLauncherId > 0) {
            final Entity entity = level().getEntity(mountedLauncherId);
            if (entity instanceof Fp5LauncherEntity launcher && launcher.isAlive()) {
                return launcher;
            }
            mountedLauncherId = -1;
        }
        if (mountedLauncherUuid != null && level() instanceof ServerLevel serverLevel) {
            final Entity entity = serverLevel.getEntity(mountedLauncherUuid);
            if (entity instanceof Fp5LauncherEntity launcher && launcher.isAlive()) {
                mountedLauncherId = launcher.getId();
                return launcher;
            }
            mountedLauncherUuid = null;
        }
        return null;
    }

    private void updateLauncherPose(final Fp5LauncherEntity launcher) {
        final float yaw = launcher.getYRot();
        final Vec3 forward = Vec3.directionFromRotation(0.0F, yaw).normalize();
        final Vec3 anchor = launcher.position()
            .add(0.0D, LAUNCHER_MOUNT_VERTICAL_OFFSET, 0.0D)
            .add(forward.scale(LAUNCHER_MOUNT_FORWARD_OFFSET));
        setPos(anchor.x, anchor.y, anchor.z);
        setCourseOrientation(yaw, -MODEL_BASE_PITCH_DEGREES);
        setOldPosAndRot();
        this.lerpSteps = 0;
    }

    private void tickAutopilot() {
        if (!hasTarget) {
            launched = false;
            entityData.set(DATA_LAUNCHED, false);
            entityData.set(DATA_BOOSTER_ACTIVE, false);
            flightPhase = FLIGHT_PHASE_IDLE;
            setDeltaMovement(Vec3.ZERO);
            yawRate = 0.0F;
            pitchRate = 0.0F;
            lateralSlip = 0.0D;
            launchTicks = 0;
            return;
        }

        launchTicks++;
        if (launchTicks >= MAX_FLIGHT_TICKS) {
            detonate(position(), Vec3.ZERO);
            return;
        }

        final boolean boosterActive = launchTicks <= LAUNCH_RAIL_LOCK_TICKS;
        if (entityData.get(DATA_BOOSTER_ACTIVE) != boosterActive) {
            entityData.set(DATA_BOOSTER_ACTIVE, boosterActive);
        }
        if (!entityData.get(DATA_LAUNCHED)) {
            entityData.set(DATA_LAUNCHED, true);
        }

        if (launchTicks <= LAUNCHER_CLEARANCE_TICKS) {
            this.noPhysics = true;
        } else {
            this.noPhysics = false;
        }

        final Vec3 currentPos = position();
        final Vec3 impactTarget = resolveImpactPoint();
        final Vec3 phaseTarget = resolvePhaseTarget(impactTarget);
        final Vec3 flightMotion = computeFlightMotion(currentPos, phaseTarget, impactTarget);
        spawnExhaustParticles();

        if (launchTicks > LAUNCHER_CLEARANCE_TICKS) {
            final double distFromOriginSqr = currentPos.distanceToSqr(launchOriginX, currentPos.y, launchOriginZ);
            if (distFromOriginSqr >= LAUNCHER_CLEARANCE_DISTANCE * LAUNCHER_CLEARANCE_DISTANCE) {
                final HitResult blockHit = level().clip(new ClipContext(
                    currentPos,
                    currentPos.add(flightMotion),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this
                ));
                if (blockHit.getType() != HitResult.Type.MISS) {
                    final net.minecraft.core.Direction hitDir = ((net.minecraft.world.phys.BlockHitResult) blockHit).getDirection();
                    detonate(blockHit.getLocation(), new Vec3(hitDir.getStepX(), hitDir.getStepY(), hitDir.getStepZ()));
                    return;
                }
            }

            // Entity collision raycasting along flight path vector
            final Vec3 nextPos = currentPos.add(flightMotion);
            final AABB sweptArea = getBoundingBox().expandTowards(flightMotion).inflate(1.0D);
            final List<Entity> candidates = level().getEntities(
                this,
                sweptArea,
                entity -> !entity.isSpectator() && entity.isPickable() && !entity.isPassengerOfSameVehicle(this)
            );

            Entity closestHitEntity = null;
            Vec3 closestHitPos = null;
            double closestDistanceSq = Double.MAX_VALUE;

            for (final Entity candidate : candidates) {
                if (candidate instanceof Fp5LauncherEntity && distFromOriginSqr < LAUNCHER_CLEARANCE_DISTANCE * LAUNCHER_CLEARANCE_DISTANCE) {
                    continue;
                }
                final AABB candidateAabb = candidate.getBoundingBox().inflate(0.3D);
                final Optional<Vec3> clip = candidateAabb.clip(currentPos, nextPos);
                if (clip.isPresent()) {
                    final double distSq = currentPos.distanceToSqr(clip.get());
                    if (distSq < closestDistanceSq) {
                        closestDistanceSq = distSq;
                        closestHitEntity = candidate;
                        closestHitPos = clip.get();
                    }
                }
            }

            if (closestHitEntity != null && closestHitPos != null) {
                if (DroneExplosionEffects.isSuperbWarfareVehicle(closestHitEntity)) {
                    DroneExplosionEffects.applyDirectImpactVehicleDamage(
                        (ServerLevel) level(),
                        this,
                        null,
                        closestHitEntity,
                        4500.0F
                    );
                }
                detonate(closestHitPos, null);
                return;
            }
        }

        move(MoverType.SELF, flightMotion);
        setDeltaMovement(flightMotion);

        if (launchTicks > LAUNCHER_CLEARANCE_TICKS) {
            if (this.horizontalCollision || this.verticalCollision || this.minorHorizontalCollision) {
                final Vec3 impactNormal = this.verticalCollision
                    ? (flightMotion.y < 0.0D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(0.0D, -1.0D, 0.0D))
                    : (flightMotion.lengthSqr() > 1.0E-4D ? flightMotion.normalize().scale(-1.0D) : new Vec3(0.0D, 1.0D, 0.0D));
                detonate(position(), impactNormal);
                return;
            }
            final double movedDistSq = position().distanceToSqr(currentPos);
            if (flightMotion.lengthSqr() > 0.25D && movedDistSq < 0.04D) {
                detonate(position(), new Vec3(0.0D, 1.0D, 0.0D));
                return;
            }
        }

        advanceFlightPhase(impactTarget);

        if (position().distanceTo(impactTarget) <= TERMINAL_DISTANCE_THRESHOLD) {
            detonate(position(), null);
            return;
        }

        if (hasReachedTerrain()) {
            detonate(new Vec3(getX(), sampleSurfaceY(getX(), getZ()), getZ()), new Vec3(0.0D, 1.0D, 0.0D));
            return;
        }

        if (getY() <= level().getMinBuildHeight() + 1) {
            detonate(position(), new Vec3(0.0D, 1.0D, 0.0D));
            return;
        }
    }


    private Vec3 resolvePhaseTarget(final Vec3 impactTarget) {
        if (!hasTarget) {
            return position();
        }
        return switch (flightPhase) {
            case FLIGHT_PHASE_CLIMB -> new Vec3(climbWaypointX, cruiseAltitude, climbWaypointZ);
            case FLIGHT_PHASE_CRUISE -> new Vec3(targetPos.getX() + 0.5D, cruiseAltitude, targetPos.getZ() + 0.5D);
            case FLIGHT_PHASE_TERMINAL -> impactTarget;
            default -> position();
        };
    }

    private Vec3 resolveImpactPoint() {
        final double targetX = targetPos.getX() + 0.5D;
        final double targetZ = targetPos.getZ() + 0.5D;
        return new Vec3(targetX, sampleSurfaceY(targetX, targetZ), targetZ);
    }

    private Vec3 computeFlightMotion(final Vec3 currentPos, final Vec3 phaseTarget, final Vec3 impactTarget) {
        final Vec3 delta = phaseTarget.subtract(currentPos);
        final boolean launchTurnLocked = isLaunchTurnLocked();
        final float targetYaw = Mth.wrapDegrees((float) (-(Mth.atan2(delta.x, delta.z) * Mth.RAD_TO_DEG)) + MODEL_FORWARD_YAW_OFFSET);
        final float desiredYaw = launchTurnLocked ? launchLockedYaw : targetYaw;
        final float desiredPitch = launchTurnLocked
            ? computeLaunchPitch(launchTicks)
            : switch (flightPhase) {
                case FLIGHT_PHASE_CLIMB -> computeAltitudeHoldPitch(cruiseAltitude - currentPos.y, CLIMB_ALTITUDE_RESPONSE_DISTANCE, 30.0F);
                case FLIGHT_PHASE_TERMINAL -> computeTerminalPitch(currentPos, impactTarget);
                default -> computeAltitudeHoldPitch(cruiseAltitude - currentPos.y, CRUISE_ALTITUDE_RESPONSE_DISTANCE, 12.0F);
            };

        final float currentYaw = getYRot();
        final float currentCoursePitch = getCoursePitch();
        final float turnAuthority = flightPhase == FLIGHT_PHASE_TERMINAL ? 1.0F : getTurnAuthority();
        final float pitchRateLimit = switch (flightPhase) {
            case FLIGHT_PHASE_CLIMB -> CLIMB_PITCH_RATE_LIMIT;
            case FLIGHT_PHASE_TERMINAL -> TERMINAL_PITCH_RATE_LIMIT;
            default -> CRUISE_PITCH_RATE_LIMIT;
        };
        final float bankLimit = switch (flightPhase) {
            case FLIGHT_PHASE_CLIMB -> CLIMB_BANK_LIMIT;
            case FLIGHT_PHASE_TERMINAL -> TERMINAL_BANK_LIMIT;
            default -> CRUISE_BANK_LIMIT;
        };
        final float yawError = Mth.wrapDegrees(desiredYaw - currentYaw);
        final float effectiveBankLimit = flightPhase == FLIGHT_PHASE_TERMINAL ? bankLimit : bankLimit * turnAuthority;
        final float targetBank = launchTurnLocked
            ? 0.0F
            : Mth.clamp(yawError * 0.5F, -effectiveBankLimit, effectiveBankLimit);
        final float nextRoll = launchTurnLocked ? 0.0F : (float) approachValue(bodyRoll, targetBank, BANK_RESPONSE_STEP);
        final float maxYawRate = switch (flightPhase) {
            case FLIGHT_PHASE_CLIMB -> MAX_CLIMB_YAW_RATE;
            case FLIGHT_PHASE_TERMINAL -> MAX_TERMINAL_YAW_RATE;
            default -> MAX_CRUISE_YAW_RATE;
        };
        final float yawAccelGain = switch (flightPhase) {
            case FLIGHT_PHASE_CLIMB -> CLIMB_YAW_ACCEL;
            case FLIGHT_PHASE_TERMINAL -> TERMINAL_YAW_ACCEL;
            default -> CRUISE_YAW_ACCEL;
        };
        final float yawDamping = switch (flightPhase) {
            case FLIGHT_PHASE_CLIMB -> CLIMB_YAW_DAMPING;
            case FLIGHT_PHASE_TERMINAL -> TERMINAL_YAW_DAMPING;
            default -> CRUISE_YAW_DAMPING;
        };
        if (launchTurnLocked) {
            yawRate = 0.0F;
        } else {
            final float yawAcceleration = (float) (Math.sin(Math.toRadians(nextRoll)) * yawAccelGain * Mth.clamp(flightSpeed / CRUISE_SPEED_TARGET, 0.45D, 1.2D) * turnAuthority);
            yawRate += yawAcceleration;
            yawRate -= yawRate * yawDamping;
            if (Math.abs(yawError) < 0.5F) {
                yawRate = (float) approachValue(yawRate, 0.0D, yawDamping);
            } else if (Math.signum(yawRate) != 0.0F && Math.signum(yawRate) != Math.signum(yawError)) {
                yawRate = (float) approachValue(yawRate, 0.0D, YAW_MISMATCH_BRAKE);
            }
        }
        yawRate = Mth.clamp(yawRate, -maxYawRate, maxYawRate);
        final float nextYaw = launchTurnLocked ? launchLockedYaw : currentYaw + yawRate;

        final float nextPitch;
        if (launchTurnLocked) {
            pitchRate = 0.0F;
            nextPitch = computeLaunchPitch(launchTicks);
        } else {
            final float pitchError = desiredPitch - currentCoursePitch;
            final float desiredPitchRate = Mth.clamp(pitchError * 0.08F, -pitchRateLimit, pitchRateLimit);
            final float pitchRateResponse = flightPhase == FLIGHT_PHASE_TERMINAL ? PITCH_RATE_RESPONSE * 1.25F : PITCH_RATE_RESPONSE;
            pitchRate = (float) approachValue(pitchRate, desiredPitchRate, pitchRateResponse);
            if (Math.abs(pitchError) < 0.25F) {
                pitchRate = (float) approachValue(pitchRate, 0.0D, pitchRateResponse * 0.5F);
            }
            nextPitch = Mth.clamp(currentCoursePitch + pitchRate, -85.0F, 85.0F);
        }
        bodyRoll = nextRoll;
        setCourseOrientation(nextYaw, nextPitch);

        final double speedTarget = resolveSpeedTarget();
        flightSpeed = approachValue(flightSpeed, speedTarget, computeSpeedStep(speedTarget));
        final Vec3 forward = Vec3.directionFromRotation(nextPitch, nextYaw + MODEL_FORWARD_YAW_OFFSET);
        final Vec3 right = Vec3.directionFromRotation(0.0F, nextYaw + MODEL_FORWARD_YAW_OFFSET - 90.0F);
        final double slipLimit = switch (flightPhase) {
            case FLIGHT_PHASE_CLIMB -> CLIMB_LATERAL_SLIP_LIMIT;
            case FLIGHT_PHASE_TERMINAL -> TERMINAL_LATERAL_SLIP_LIMIT;
            default -> CRUISE_LATERAL_SLIP_LIMIT;
        };
        final double bankRadians = Math.toRadians(nextRoll);
        final double desiredLateralSlip = launchTurnLocked
            ? 0.0D
            : Mth.clamp(Math.sin(bankRadians) * slipLimit * Mth.clamp(flightSpeed / CRUISE_SPEED_TARGET, 0.45D, 1.25D) * turnAuthority, -slipLimit, slipLimit);
        final double slipStep = Math.abs(desiredLateralSlip) > Math.abs(lateralSlip) ? SLIP_BUILD_STEP : SLIP_DECAY_STEP;
        lateralSlip = launchTurnLocked ? 0.0D : approachValue(lateralSlip, desiredLateralSlip, slipStep);
        final Vec3 desiredMotion = forward.add(right.scale(lateralSlip)).normalize();
        final Vec3 previousMotion = getDeltaMovement().lengthSqr() > 1.0E-6D ? getDeltaMovement().normalize() : desiredMotion;
        final double steerBlend = launchTurnLocked
            ? 1.0D
            : (flightPhase == FLIGHT_PHASE_TERMINAL ? TERMINAL_STEER_BLEND : Mth.lerp(turnAuthority, 0.03D, CRUISE_STEER_BLEND));
        return previousMotion.lerp(desiredMotion, steerBlend).normalize().scale(flightSpeed);
    }

    public static final float LAUNCH_STEEP_CLIMB_PITCH = -46.0F;

    public float computeLaunchPitch(final int ticks) {
        if (ticks <= 12) {
            final float p = (float) ticks / 12.0F;
            return Mth.lerp(p, -MODEL_BASE_PITCH_DEGREES, LAUNCH_STEEP_CLIMB_PITCH);
        }
        if (ticks <= 50) {
            return LAUNCH_STEEP_CLIMB_PITCH;
        }
        if (ticks <= LAUNCH_RAIL_LOCK_TICKS) {
            final float p = (float) (ticks - 50) / (float) (LAUNCH_RAIL_LOCK_TICKS - 50);
            final float smoothP = p * p * (3.0F - 2.0F * p);
            return Mth.lerp(smoothP, LAUNCH_STEEP_CLIMB_PITCH, 0.0F);
        }
        return 0.0F;
    }

    private void advanceFlightPhase(final Vec3 impactTarget) {
        if (!hasTarget) {
            return;
        }
        if (launchTicks <= LAUNCH_RAIL_LOCK_TICKS) {
            return;
        }
        if (flightPhase == FLIGHT_PHASE_CLIMB) {
            final Vec3 climbTarget = new Vec3(climbWaypointX, cruiseAltitude, climbWaypointZ);
            if (position().distanceTo(climbTarget) <= CLIMB_PHASE_THRESHOLD || getY() >= cruiseAltitude - 1.0D) {
                cruiseAltitude = Math.max(cruiseAltitude, getY());
                flightPhase = FLIGHT_PHASE_CRUISE;
            }
        } else if (flightPhase == FLIGHT_PHASE_CRUISE) {
            final Vec3 forward = getCourseDirection();
            final double peakSurfaceY = sampleAheadSurfaceY(position(), forward);
            final double targetAltitude = peakSurfaceY + CRUISE_TERRAIN_CLEARANCE;
            if (targetAltitude > cruiseAltitude) {
                cruiseAltitude = approachValue(cruiseAltitude, targetAltitude, 0.45D);
            } else {
                cruiseAltitude = approachValue(cruiseAltitude, targetAltitude, 0.20D);
            }

            if (isLaunchTurnLocked()) {
                return;
            }
            final double dx = impactTarget.x - getX();
            final double dz = impactTarget.z - getZ();
            final double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance <= TERMINAL_APPROACH_DISTANCE) {
                flightPhase = FLIGHT_PHASE_TERMINAL;
            }
        }
    }

    private double sampleAheadSurfaceY(final Vec3 currentPos, final Vec3 forwardDir) {
        final double h0 = sampleSurfaceY(currentPos.x, currentPos.z);
        final double h1 = sampleSurfaceY(currentPos.x + forwardDir.x * 40.0D, currentPos.z + forwardDir.z * 40.0D);
        final double h2 = sampleSurfaceY(currentPos.x + forwardDir.x * 80.0D, currentPos.z + forwardDir.z * 80.0D);
        final double h3 = sampleSurfaceY(currentPos.x + forwardDir.x * 130.0D, currentPos.z + forwardDir.z * 130.0D);
        final double h4 = sampleSurfaceY(currentPos.x + forwardDir.x * 200.0D, currentPos.z + forwardDir.z * 200.0D);
        return Math.max(h0, Math.max(Math.max(h1, h2), Math.max(h3, h4)));
    }

    private float computeAltitudeHoldPitch(final double altitudeError, final double responseDistance, final float maxPitch) {
        final float desiredPitch = (float) (-(Mth.atan2(altitudeError, Math.max(responseDistance, 1.0D)) * Mth.RAD_TO_DEG));
        return Mth.clamp(desiredPitch, -maxPitch, maxPitch);
    }

    private float computeTerminalPitch(final Vec3 currentPos, final Vec3 impactTarget) {
        final Vec3 delta = impactTarget.subtract(currentPos);
        final double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        final float impactPitch = (float) (-(Mth.atan2(delta.y, Math.max(horizontalDistance, 1.0E-6D)) * Mth.RAD_TO_DEG));
        return Mth.clamp(impactPitch, 0.0F, TERMINAL_MAX_PITCH);
    }

    private boolean hasReachedTerrain() {
        if (flightPhase != FLIGHT_PHASE_TERMINAL) {
            return false;
        }
        if (getDeltaMovement().y > -0.01D) {
            return false;
        }
        return getY() <= sampleSurfaceY(getX(), getZ()) + TERRAIN_IMPACT_BUFFER;
    }

    private double sampleSurfaceY(final double x, final double z) {
        final int surfaceY = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        return surfaceY + TERMINAL_GROUND_OFFSET;
    }

    private boolean isLaunchTurnLocked() {
        if (flightPhase == FLIGHT_PHASE_TERMINAL) {
            return false;
        }
        return launchTicks <= LAUNCH_RAIL_LOCK_TICKS;
    }

    public float getTurnAuthority() {
        if (launchTicks <= LAUNCH_RAIL_LOCK_TICKS) {
            return 0.0F;
        }
        if (launchTicks >= LAUNCH_RAIL_LOCK_TICKS + BOOSTER_TRANSITION_TICKS) {
            return 1.0F;
        }
        final float s = (float) (launchTicks - LAUNCH_RAIL_LOCK_TICKS) / (float) BOOSTER_TRANSITION_TICKS;
        return s * s * (3.0F - 2.0F * s);
    }

    public float getVisualRoll(final float partialTick) {
        return Mth.lerp(partialTick, bodyRollO, bodyRoll);
    }

    public double resolveSpeedTarget() {
        if (launchTicks <= BOOSTER_KICK_TICKS) {
            return BOOSTER_PEAK_SPEED;
        }
        if (launchTicks <= BOOSTER_BURN_TICKS) {
            return BOOSTER_SUSTAIN_SPEED;
        }
        final double phaseTarget = switch (flightPhase) {
            case FLIGHT_PHASE_TERMINAL -> resolveTerminalSpeedTarget();
            default -> CRUISE_SPEED_TARGET;
        };
        if (launchTicks <= BOOSTER_BURN_TICKS + BOOSTER_TRANSITION_TICKS) {
            final double s = (double) (launchTicks - BOOSTER_BURN_TICKS) / (double) BOOSTER_TRANSITION_TICKS;
            final double h = s * s * (3.0D - 2.0D * s);
            return Mth.lerp(h, BOOSTER_SUSTAIN_SPEED, phaseTarget);
        }
        return phaseTarget;
    }

    private double computeSpeedStep(final double speedTarget) {
        if (launchTicks <= BOOSTER_KICK_TICKS) {
            return BOOSTER_ACCEL_STEP;
        }
        if (launchTicks <= BOOSTER_BURN_TICKS) {
            return BOOSTER_SUSTAIN_STEP;
        }
        if (launchTicks <= BOOSTER_BURN_TICKS + BOOSTER_TRANSITION_TICKS) {
            return BOOSTER_TRANSITION_STEP;
        }
        return Mth.clamp(Math.abs(speedTarget - flightSpeed) * 0.09D, MIN_SPEED_STEP, MAX_SPEED_STEP);
    }

    private double resolveTerminalSpeedTarget() {
        final double diveFactor = Mth.clamp(getCoursePitch() / 25.0D, 0.0D, 1.0D);
        return Mth.lerp(diveFactor, TERMINAL_SPEED_TARGET, TERMINAL_SPEED_TARGET + 0.35D);
    }

    private void spawnExhaustParticles() {
        // Contrails are rendered locally on client-side via Fp5ClientVfx and DroneParticleManager
        // using OCP textures up to 512m render distance without server network packet bottlenecks.
    }

    public float getCoursePitch() {
        return getXRot() - MODEL_BASE_PITCH_DEGREES;
    }

    public Vec3 getCourseDirection() {
        final float motionYaw = getYRot() + MODEL_FORWARD_YAW_OFFSET;
        final Vec3 forward = Vec3.directionFromRotation(getCoursePitch(), motionYaw);
        return forward.lengthSqr() > 1.0E-6D ? forward.normalize() : Vec3.directionFromRotation(-MODEL_BASE_PITCH_DEGREES, motionYaw).normalize();
    }

    private void setCourseOrientation(final float yaw, final float pitch) {
        final float entityPitch = Mth.clamp(pitch + MODEL_BASE_PITCH_DEGREES, -89.0F, 89.0F);
        setRot(yaw, entityPitch);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void detonate(final Vec3 impactPos) {
        detonate(impactPos, null);
    }

    private void detonate(final Vec3 impactPos, @javax.annotation.Nullable final Vec3 explicitNormal) {
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        dropItemOnRemove = false;
        launched = false;
        entityData.set(DATA_ON_LAUNCHER, false);
        entityData.set(DATA_LAUNCHED, false);
        entityData.set(DATA_BOOSTER_ACTIVE, false);
        mountedLauncherId = -1;
        mountedLauncherUuid = null;
        setPos(impactPos.x, impactPos.y, impactPos.z);
        spawnTntEffect(explicitNormal);
        discard();
    }

    private void spawnTntEffect(@javax.annotation.Nullable final Vec3 explicitNormal) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        DroneExplosionEffects.afterFlamingoExplosion(serverLevel, this, null, getDeltaMovement(), explicitNormal);
    }

    private void dropAsItem() {
        spawnAtLocation(createItemStack());
    }

    private void updateBoundingBox() {
        final float halfWidth = HITBOX_WIDTH * 0.5F;
        final float halfLength = HITBOX_LENGTH * 0.5F;
        final Vec3 center = position();
        final double yawRad = Math.toRadians(getYRot());
        final double cos = Math.cos(yawRad);
        final double sin = Math.sin(yawRad);
        final Vec3[] corners = new Vec3[] {
            new Vec3(-halfWidth, 0.0D, -halfLength),
            new Vec3(halfWidth, 0.0D, -halfLength),
            new Vec3(halfWidth, 0.0D, halfLength),
            new Vec3(-halfWidth, 0.0D, halfLength)
        };
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (final Vec3 corner : corners) {
            final double rotX = corner.x * cos - corner.z * sin;
            final double rotZ = corner.x * sin + corner.z * cos;
            minX = Math.min(minX, center.x + rotX);
            minZ = Math.min(minZ, center.z + rotZ);
            maxX = Math.max(maxX, center.x + rotX);
            maxZ = Math.max(maxZ, center.z + rotZ);
        }
        setBoundingBox(new AABB(minX, getY(), minZ, maxX, getY() + HITBOX_HEIGHT, maxZ));
    }

    @Override
    public EntityDimensions getDimensions(final Pose pose) {
        return FLAMINGO_SIZE;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(1.0D * SCALE, 0.5D * SCALE, 1.0D * SCALE);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fp5_flamingo", 0, state -> {
            state.setAndContinue(IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    private static double approachValue(final double current, final double target, final double maxStep) {
        if (current < target) {
            return Math.min(current + maxStep, target);
        }
        return Math.max(current - maxStep, target);
    }
}
