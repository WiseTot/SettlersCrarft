package com.nxtlinea.settlerscraft.client;

import com.nxtlinea.settlerscraft.client.StorageScreen;
import com.nxtlinea.settlerscraft.entity.ModEntities;
import com.nxtlinea.settlerscraft.entity.SettlerEntity;
import com.nxtlinea.settlerscraft.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class SettlerscraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.SETTLER, SettlerEntityRenderer::new);
        HandledScreens.register(ModScreenHandlers.STORAGE_SCREEN_HANDLER, StorageScreen::new);
    }

    public static class SettlerEntityRenderer extends MobEntityRenderer<SettlerEntity, BipedEntityModel<SettlerEntity>> {

        private static final Identifier STEVE_TEXTURE = new Identifier("minecraft", "textures/entity/player/wide/steve.png");

        public SettlerEntityRenderer(EntityRendererFactory.Context context) {
            super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
        }

        @Override
        public Identifier getTexture(SettlerEntity entity) {
            return STEVE_TEXTURE;
        }
    }
}