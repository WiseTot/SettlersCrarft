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
        // Формула как у ванильного GenericContainerScreen: 114 + rows*18, у нас всегда 3 ряда
        this.backgroundHeight = 114 + 3 * 18;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        // Важно передавать реальные размеры текстуры (176x168) — иначе движок считает
        // текстуру 256x256 по умолчанию и картинка растягивается/съезжает
        context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, this.backgroundWidth, this.backgroundHeight);
    }
}