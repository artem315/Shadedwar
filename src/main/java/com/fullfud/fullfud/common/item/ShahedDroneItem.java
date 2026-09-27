package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.ShahedColor;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public class ShahedDroneItem extends Item {
    private final ShahedColor color;
    private final double speedScale;
    private final Supplier<? extends EntityType<? extends ShahedDroneEntity>> entityTypeSupplier;

    public ShahedDroneItem(final Properties properties, final ShahedColor color) {
        this(properties, color, 1.0D, FullfudRegistries.SHAHED_ENTITY);
    }

    public ShahedDroneItem(final Properties properties, final ShahedColor color, final double speedScale) {
        this(properties, color, speedScale, FullfudRegistries.SHAHED_ENTITY);
    }

    public ShahedDroneItem(final Properties properties, final ShahedColor color, final Supplier<? extends EntityType<? extends ShahedDroneEntity>> entityTypeSupplier) {
        this(properties, color, 1.0D, entityTypeSupplier);
    }

    public ShahedDroneItem(final Properties properties, final ShahedColor color, final double speedScale, final Supplier<? extends EntityType<? extends ShahedDroneEntity>> entityTypeSupplier) {
        super(properties);
        this.color = color;
        this.speedScale = speedScale;
        this.entityTypeSupplier = entityTypeSupplier;
    }

    public ShahedDroneEntity createDrone(final ServerLevel level) {
        return this.entityTypeSupplier != null ? this.entityTypeSupplier.get().create(level) : FullfudRegistries.SHAHED_ENTITY.get().create(level);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        return InteractionResult.PASS;
    }

    private static void linkWithMonitor(final Player player, final ShahedDroneEntity drone) {
        final ItemStack mainHand = player.getMainHandItem();
        final ItemStack offHand = player.getOffhandItem();

        if (mainHand.getItem() instanceof MonitorItem) {
            MonitorItem.setLinkedDrone(mainHand, drone.getUUID());
            if (player instanceof ServerPlayer serverPlayer) {
                drone.assignOwner(serverPlayer);
            }
            player.displayClientMessage(Component.translatable("message.fullfud.monitor.linked"), false);
            return;
        }

        if (offHand.getItem() instanceof MonitorItem) {
            MonitorItem.setLinkedDrone(offHand, drone.getUUID());
            if (player instanceof ServerPlayer serverPlayer) {
                drone.assignOwner(serverPlayer);
            }
            player.displayClientMessage(Component.translatable("message.fullfud.monitor.linked"), false);
        }
    }

    public ShahedColor getColor() {
        return color;
    }

    public double getSpeedScale() {
        return speedScale;
    }
}
