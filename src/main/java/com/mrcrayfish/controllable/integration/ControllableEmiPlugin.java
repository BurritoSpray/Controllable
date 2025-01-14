package com.mrcrayfish.controllable.integration;

import com.mrcrayfish.controllable.client.*;
import com.mrcrayfish.controllable.client.gui.navigation.BasicNavigationPoint;
import com.mrcrayfish.controllable.client.gui.navigation.NavigationPoint;
import com.mrcrayfish.controllable.client.gui.navigation.WidgetNavigationPoint;
import dev.emi.emi.api.*;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.*;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import dev.emi.emi.widget.RecipeButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// TODO: Add hints for Emi

@EmiEntrypoint
public class ControllableEmiPlugin implements EmiPlugin
{
    // Size of a tile in the INDEX panel
    private static int ENTRY_SIZE = 0;
    private static boolean loaded = false;
    @Override
    public void initialize(EmiInitRegistry registry)
    {
    }

    @Override
    public void register(EmiRegistry registry) {
        System.out.println("Controllable EmiPlugin loaded!");
        getEntrySize();
        loaded = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null)
        {
            registry.addExclusionArea(InventoryScreen.class, (screen, consumer) -> {
                consumer.accept(new Bounds(0, mc.screen.height - 75, 125, mc.screen.height-25));
                consumer.accept(new Bounds(mc.screen.width - 125, mc.screen.height - 20, mc.screen.width, mc.screen.height));
            });
        }
    }

    /**
     * Gets the ENTRY_SIZE using reflection because its private...
     */
    private void getEntrySize()
    {
        try {
            Field entrySize = EmiScreenManager.class.getDeclaredField("ENTRY_SIZE");
            entrySize.setAccessible(true);
            ENTRY_SIZE = entrySize.getInt(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            ENTRY_SIZE = 18; // Default value in the Emi mod
        }
    }

    public static boolean isEmi(){
        return loaded;
    }

    private static List<NavigationPoint> getRecipeScreenNavigationPoints() {
        List<NavigationPoint> points = new ArrayList<>();

        Minecraft mc = Minecraft.getInstance();
        // Making sure we are on the RecipeScreen
        if (!(mc.screen instanceof RecipeScreen))
        {
            return points;
        }

        RecipeScreen screen = (RecipeScreen) mc.screen;
        Field arrowsField = null;
        Field currentPageField = null;
        List<SizedButtonWidget> arrows = new ArrayList<>();
        List<WidgetGroup> currentPage = new ArrayList<>();
        try {
            // Using reflection to get private fields....
            arrowsField = RecipeScreen.class.getDeclaredField("arrows");
            currentPageField = RecipeScreen.class.getDeclaredField("currentPage");
            arrowsField.setAccessible(true);
            currentPageField.setAccessible(true);
            arrows = (List<SizedButtonWidget>) arrowsField.get(mc.screen);
            currentPage = (List<WidgetGroup>) currentPageField.get(mc.screen);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}

        for (var arrow : arrows)
        {
            points.add(new WidgetNavigationPoint(arrow.x + arrow.getWidth()/2, arrow.y + arrow.getHeight()/2, arrow));
        }

        for (var widgetGroup : currentPage)
        {
            for (var widget : widgetGroup.widgets)
            {
                if (widget instanceof SlotWidget || widget instanceof RecipeButtonWidget)
                {
                    var widgetBounds = widget.getBounds();
                    points.add(new WidgetNavigationPoint((widgetGroup.x + widgetBounds.x()) + widgetBounds.width()/2, (widgetGroup.y + widgetBounds.y()) + widgetBounds.height()/2, widget));
                }
            }
        }

        return points;
    }

    // TODO: Add the cost, the treeview mode and the help points
    private static List<NavigationPoint> getBoMScreenNavigationPoints() {
        List<NavigationPoint> points = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof BoMScreen))
            return points;

        BoMScreen screen = (BoMScreen) mc.screen;
        // Reflection needed again...
        Field nodesField = null;
        Field bomOffXField = null;
        Field bomOffYField = null;
        double bomOffX = 0;
        double bomOffY = 0;
        List<?> nodes = new ArrayList<>();
        Class<?> nodesClass = null;
        Field nodeWidth = null;
        Field nodeX = null;
        Field nodeY = null;
        Class<?> materialNodeClass = null;
        Field materialNodeEmiIngredient = null;
        try {
            nodesField = screen.getClass().getDeclaredField("nodes");
            nodesField.setAccessible(true);
            bomOffXField = screen.getClass().getDeclaredField("offX");
            bomOffXField.setAccessible(true);
            bomOffX = bomOffXField.getDouble(screen);
            bomOffYField = screen.getClass().getDeclaredField("offY");
            bomOffYField.setAccessible(true);
            bomOffY = bomOffYField.getDouble(screen);
            nodes = (List<?>) nodesField.get(screen);
            nodesClass = Class.forName("dev.emi.emi.screen.BoMScreen$Node");
            nodeWidth = nodesClass.getDeclaredField("width");
            nodeWidth.setAccessible(true);
            nodeX = nodesClass.getDeclaredField("x");
            nodeX.setAccessible(true);
            nodeY = nodesClass.getDeclaredField("y");
            nodeY.setAccessible(true);
            materialNodeClass = Class.forName("dev.emi.emi.screen.BoMScreen$MaterialNode");
            materialNodeEmiIngredient = materialNodeClass.getDeclaredField("ingredient");
        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException ignore) {
        }
        if (nodesClass != null) {
            // We have to iterate over the costs and nodes

//            int cx = mc.getWindow().getWidth()/2;
            int cx = mc.screen.width/2 + (int)bomOffX;
            int cy = mc.screen.height/2 + (int)bomOffY;
            for (Object node : nodes) {
                if (!nodesClass.isInstance(node))
                    continue;
                try {
                    // X == offset from the center of the screen
                    // Y == ???
                    int x = nodeX.getInt(node);
                    int y = nodeY.getInt(node);
                    points.add(new BasicNavigationPoint(cx + x, cy + y));
                } catch (IllegalAccessException ignore) {
                }
            }
        }

        return points;
    }

    /**
     * Find the Emi stacks on screen and return the list of clickable points
     * @return
     */
    public static List<NavigationPoint> getNavigationPoints()
    {
        List<NavigationPoint> points = new ArrayList<>();

        Minecraft mc = Minecraft.getInstance();
        // Make an array and loop over each to check if the screen should be getting navigation points
        if (mc.level == null || mc.screen instanceof PauseScreen || mc.screen instanceof OptionsScreen || mc.screen instanceof ControlsScreen)
            return points;

        if (mc.screen instanceof BoMScreen)
        {
            points.addAll(getBoMScreenNavigationPoints());
            return points;
        }

        points.addAll(getRecipeScreenNavigationPoints());

        // For each sidebar get the navigation points
        for (var side : SidebarSide.values())
        {
            var sidebar = EmiScreenManager.getPanelFor(side);
            if (sidebar == null || !sidebar.isVisible())
                continue;

            var leftPage = sidebar.pageLeft;
            var pageRight = sidebar.pageRight;
            var cycle = sidebar.cycle;
            points.add(new WidgetNavigationPoint(leftPage.x + leftPage.getWidth()/2, leftPage.y + leftPage.getHeight()/2, leftPage));
            points.add(new WidgetNavigationPoint(pageRight.x + pageRight.getWidth()/2, pageRight.y + pageRight.getHeight()/2, pageRight));
            points.add(new WidgetNavigationPoint(cycle.x + cycle.getWidth()/2, cycle.y + cycle.getHeight()/2, cycle));

            for (var space : sidebar.spaces)
            {
                for (int y = 0; y < space.th; y++)
                {
                    for (int x = 0; x < space.getWidth(y); x++)
                    {
                        int cx = space.getX(x, y) + ENTRY_SIZE/2;
                        int cy = space.getY(x, y) + ENTRY_SIZE/2;

                        if (EmiScreenManager.getHoveredStack(cx, cy, false).getStack() != EmiIngredient.of(Ingredient.of(Blocks.AIR.asItem())))
                        {
                            points.add(new BasicNavigationPoint(cx, cy));
                        }
                    }
                }
            }
        }
        return points;
    }

    /**
     * Uses reflection to set pressed to false for the given binding
     * @param button the button binding to cancel
     */
    private static void cancelButton(ButtonBinding button)
    {
        try{
            Field pressedField = button.getClass().getDeclaredField("pressed");
            pressedField.setAccessible(true);
            pressedField.setBoolean(button, false);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
    }

    private static class RecipeScreenData
    {
        private RecipeScreen screen;
        public List<RecipeTab> tabs;
        public List<WidgetGroup> currentPage;
        private int tabPage, tab, page, tabPageSize;
        private Field tabsField, tabPageSizeField, tabPageField, tabField, pageField, currentPageField;

        public RecipeScreenData(RecipeScreen screen)
        {
            this.screen = screen;
            try {

                tabsField = screen.getClass().getDeclaredField("tabs");
                tabsField.setAccessible(true);
                tabs = (List<RecipeTab>) tabsField.get(screen);
                tabPageSizeField = screen.getClass().getDeclaredField("tabPageSize");
                tabPageSizeField.setAccessible(true);
                tabPageSize = tabPageSizeField.getInt(screen);
                tabPageField = screen.getClass().getDeclaredField("tabPage");
                tabPageField.setAccessible(true);
                tabPage = tabPageField.getInt(screen);
                tabField = screen.getClass().getDeclaredField("tab");
                tabField.setAccessible(true);
                tab = tabField.getInt(screen);
                pageField = screen.getClass().getDeclaredField("page");
                pageField.setAccessible(true);
                page = pageField.getInt(screen);
                currentPageField = screen.getClass().getDeclaredField("currentPage");
                currentPageField.setAccessible(true);
                currentPage = (List<WidgetGroup>) currentPageField.get(screen);

            } catch(NoSuchFieldException | IllegalAccessException ignore) {}
        }

        public int getTabPageSize() {return tabPageSize;}
        public int getTabPage() {return tabPage;}
        public int getPage() {return page;}
        public int getTab() {return this.tab;}

        public void setTab(int tab)
        {
            if (tab < 0)
                tab = tabs.size()-1;
            if (tab >= tabs.size())
            {
                tab = 0;
            }
            try{
                tabField.setInt(screen, tab);
                tabPageField.setInt(screen, tab/tabPageSize);
                this.tabPage = tab/tabPageSize;
                this.tab = tab;
            }catch (IllegalAccessException ignore){}
        }

        public void setPage(int page)
        {
            if (page < 0)
            {
                page = tabs.get(tab).getPageCount();
            } else if (page >= tabs.get(tab).getPageCount())
            {
                page = 0;
            }

            try{
                tabPageField.setInt(screen, page);
                this.page = page;
            } catch (IllegalAccessException ignore){}
        }
    }

    /**
     * Handles controller input when inside an Emi sidebar or an EmiScreen
     * @param controller
     * @param button
     * @param state
     * @param virtual
     */
    public static void handleButtonInput(ControllerInput controllerInput, @NotNull Controller controller, int button, boolean state, boolean virtual)
    {
        Minecraft mc = Minecraft.getInstance();
        if (!state)
            return;
        if (mc.level == null || (
                !(mc.screen instanceof InventoryScreen) &&
                !(mc.screen instanceof RecipeScreen) &&
                !(mc.screen instanceof CraftingScreen) &&
                !(mc.screen instanceof ContainerScreen)))
        {
            return;
        }

        int mouseX = (int)controllerInput.getMouseX();
        int mouseY = (int)controllerInput.getMouseY();

        // TODO: Split the input sections into their own functions
        // Sidebars
        // InventoryScreen
        // RecipeScreen
        // BoMScreen

        // TODO: Add keybindings for the buttons and only display them in the settings if the Emi mod is detected.

        // Get the panel under cursor if any
        var panel = EmiScreenManager.getHoveredPanel(mouseX, mouseY);
        if (panel != null && (panel.getType() == SidebarType.INDEX || panel.getType() == SidebarType.CRAFTABLES || panel.getType() == SidebarType.FAVORITES))
        {
            if (ButtonBindings.NEXT_CREATIVE_TAB.isButtonDown())
            {
                panel.pageLeft.onPress();
            } else if (ButtonBindings.PREVIOUS_CREATIVE_TAB.isButtonDown())
            {
                panel.pageRight.onPress();
            }
        }

        // Get Hovered EmiIngredient if any
        /*
         * For some reason the panels and recipeScreen does not work the same so we have to use different techniques
         * to fetch the hovered stack.
         */
//        if (mc.screen instanceof InventoryScreen)
        var stack = EmiScreenManager.getHoveredStack(mouseX, mouseY, false);
        EmiIngredient ingredient = stack.getStack();
        if (mc.screen instanceof RecipeScreen && ingredient == EmiIngredient.of(Ingredient.of(Blocks.AIR.asItem())))
            ingredient = ((RecipeScreen)mc.screen).getHoveredStack();


        if (ButtonBindings.PICKUP_ITEM.isButtonPressed())
        {
            // Handle key if in InventoryScreen
            // TODO: Replace this with a check for Emi panels
            if (mc.screen instanceof InventoryScreen || mc.screen instanceof RecipeScreen || mc.screen instanceof CraftingScreen)
            {
                if (ingredient != EmiIngredient.of(Ingredient.of(Blocks.AIR.asItem())))
                {
                    // Check if the panel is CRAFTABLES to fill the crafting table with the recipe
                    if (panel != null && panel.getType() == SidebarType.CRAFTABLES && EmiRecipeFiller.isSupported(stack.getRecipeContext()))
                    {
                        // TODO: Check the refreshed panel after craft and update cursor position if changed
                        EmiRecipeFiller.performFill(stack.getRecipeContext(), EmiApi.getHandledScreen(), EmiCraftContext.Type.CRAFTABLE, EmiCraftContext.Destination.INVENTORY,1);
                    } else
                    {
                        EmiApi.displayRecipes(ingredient);
                    }
                    cancelButton(ButtonBindings.PICKUP_ITEM);
                }
            }
        } else if (ButtonBindings.QUICK_MOVE.isButtonPressed())
        {
            if (mc.screen instanceof RecipeScreen || mc.screen instanceof BoMScreen)
            {
                mc.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_BACKSPACE), 0);
                cancelButton(ButtonBindings.QUICK_MOVE);
                cancelButton(ButtonBindings.USE_ITEM);
            } else if ((mc.screen instanceof CraftingScreen || mc.screen instanceof InventoryScreen) && ingredient != EmiIngredient.of(Ingredient.of(Blocks.AIR.asItem())))
            {
                EmiRecipeFiller.performFill(stack.getRecipeContext(), EmiApi.getHandledScreen(), EmiCraftContext.Type.CRAFTABLE, EmiCraftContext.Destination.NONE, 64);
            }
        } else if (ButtonBindings.NEXT_CREATIVE_TAB.isButtonPressed())
        {
            if (panel == null && mc.screen instanceof RecipeScreen)
            {
                if (mc.screen instanceof RecipeScreen)
                {
                    RecipeScreenData recipeScreen = new RecipeScreenData((RecipeScreen) mc.screen);
                    ((RecipeScreen)mc.screen).setPage(recipeScreen.tabPage, recipeScreen.tab, recipeScreen.page-1);
                }
            }
        } else if (ButtonBindings.PREVIOUS_CREATIVE_TAB.isButtonPressed())
        {
            if (panel == null && mc.screen instanceof RecipeScreen)
            {
                if (mc.screen instanceof RecipeScreen)
                {
                    RecipeScreenData recipeScreen = new RecipeScreenData((RecipeScreen) mc.screen);
                    ((RecipeScreen)mc.screen).setPage(recipeScreen.tabPage, recipeScreen.tab, recipeScreen.page+1);
                }
            }
        } else if (ButtonBindings.NEXT_RECIPE_TAB.isButtonPressed())
        {
            if (mc.screen instanceof RecipeScreen)
            {
                RecipeScreenData recipeScreen = new RecipeScreenData((RecipeScreen) mc.screen);
                recipeScreen.setTab(recipeScreen.tab-1);
                ((RecipeScreen)mc.screen).setPage(recipeScreen.tabPage, recipeScreen.tab, recipeScreen.page);
            }
        } else if (ButtonBindings.PREVIOUS_RECIPE_TAB.isButtonPressed())
        {
            if (mc.screen instanceof RecipeScreen)
            {
                RecipeScreenData recipeScreen = new RecipeScreenData((RecipeScreen) mc.screen);
                recipeScreen.setTab(recipeScreen.tab+1);
                ((RecipeScreen)mc.screen).setPage(recipeScreen.tabPage, recipeScreen.tab, recipeScreen.page);
            }
        } else if (controller.isButtonPressed(Buttons.RIGHT_THUMB_STICK))
        {
            if (mc.screen instanceof AbstractContainerScreen<?>) {
                var item = ((AbstractContainerScreen<?>) mc.screen).getSlotUnderMouse();
                if (panel == null && (mc.screen instanceof InventoryScreen || mc.screen instanceof CraftingScreen)) {

                    if (item != null) {
                        EmiApi.displayRecipes(EmiIngredient.of(Ingredient.of(item.getItem())));
                        return;
                    } else if (ingredient != EmiIngredient.of(Ingredient.of(Blocks.AIR.asItem()))) {
                        EmiApi.displayRecipes(ingredient);
                        return;
                    }
                }
            }
            if (ingredient != EmiIngredient.of(Ingredient.of(Blocks.AIR.asItem())))
            {
                if (mc.screen instanceof RecipeScreen) {

                    if (panel == null)
                    {
                        EmiFavorites.addFavorite(ingredient);
                        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
                        EmiFavorites.save();
                        return;
                    }
                }
                if (panel != null) {

                    if (panel.getType() == SidebarType.FAVORITES) {
                        EmiFavorites.removeFavorite(ingredient);
                        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
                        EmiFavorites.save();
                    } else if (panel.getType() == SidebarType.INDEX || panel.getType() == SidebarType.CRAFTABLES) {
                        boolean found = false;
                        for (var fav : EmiFavorites.favorites) {
                            if (fav.getStack().getEmiStacks().get(0).getId() == ingredient.getEmiStacks().get(0).getId()) {
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            EmiFavorites.removeFavorite(ingredient);
                        } else {
                            EmiFavorites.addFavorite(ingredient);
                        }
                        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
                        EmiFavorites.save();
                    }
                }
            }
        } else if (controller.isButtonPressed(Buttons.LEFT_THUMB_STICK))
        {
//            if (mc.screen instanceof InventoryScreen && ingredient != EmiIngredient.of(Ingredient.of(Blocks.AIR.asItem())) && panel == null)
//            {
//                EmiApi.displayRecipes(ingredient);
//            }
        }
    }

    public static boolean isEmiAction(ControllerInput controllerInput)
    {
        Minecraft mc = Minecraft.getInstance();
        if (
                mc.screen instanceof BoMScreen ||
                mc.screen instanceof ConfigScreen ||
                mc.screen instanceof RecipeScreen ||
                mc.screen instanceof InventoryScreen ||
                mc.screen instanceof CraftingScreen
        ) return true;

        if (EmiScreenManager.getHoveredPanel((int)controllerInput.getMouseX(), (int)controllerInput.getMouseY()) != null)
            return true;

        return false;
    }
}

class EmiControllerInput extends ControllerInput
{
    @Override
    public void handleButtonInput(Controller controller, int button, boolean state, boolean virtual)
    {
    }
}
