package com.nxtlinea.settlerscraft.entity;

import com.nxtlinea.settlerscraft.block.StorageBlockEntity;
import com.nxtlinea.settlerscraft.building.BlueprintData;
import com.nxtlinea.settlerscraft.building.ConstructionManager;
import com.nxtlinea.settlerscraft.building.ModBlueprints;
import com.nxtlinea.settlerscraft.building.StorageManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SettlerEntity extends PathAwareEntity {

    private static final int WANDER_RADIUS = 10;
    private static final int WANDER_WAIT_TICKS = 40;
    private static final int BUILD_STEP_DELAY_TICKS = 10;
    private static final int NO_STORAGE_RETRY_TICKS = 60;
    private static final int NO_MATERIAL_RETRY_TICKS = 60;
    private static final int CARRY_CAPACITY = 16;

    private SettlerState state = SettlerState.IDLE;
    private SettlerWalkPurpose walkPurpose = SettlerWalkPurpose.WANDER;
    private int waitTicksRemaining = 0;

    private BlockPos buildOrigin = null;
    private List<BlueprintData.BlockPlacement> currentBuildQueue = null;

    private BlockPos storageTarget = null;
    private Item carriedItem = null;
    private int carriedCount = 0;

    public SettlerEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            updateFsm();
        }
    }

    private void updateFsm() {
        switch (this.state) {
            case IDLE -> handleIdle();
            case WALKING -> handleWalking();
            case GATHERING -> handleGathering();
            case BUILDING -> handleBuilding();
            case WAITING -> handleWaiting();
        }
    }

    private void handleIdle() {
        if (this.currentBuildQueue == null) {
            BlockPos pendingSite = ConstructionManager.pollNextSite();

            if (pendingSite != null && ModBlueprints.STARTER_HOUSE != null) {
                this.buildOrigin = pendingSite;
                this.currentBuildQueue = new ArrayList<>(ModBlueprints.STARTER_HOUSE.getPlacements());
                this.carriedItem = null;
                this.carriedCount = 0;
                // не двигаемся в этом тике, следующий вызов handleIdle разберётся, что делать дальше
                return;
            }

            startWandering();
            return;
        }

        if (this.currentBuildQueue.isEmpty()) {
            this.buildOrigin = null;
            this.currentBuildQueue = null;
            this.carriedItem = null;
            this.carriedCount = 0;
            return;
        }

        Item neededItem = this.currentBuildQueue.get(0).state().getBlock().asItem();

        if (this.carriedItem == neededItem && this.carriedCount > 0) {
            this.walkPurpose = SettlerWalkPurpose.TO_SITE;
            this.getNavigation().startMovingTo(
                    this.buildOrigin.getX() + 0.5, this.buildOrigin.getY(), this.buildOrigin.getZ() + 0.5, 0.6D
            );
            this.state = SettlerState.WALKING;
            return;
        }

        BlockPos nearestStorage = StorageManager.findNearest(this.getWorld(), this.getBlockPos());

        if (nearestStorage == null) {
            this.waitTicksRemaining = NO_STORAGE_RETRY_TICKS;
            this.state = SettlerState.WAITING;
            return;
        }

        this.storageTarget = nearestStorage;
        this.walkPurpose = SettlerWalkPurpose.TO_STORAGE;
        this.getNavigation().startMovingTo(
                nearestStorage.getX() + 0.5, nearestStorage.getY(), nearestStorage.getZ() + 0.5, 0.6D
        );
        this.state = SettlerState.WALKING;
    }

    private void startWandering() {
        double offsetX = (this.random.nextDouble() * 2.0 - 1.0) * WANDER_RADIUS;
        double offsetZ = (this.random.nextDouble() * 2.0 - 1.0) * WANDER_RADIUS;
        this.getNavigation().startMovingTo(this.getX() + offsetX, this.getY(), this.getZ() + offsetZ, 0.6D);
        this.walkPurpose = SettlerWalkPurpose.WANDER;
        this.state = SettlerState.WALKING;
    }

    private void handleWalking() {
        if (!this.getNavigation().isIdle()) {
            return;
        }

        switch (this.walkPurpose) {
            case WANDER -> {
                this.waitTicksRemaining = WANDER_WAIT_TICKS;
                this.state = SettlerState.WAITING;
            }
            case TO_STORAGE -> this.state = SettlerState.GATHERING;
            case TO_SITE -> this.state = SettlerState.BUILDING;
        }
    }

    private void handleGathering() {
        BlockEntity blockEntity = this.storageTarget != null ? this.getWorld().getBlockEntity(this.storageTarget) : null;

        if (!(blockEntity instanceof StorageBlockEntity storage) || this.currentBuildQueue == null || this.currentBuildQueue.isEmpty()) {
            this.waitTicksRemaining = NO_STORAGE_RETRY_TICKS;
            this.state = SettlerState.WAITING;
            return;
        }

        Item neededItem = this.currentBuildQueue.get(0).state().getBlock().asItem();
        int neededCount = countLeadingSameType(this.currentBuildQueue, neededItem, CARRY_CAPACITY);

        int extracted = storage.extractItem(neededItem, neededCount);

        if (extracted > 0) {
            this.carriedItem = neededItem;
            this.carriedCount = extracted;
            this.waitTicksRemaining = 5;
        } else {
            this.waitTicksRemaining = NO_MATERIAL_RETRY_TICKS;
        }

        this.state = SettlerState.WAITING;
    }

    private void handleBuilding() {
        if (this.currentBuildQueue != null && !this.currentBuildQueue.isEmpty()
                && this.carriedItem != null && this.carriedCount > 0) {

            BlueprintData.BlockPlacement next = this.currentBuildQueue.get(0);

            if (next.state().getBlock().asItem() == this.carriedItem) {
                BlockPos worldPos = this.buildOrigin.add(next.relativePos());
                this.getWorld().setBlockState(worldPos, next.state());
                this.currentBuildQueue.remove(0);
                this.carriedCount--;
            }
        }

        this.waitTicksRemaining = BUILD_STEP_DELAY_TICKS;
        this.state = SettlerState.WAITING;
    }

    private void handleWaiting() {
        this.waitTicksRemaining--;
        if (this.waitTicksRemaining > 0) {
            return;
        }
        this.state = SettlerState.IDLE;
    }

    /**
     * Считает, сколько предметов данного типа идёт подряд с начала очереди построения
     * (не более cap) — именно столько имеет смысл принести за одну ходку.
     */
    private int countLeadingSameType(List<BlueprintData.BlockPlacement> queue, Item item, int cap) {
        int count = 0;
        for (BlueprintData.BlockPlacement placement : queue) {
            if (placement.state().getBlock().asItem() != item) {
                break;
            }
            count++;
            if (count >= cap) {
                break;
            }
        }
        return count;
    }

    public static DefaultAttributeContainer.Builder createSettlerAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0D);
    }

    @Nullable
    @Override
    public net.minecraft.entity.EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable net.minecraft.entity.EntityData entityData, @Nullable NbtCompound entityNbt) {
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }
}