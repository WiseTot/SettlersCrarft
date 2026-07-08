package com.nxtlinea.settlerscraft.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Схема здания, загруженная из .nbt файла (сохранённого структурным блоком).
 * Хранит список блоков с координатами, относительными к точке застройки.
 */
public class BlueprintData {

    // Числовые ID типов NBT-тегов (стандарт формата, не меняется между версиями)
    private static final int NBT_TYPE_INT = 3;
    private static final int NBT_TYPE_COMPOUND = 10;

    // Служебные блоки Structure Block'а, которые никогда не должны попадать в реальную постройку,
    // даже если случайно оказались захвачены в зону сохранения
    private static final Set<Block> IGNORED_BLOCKS = Set.of(
            Blocks.STRUCTURE_BLOCK,
            Blocks.STRUCTURE_VOID,
            Blocks.JIGSAW
    );

    public record BlockPlacement(BlockPos relativePos, BlockState state) {}

    private final List<BlockPlacement> placements;

    private BlueprintData(List<BlockPlacement> placements) {
        this.placements = placements;
    }

    public List<BlockPlacement> getPlacements() {
        return placements;
    }

    public int getBlockCount() {
        return placements.size();
    }

    /**
     * Загружает схему из .nbt файла в ресурсах мода.
     * resourcePath пример: "data/settlerscraft/structures/starter_house.nbt"
     */
    public static BlueprintData loadFromResource(String resourcePath) {
        try (InputStream stream = BlueprintData.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Файл схемы не найден в ресурсах: " + resourcePath);
            }

            NbtCompound root = NbtIo.readCompressed(stream);
            return parse(root);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить blueprint: " + resourcePath, e);
        }
    }

    private static BlueprintData parse(NbtCompound root) {
        NbtList paletteNbt = root.getList("palette", NBT_TYPE_COMPOUND);
        List<BlockState> palette = new ArrayList<>();

        for (int i = 0; i < paletteNbt.size(); i++) {
            NbtCompound entry = paletteNbt.getCompound(i);
            String blockName = entry.getString("Name");
            Block block = Registries.BLOCK.get(new Identifier(blockName));
            BlockState state = block.getDefaultState();

            if (entry.contains("Properties")) {
                NbtCompound properties = entry.getCompound("Properties");
                for (String key : properties.getKeys()) {
                    state = applyProperty(state, key, properties.getString(key));
                }
            }

            palette.add(state);
        }

        NbtList blocksNbt = root.getList("blocks", NBT_TYPE_COMPOUND);
        List<BlockPlacement> placements = new ArrayList<>();
        int skippedServiceBlocks = 0;

        for (int i = 0; i < blocksNbt.size(); i++) {
            NbtCompound entry = blocksNbt.getCompound(i);
            NbtList posNbt = entry.getList("pos", NBT_TYPE_INT);
            int x = posNbt.getInt(0);
            int y = posNbt.getInt(1);
            int z = posNbt.getInt(2);
            int stateIndex = entry.getInt("state");

            BlockState state = palette.get(stateIndex);

            if (state.isAir()) {
                continue;
            }

            if (IGNORED_BLOCKS.contains(state.getBlock())) {
                skippedServiceBlocks++;
                continue;
            }

            placements.add(new BlockPlacement(new BlockPos(x, y, z), state));
        }

        if (skippedServiceBlocks > 0) {
            com.nxtlinea.settlerscraft.Settlerscraft.LOGGER.info(
                    "Пропущено служебных блоков (structure_block/structure_void) при загрузке blueprint: " + skippedServiceBlocks
            );
        }

        // Строим снизу вверх — выглядит естественнее
        placements.sort(Comparator.comparingInt(p -> p.relativePos().getY()));

        return new BlueprintData(placements);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperty(BlockState state, String key, String valueStr) {
        Property property = state.getBlock().getStateManager().getProperty(key);
        if (property == null) {
            return state;
        }

        var parsed = property.parse(valueStr);
        if (parsed.isPresent()) {
            return state.with(property, (Comparable) parsed.get());
        }
        return state;
    }
}