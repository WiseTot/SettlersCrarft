package com.nxtlinea.settlerscraft.client;

import com.nxtlinea.settlerscraft.Settlerscraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class StorageScreen extends HandledScreen<GenericContainerScreenHandler> {

    private static final Identifier TEXTURE = new Identifier(Settlerscraft.MOD_ID, "textures/gui/container/storage.png");

    public StorageScreen(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }
}