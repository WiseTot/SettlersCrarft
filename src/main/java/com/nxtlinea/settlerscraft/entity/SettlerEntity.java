package com.nxtlinea.settlerscraft.entity;

import com.nxtlinea.settlerscraft.block.StorageBlockEntity;
import com.nxtlinea.settlerscraft.building.BlueprintData;
import com.nxtlinea.settlerscraft.building.ConstructionManager;
import com.nxtlinea.settlerscraft.building.ModBlueprints;
import com.nxtlinea.settlerscraft.building.RoadManager;
import com.nxtlinea.settlerscraft.building.StorageManager;
import com.nxtlinea.settlerscraft.entity.profession.ProfessionBehavior;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
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

    private static final int CARRY_CAPACITY_PER_TYPE = 64;
    private static final int MAX_CARRIED_TYPES = 5;

    private static final double ARRIVAL_DISTANCE_SQ = 4.0; // ~2 блока
    private static final double WORK_REACH_DISTANCE_SQ = 36.0; // ~6 блоков — условная "длина руки" для работы (рубка и т.д.)

    private static final TrackedData<ItemStack> MISSING_ITEM =
            DataTracker.registerData(SettlerEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    private SettlerState state = SettlerState.IDLE;
    private SettlerWalkPurpose walkPurpose = SettlerWalkPurpose.WANDER;
    private int waitTicksRemaining = 0;

    private BlockPos buildOrigin = null;
    private List<BlueprintData.BlockPlacement> currentBuildQueue = null;

    private BlockPos storageTarget = null;

    private final Map<Item, Integer> carriedItems = new LinkedHashMap<>();

    private boolean returningExcess = false;

    private Profession profession = Profession.NONE;
    private List<BlockPos> workQueue = null;

    public SettlerEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(MISSING_ITEM, ItemStack.EMPTY);
    }

    public ItemStack getMissingItem() {
        return this.dataTracker.get(MISSING_ITEM);
    }

    public Profession getProfession() {
        return this.profession;
    }

    private void setMissingItem(Item item) {
        this.dataTracker.set(MISSING_ITEM, new ItemStack(item));
    }

    private void clearMissingItem() {
        if (!this.dataTracker.get(MISSING_ITEM).isEmpty()) {
            this.dataTracker.set(MISSING_ITEM, ItemStack.EMPTY);
        }
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
            case WORKING -> handleWorking();
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

            List<BlockPos> pendingRoad = RoadManager.pollNextRoad();

            if (pendingRoad != null) {
                this.buildOrigin = BlockPos.ORIGIN;
                this.currentBuildQueue = new ArrayList<>();
                for (BlockPos pos : pendingRoad) {
                    this.currentBuildQueue.add(new BlueprintData.BlockPlacement(pos, Blocks.COBBLESTONE.getDefaultState()));
                }
                this.carriedItems.clear();
                return;
            }

            ProfessionBehavior behavior = this.profession.getBehavior();
            if (behavior != null && behavior.tryStartWork(this)) {
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
            clearMissingItem();
            return;
        }

        Item neededItem = this.currentBuildQueue.get(0).state().getBlock().asItem();

        if (this.carriedItems.getOrDefault(neededItem, 0) > 0) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(neededItem));
            clearMissingItem();

            BlockPos nextBlockPos = this.buildOrigin.add(this.currentBuildQueue.get(0).relativePos());
            this.walkPurpose = SettlerWalkPurpose.TO_SITE;
            this.getNavigation().startMovingTo(
                    nextBlockPos.getX() + 0.5, nextBlockPos.getY(), nextBlockPos.getZ() + 0.5, 0.6D
            );
            this.state = SettlerState.WALKING;
            return;
        }

        goToStorage(false);
    }

    private void goToStorage(boolean toDeposit) {
        BlockPos nearestStorage = StorageManager.findNearest(this.getWorld(), this.getBlockPos());

        if (nearestStorage == null) {
            if (!toDeposit && this.currentBuildQueue != null && !this.currentBuildQueue.isEmpty()) {
                setMissingItem(this.currentBuildQueue.get(0).state().getBlock().asItem());
            }
            this.waitTicksRemaining = NO_STORAGE_RETRY_TICKS;
            this.state = SettlerState.WAITING;
            return;
        }

        this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        this.storageTarget = nearestStorage;
        this.returningExcess = toDeposit;
        this.walkPurpose = SettlerWalkPurpose.TO_STORAGE;

        BlockPos standingSpot = findStandingSpotNear(nearestStorage);
        this.getNavigation().startMovingTo(
                standingSpot.getX() + 0.5, standingSpot.getY(), standingSpot.getZ() + 0.5, 0.6D
        );
        this.state = SettlerState.WALKING;
    }

    private BlockPos findStandingSpotNear(BlockPos targetPos) {
        BlockPos[] candidates = {
                targetPos.north(), targetPos.south(), targetPos.east(), targetPos.west()
        };

        for (BlockPos candidate : candidates) {
            boolean spotIsOpen = this.getWorld().getBlockState(candidate).isAir();
            boolean groundIsSolid = !this.getWorld().getBlockState(candidate.down()).isAir();

            if (spotIsOpen && groundIsSolid) {
                return candidate;
            }
        }

        return targetPos; // ничего не нашли — попробуем как раньше
    }

    private void startWandering() {
        this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        clearMissingItem();

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
            case TO_STORAGE -> {
                if (this.storageTarget != null && this.getBlockPos().getSquaredDistance(this.storageTarget) > ARRIVAL_DISTANCE_SQ) {
                    this.getNavigation().startMovingTo(
                            this.storageTarget.getX() + 0.5, this.storageTarget.getY(), this.storageTarget.getZ() + 0.5, 0.6D
                    );
                    return;
                }
                this.state = SettlerState.GATHERING;
            }
            case TO_SITE -> {
                BlockPos nextBlockPos = (this.buildOrigin != null && this.currentBuildQueue != null && !this.currentBuildQueue.isEmpty())
                        ? this.buildOrigin.add(this.currentBuildQueue.get(0).relativePos())
                        : null;

                if (nextBlockPos != null && this.getBlockPos().getSquaredDistance(nextBlockPos) > ARRIVAL_DISTANCE_SQ) {
                    this.getNavigation().startMovingTo(
                            nextBlockPos.getX() + 0.5, nextBlockPos.getY(), nextBlockPos.getZ() + 0.5, 0.6D
                    );
                    return;
                }
                this.state = SettlerState.BUILDING;
            }
            case TO_WORK_SITE -> {
                BlockPos target = (this.workQueue != null && !this.workQueue.isEmpty()) ? this.workQueue.get(0) : null;

                if (target != null && this.getBlockPos().getSquaredDistance(target) > WORK_REACH_DISTANCE_SQ) {
                    if (this.getNavigation().isIdle()) {
                        this.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6D);
                    }
                    return;
                }
                this.state = SettlerState.WORKING;
            }
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

        Item primaryNeeded = this.currentBuildQueue.get(0).state().getBlock().asItem();

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

        for (Item item : typesToFetch) {
            int extracted = storage.extractItem(item, CARRY_CAPACITY_PER_TYPE);
            if (extracted > 0) {
                this.carriedItems.merge(item, extracted, Integer::sum);
            }
        }

        if (this.carriedItems.getOrDefault(primaryNeeded, 0) > 0) {
            clearMissingItem();
            this.waitTicksRemaining = 5;
        } else {
            setMissingItem(primaryNeeded);
            this.waitTicksRemaining = NO_MATERIAL_RETRY_TICKS;
        }

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

                this.getLookControl().lookAt(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5, 30.0F, 30.0F);
                this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(neededItem));
                this.swingHand(Hand.MAIN_HAND);
                this.getWorld().playSound(
                        null, worldPos, next.state().getSoundGroup().getPlaceSound(),
                        SoundCategory.BLOCKS, 1.0F, 1.0F
                );

                this.getWorld().setBlockState(worldPos, next.state());
                this.currentBuildQueue.remove(0);

                if (have - 1 <= 0) {
                    this.carriedItems.remove(neededItem);
                } else {
                    this.carriedItems.put(neededItem, have - 1);
                }

                clearMissingItem();
            }
        }

        this.waitTicksRemaining = BUILD_STEP_DELAY_TICKS;
        this.state = SettlerState.WAITING;
    }

    private void handleWorking() {
        ProfessionBehavior behavior = this.profession.getBehavior();

        if (behavior != null) {
            behavior.performWorkAction(this);
        } else {
            this.waitTicksRemaining = WANDER_WAIT_TICKS;
            this.state = SettlerState.WAITING;
        }
    }

    private void handleWaiting() {
        this.waitTicksRemaining--;
        if (this.waitTicksRemaining > 0) {
            return;
        }
        this.state = SettlerState.IDLE;
    }

    // ------------------------------------------------------------------
    // Публичный API для классов профессий (com.nxtlinea.settlerscraft.entity.profession.*)
    // ------------------------------------------------------------------

    public List<BlockPos> getWorkQueue() {
        return this.workQueue;
    }

    public void setWorkQueue(List<BlockPos> workQueue) {
        this.workQueue = workQueue;
    }

    public Map<Item, Integer> getCarriedItems() {
        return this.carriedItems;
    }

    public void addCarriedItem(Item item, int amount) {
        this.carriedItems.merge(item, amount, Integer::sum);
    }

    public void returnCarriedItemsToStorage() {
        goToStorage(true);
    }

    public void startWalkingToWorkSite(BlockPos pos) {
        this.walkPurpose = SettlerWalkPurpose.TO_WORK_SITE;
        this.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.6D);
        this.state = SettlerState.WALKING;
    }

    public void waitFor(int ticks) {
        this.waitTicksRemaining = ticks;
        this.state = SettlerState.WAITING;
    }

    public void lookAtAndSwing(BlockPos pos) {
        this.getLookControl().lookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30.0F, 30.0F);
        this.swingHand(Hand.MAIN_HAND);
    }

    public void equipItem(Item item) {
        this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    // ------------------------------------------------------------------

    public static DefaultAttributeContainer.Builder createSettlerAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D);
    }

    @Nullable
    @Override
    public net.minecraft.entity.EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable net.minecraft.entity.EntityData entityData, @Nullable NbtCompound entityNbt) {
        net.minecraft.entity.EntityData result = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);

        Profession[] values = Profession.values();
        this.profession = values[this.random.nextInt(values.length)];

        if (this.profession.getDisplayName() != null) {
            this.setCustomName(Text.literal(this.profession.getDisplayName()));
            this.setCustomNameVisible(true);
        } else {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
        }

        return result;
    }
}