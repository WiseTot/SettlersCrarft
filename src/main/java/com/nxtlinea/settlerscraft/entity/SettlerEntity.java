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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettlerEntity extends PathAwareEntity {

    private static final int WANDER_RADIUS = 10;
    private static final int WANDER_WAIT_TICKS = 40;
    private static final int BUILD_STEP_DELAY_TICKS = 10;
    private static final int NO_STORAGE_RETRY_TICKS = 60;
    private static final int NO_MATERIAL_RETRY_TICKS = 60;

    private static final int CARRY_CAPACITY_PER_TYPE = 32;
    private static final int MAX_CARRIED_TYPES = 5;

    private SettlerState state = SettlerState.IDLE;
    private SettlerWalkPurpose walkPurpose = SettlerWalkPurpose.WANDER;
    private int waitTicksRemaining = 0;

    private BlockPos buildOrigin = null;
    private List<BlueprintData.BlockPlacement> currentBuildQueue = null;

    private BlockPos storageTarget = null;

    private final Map<Item, Integer> carriedItems = new LinkedHashMap<>();

    private boolean returningExcess = false;

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
                this.carriedItems.clear();
                return;
            }

            startWandering();
            return;
        }

        if (this.currentBuildQueue.isEmpty()) {
            if (!this.carriedItems.isEmpty()) {
                goToStorage(true);
                return;
            }

            this.buildOrigin = null;
            this.currentBuildQueue = null;
            return;
        }

        Item neededItem = this.currentBuildQueue.get(0).state().getBlock().asItem();

        if (this.carriedItems.getOrDefault(neededItem, 0) > 0) {
            this.walkPurpose = SettlerWalkPurpose.TO_SITE;
            this.getNavigation().startMovingTo(
                    this.buildOrigin.getX() + 0.5, this.buildOrigin.getY(), this.buildOrigin.getZ() + 0.5, 0.6D
            );
            this.state = SettlerState.WALKING;
            return;
        }

        goToStorage(false);
    }

    private void goToStorage(boolean toDeposit) {
        BlockPos nearestStorage = StorageManager.findNearest(this.getWorld(), this.getBlockPos());

        if (nearestStorage == null) {
            this.waitTicksRemaining = NO_STORAGE_RETRY_TICKS;
            this.state = SettlerState.WAITING;
            return;
        }

        this.storageTarget = nearestStorage;
        this.returningExcess = toDeposit;
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

        if (!(blockEntity instanceof StorageBlockEntity storage)) {
            this.waitTicksRemaining = NO_STORAGE_RETRY_TICKS;
            this.state = SettlerState.WAITING;
            return;
        }

        if (this.returningExcess) {
            depositAllCarriedItems(storage);
            this.returningExcess = false;
            this.waitTicksRemaining = 5;
            this.state = SettlerState.WAITING;
            return;
        }

        if (this.currentBuildQueue == null || this.currentBuildQueue.isEmpty()) {
            this.waitTicksRemaining = NO_STORAGE_RETRY_TICKS;
            this.state = SettlerState.WAITING;
            return;
        }

        Set<Item> typesToFetch = new LinkedHashSet<>();

        for (BlueprintData.BlockPlacement placement : this.currentBuildQueue) {
            Item item = placement.state().getBlock().asItem();

            if (this.carriedItems.getOrDefault(item, 0) > 0) {
                continue;
            }

            typesToFetch.add(item);

            if (this.carriedItems.size() + typesToFetch.size() >= MAX_CARRIED_TYPES) {
                break;
            }
        }

        boolean gotAnything = false;

        for (Item item : typesToFetch) {
            int extracted = storage.extractItem(item, CARRY_CAPACITY_PER_TYPE);
            if (extracted > 0) {
                this.carriedItems.merge(item, extracted, Integer::sum);
                gotAnything = true;
            }
        }

        this.waitTicksRemaining = gotAnything ? 5 : NO_MATERIAL_RETRY_TICKS;
        this.state = SettlerState.WAITING;
    }

    private void depositAllCarriedItems(StorageBlockEntity storage) {
        Iterator<Map.Entry<Item, Integer>> iterator = this.carriedItems.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Item, Integer> entry = iterator.next();
            ItemStack leftover = storage.insertStack(new ItemStack(entry.getKey(), entry.getValue()));

            if (leftover.isEmpty()) {
                iterator.remove();
            } else {
                entry.setValue(leftover.getCount());
            }
        }
    }

    private void handleBuilding() {
        if (this.currentBuildQueue != null && !this.currentBuildQueue.isEmpty()) {
            BlueprintData.BlockPlacement next = this.currentBuildQueue.get(0);
            Item neededItem = next.state().getBlock().asItem();
            int have = this.carriedItems.getOrDefault(neededItem, 0);

            if (have > 0) {
                BlockPos worldPos = this.buildOrigin.add(next.relativePos());
                this.getWorld().setBlockState(worldPos, next.state());
                this.currentBuildQueue.remove(0);

                if (have - 1 <= 0) {
                    this.carriedItems.remove(neededItem);
                } else {
                    this.carriedItems.put(neededItem, have - 1);
                }
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