package com.nxtlinea.settlerscraft.client;

import com.nxtlinea.settlerscraft.Settlerscraft;
import com.nxtlinea.settlerscraft.entity.SettlerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class MissingMaterialIconRenderer {

    private static final Identifier FRAME_TEXTURE =
            new Identifier(Settlerscraft.MOD_ID, "textures/entity/missing_material_frame.png");

    public static void render(SettlerEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        ItemStack missing = entity.getMissingItem();
        if (missing.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        matrices.push();
        matrices.translate(0.0, entity.getHeight() + 0.3, 0.0);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.22F, -0.22F, 0.22F);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer frameBuffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(FRAME_TEXTURE));
        drawFrameBothSides(frameBuffer, matrix, light);

        matrices.translate(0.0, 0.0, -0.05);
        client.getItemRenderer().renderItem(
                missing, ModelTransformationMode.GUI, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, entity.getWorld(), 0
        );

        matrices.pop();
    }

    private static void drawFrameBothSides(VertexConsumer buffer, Matrix4f matrix, int light) {
        float half = 0.70F;

        emit(buffer, matrix, -half, half, 0.0F, 0.0F, 1.0F, light);
        emit(buffer, matrix, half, half, 0.0F, 1.0F, 1.0F, light);
        emit(buffer, matrix, half, -half, 0.0F, 1.0F, 0.0F, light);
        emit(buffer, matrix, -half, -half, 0.0F, 0.0F, 0.0F, light);

        emit(buffer, matrix, -half, -half, 0.0F, 0.0F, 0.0F, light);
        emit(buffer, matrix, half, -half, 0.0F, 1.0F, 0.0F, light);
        emit(buffer, matrix, half, half, 0.0F, 1.0F, 1.0F, light);
        emit(buffer, matrix, -half, half, 0.0F, 0.0F, 1.0F, light);
    }

    private static void emit(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(matrix, x, y, z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0F, 0.0F, 1.0F).next();
    }
}