package com.nxtlinea.settlerscraft.item;

import com.nxtlinea.settlerscraft.building.ConstructionManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public class SurveyorWandItem extends Item {

    public SurveyorWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }

        BlockPos targetPos = context.getBlockPos().up();
        ConstructionManager.addSite(targetPos);

        if (context.getPlayer() != null) {
            context.getPlayer().sendMessage(
                    Text.literal("Точка застройки отмечена: " + targetPos.toShortString()),
                    true
            );
        }

        return ActionResult.CONSUME;
    }
}