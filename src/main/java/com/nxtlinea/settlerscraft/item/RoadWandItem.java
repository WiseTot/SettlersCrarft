package com.nxtlinea.settlerscraft.item;

import com.nxtlinea.settlerscraft.building.RoadManager;
import com.nxtlinea.settlerscraft.building.RoadPathfinder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class RoadWandItem extends Item {

    public RoadWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }

        ItemStack stack = context.getStack();
        BlockPos clickedPos = context.getBlockPos().up();
        NbtCompound nbt = stack.getOrCreateNbt();

        if (nbt.contains("StartX")) {
            BlockPos start = new BlockPos(nbt.getInt("StartX"), nbt.getInt("StartY"), nbt.getInt("StartZ"));
            nbt.remove("StartX");
            nbt.remove("StartY");
            nbt.remove("StartZ");

            List<BlockPos> path = RoadPathfinder.findPath(context.getWorld(), start, clickedPos);

            if (context.getPlayer() != null) {
                if (path.isEmpty()) {
                    context.getPlayer().sendMessage(Text.literal("Не удалось проложить путь для дороги (слишком сложный рельеф)"), true);
                } else {
                    List<BlockPos> widePath = RoadPathfinder.widen(context.getWorld(), path);
                    RoadManager.addRoad(widePath);
                    context.getPlayer().sendMessage(Text.literal("Дорога добавлена в очередь: " + widePath.size() + " блоков"), true);
                }
            }
        } else {
            nbt.putInt("StartX", clickedPos.getX());
            nbt.putInt("StartY", clickedPos.getY());
            nbt.putInt("StartZ", clickedPos.getZ());

            if (context.getPlayer() != null) {
                context.getPlayer().sendMessage(Text.literal("Начало дороги отмечено: " + clickedPos.toShortString()), true);
            }
        }

        return ActionResult.CONSUME;
    }
}