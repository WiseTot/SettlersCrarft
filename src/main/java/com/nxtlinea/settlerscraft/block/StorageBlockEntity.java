package com.nxtlinea.settlerscraft.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

public class StorageBlockEntity extends BlockEntity implements Inventory {

    private static final int SIZE = 27;

    private DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    public StorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_BLOCK_ENTITY, pos, state);
    }

    // --- Реализация Inventory ---

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > stack.getMaxCount()) {
            stack.setCount(stack.getMaxCount());
        }
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (this.world == null || this.world.getBlockEntity(this.pos) != this) {
            return false;
        }
        return player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() {
        items.clear();
    }

    // --- Персистентность ---

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
        Inventories.readNbt(nbt, items);
    }

    // --- Вспомогательные методы для жителей и игрока ---

    /**
     * Достаёт до maxAmount единиц указанного предмета из склада.
     * Возвращает реально извлечённое количество (может быть меньше запрошенного или 0).
     */
    public int extractItem(Item item, int maxAmount) {
        int remaining = maxAmount;

        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && stack.isOf(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.decrement(take);
                remaining -= take;
                if (stack.isEmpty()) {
                    items.set(i, ItemStack.EMPTY);
                }
            }
        }

        markDirty();
        return maxAmount - remaining;
    }

    /**
     * Пытается вложить стак в склад (сначала в существующие такие же стаки, потом в пустые слоты).
     * Возвращает остаток, который не поместился (может быть ItemStack.EMPTY, если влезло всё).
     */
    public ItemStack insertStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
            ItemStack existing = items.get(i);
            if (!existing.isEmpty() && existing.isOf(stack.getItem()) && existing.getCount() < existing.getMaxCount()) {
                int space = existing.getMaxCount() - existing.getCount();
                int move = Math.min(space, stack.getCount());
                existing.increment(move);
                stack.decrement(move);
            }
        }

        for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack.copy());
                stack.setCount(0);
            }
        }

        markDirty();
        return stack;
    }

    public DefaultedList<ItemStack> getItemsForScatter() {
        return items;
    }
}