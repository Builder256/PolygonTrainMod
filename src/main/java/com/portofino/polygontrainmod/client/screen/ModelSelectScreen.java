package com.portofino.polygontrainmod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ModelSelectScreen extends Screen {
    public record ModelInfo(String id, String displayName) {}

    private static final int LIST_TOP = 36;
    private static final int LIST_BOTTOM_MARGIN = 36;
    private static final int ITEM_HEIGHT = 22;

    private final List<ModelInfo> models;
    private final Consumer<String> onSelected;
    private ModelList modelList;

    public ModelSelectScreen(Component title, List<ModelInfo> models, Consumer<String> onSelected) {
        super(title);
        this.models = models;
        this.onSelected = onSelected;
    }

    @Override
    protected void init() {
        modelList = new ModelList(minecraft, width, height - LIST_BOTTOM_MARGIN, LIST_TOP, ITEM_HEIGHT);
        addRenderableWidget(modelList);
        
        // OK button
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> {
            ModelList.ModelEntry selected = modelList.getSelected();
            if (selected != null) {
                onSelected.accept(selected.id);
            }
            onClose();
        }).bounds(width / 2 - 155, height - LIST_BOTTOM_MARGIN + 8, 150, 20).build());
        
        // Cancel button
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
            .bounds(width / 2 + 5, height - LIST_BOTTOM_MARGIN + 8, 150, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, getTitle(), width / 2, 10, 0xFFFFFF);
        if (models.isEmpty()) {
            graphics.drawCenteredString(font,
                Component.translatable("screen.polygontrainmod.no_models"),
                width / 2, height / 2, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class ModelList extends ObjectSelectionList<ModelList.ModelEntry> {
        ModelList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
            setRenderHeader(false, 0);
            for (ModelInfo info : models) {
                addEntry(new ModelEntry(info.id(), info.displayName()));
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return width - 8;
        }

        @Override
        public int getRowWidth() {
            return width - 20;
        }

        class ModelEntry extends ObjectSelectionList.Entry<ModelEntry> {
            public final String id;
            private final Component label;

            ModelEntry(String id, String displayName) {
                this.id = id;
                String text = (displayName != null && !displayName.isBlank()) ? displayName : id;
                this.label = Component.literal(text);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width,
                               int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int color = hovered ? 0xFFFF55 : 0xFFFFFF;
                graphics.drawString(ModelSelectScreen.this.font, label, left + 6, top + (height - 8) / 2, color);
                if (ModelList.this.getSelected() == this) {
                    graphics.fill(left, top, left + width, top + height, 0x44FFFFFF);
                }
            }
            
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    ModelList.this.setSelected(this);
                    onSelected.accept(id);
                    onClose();
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return label;
            }
        }
    }
}
