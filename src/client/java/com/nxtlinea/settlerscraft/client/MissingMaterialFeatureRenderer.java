package com.nxtlinea.settlerscraft.client;

import com.nxtlinea.settlerscraft.entity.SettlerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class MissingMaterialFeatureRenderer extends FeatureRenderer<SettlerEntity, BipedEntityModel<SettlerEntity>> {

    private static final ItemStack BORDER_STACK = new ItemStack(Items.RED_CONCRETE);

    public MissingMaterialFeatureRenderer(FeatureRendererContext<SettlerEntity, BipedEntityModel<SettlerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       SettlerEntity entity, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {

        ItemStack missing = entity.getMissingItem();
        if (missing.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        matrices.push();
        matrices.translate(0.0, entity.getHeight() + 0.5, 0.0);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(0.4F, 0.4F, 0.4F);

        matrices.push();
        matrices.translate(0.0, 0.0, -0.01F);
        matrices.scale(1.35F, 1.35F, 1.35F);
        client.getItemRenderer().renderItem(
                BORDER_STACK, ModelTransformationMode.GUI, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, entity.getWorld(), 0
        );
        matrices.pop();

        client.getItemRenderer().renderItem(
                missing, ModelTransformationMode.GUI, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, entity.getWorld(), 0
        );

        matrices.pop();
    }
}