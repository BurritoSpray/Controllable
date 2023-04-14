package com.mrcrayfish.controllable.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mrcrayfish.controllable.client.gui.screens.ControllerLayoutScreen;
import com.mrcrayfish.controllable.client.gui.screens.SettingsScreen;
import com.mrcrayfish.framework.api.event.TickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayDeque;
import java.util.Queue;

import static io.github.libsdl4j.api.gamecontroller.SDL_GameControllerButton.*;

/**
 * Author: MrCrayfish
 */
public class InputProcessor
{
    private static InputProcessor instance;

    public static InputProcessor instance()
    {
        if(instance == null)
        {
            instance = new InputProcessor();
        }
        return instance;
    }

    private final Queue<ButtonStates> inputQueue = new ArrayDeque<>();
    private final ControllerInput input;
    private final ControllerManager manager;

    private InputProcessor()
    {
        this.input = new ControllerInput();
        this.manager = ControllerManager.instance();
        TickEvents.START_RENDER.register((partialTick) -> this.pollControllerInput(false));
        TickEvents.END_RENDER.register((partialTick) -> this.pollControllerInput(false));
        TickEvents.START_CLIENT.register(() -> this.pollControllerInput(true));
        TickEvents.END_CLIENT.register(() -> this.pollControllerInput(false));
    }

    private void pollControllerInput(boolean process)
    {
        this.gatherAndQueueControllerInput();
        if(process)
        {
            this.processButtonStates();
        }
    }

    private void gatherAndQueueControllerInput()
    {
        if(this.manager == null)
            return;

        this.manager.tick();

        Controller currentController = this.manager.getActiveController();
        if(currentController == null)
            return;

        if(!currentController.updateGamepadState())
            return;

        // Capture all inputs and queue
        ButtonStates states = new ButtonStates();
        states.setState(Buttons.A, this.getButtonState(SDL_CONTROLLER_BUTTON_A));
        states.setState(Buttons.B, this.getButtonState(SDL_CONTROLLER_BUTTON_B));
        states.setState(Buttons.X, this.getButtonState(SDL_CONTROLLER_BUTTON_X));
        states.setState(Buttons.Y, this.getButtonState(SDL_CONTROLLER_BUTTON_Y));
        states.setState(Buttons.SELECT, this.getButtonState(SDL_CONTROLLER_BUTTON_BACK));
        states.setState(Buttons.HOME, this.getButtonState(SDL_CONTROLLER_BUTTON_GUIDE));
        states.setState(Buttons.START, this.getButtonState(SDL_CONTROLLER_BUTTON_START));
        states.setState(Buttons.LEFT_THUMB_STICK, this.getButtonState(SDL_CONTROLLER_BUTTON_LEFTSTICK));
        states.setState(Buttons.RIGHT_THUMB_STICK, this.getButtonState(SDL_CONTROLLER_BUTTON_RIGHTSTICK));
        states.setState(Buttons.LEFT_BUMPER, this.getButtonState(SDL_CONTROLLER_BUTTON_LEFTSHOULDER));
        states.setState(Buttons.RIGHT_BUMPER, this.getButtonState(SDL_CONTROLLER_BUTTON_RIGHTSHOULDER));
        states.setState(Buttons.LEFT_TRIGGER, currentController.getLTriggerValue() >= 0.5F);
        states.setState(Buttons.RIGHT_TRIGGER, currentController.getRTriggerValue() >= 0.5F);
        states.setState(Buttons.DPAD_UP, this.getButtonState(SDL_CONTROLLER_BUTTON_DPAD_UP));
        states.setState(Buttons.DPAD_DOWN, this.getButtonState(SDL_CONTROLLER_BUTTON_DPAD_DOWN));
        states.setState(Buttons.DPAD_LEFT, this.getButtonState(SDL_CONTROLLER_BUTTON_DPAD_LEFT));
        states.setState(Buttons.DPAD_RIGHT, this.getButtonState(SDL_CONTROLLER_BUTTON_DPAD_RIGHT));
        states.setState(Buttons.MISC, this.getButtonState(SDL_CONTROLLER_BUTTON_MISC1));
        states.setState(Buttons.PADDLE_ONE, this.getButtonState(SDL_CONTROLLER_BUTTON_PADDLE1));
        states.setState(Buttons.PADDLE_TWO, this.getButtonState(SDL_CONTROLLER_BUTTON_PADDLE2));
        states.setState(Buttons.PADDLE_THREE, this.getButtonState(SDL_CONTROLLER_BUTTON_PADDLE3));
        states.setState(Buttons.PADDLE_FOUR, this.getButtonState(SDL_CONTROLLER_BUTTON_PADDLE4));
        states.setState(Buttons.TOUCHPAD, this.getButtonState(SDL_CONTROLLER_BUTTON_TOUCHPAD));
        this.inputQueue.offer(states);
    }

    private boolean getButtonState(int buttonCode)
    {
        return this.manager.getActiveController() != null && this.manager.getActiveController().getGamepadState()[buttonCode] == 1;
    }

    private void processButtonStates()
    {
        ButtonBinding.tick();
        while(!this.inputQueue.isEmpty())
        {
            ButtonStates states = this.inputQueue.poll();
            for(int i = 0; i < Buttons.BUTTONS.length; i++)
            {
                this.processButton(Buttons.BUTTONS[i], states);
            }
        }
    }

    private void processButton(int index, ButtonStates newStates)
    {
        boolean state = newStates.getState(index);

        Screen screen = Minecraft.getInstance().screen;
        if(screen instanceof ControllerLayoutScreen)
        {
            ((ControllerLayoutScreen) screen).processButton(index, newStates);
            return;
        }

        Controller controller = this.manager.getActiveController();
        if(controller == null)
            return;

        if(controller.getMapping() != null)
        {
            index = controller.getMapping().remap(index);
        }

        //No binding so don't perform any action
        if(index == -1)
            return;

        ButtonStates states = controller.getButtonsStates();

        if(state)
        {
            if(!states.getState(index))
            {
                states.setState(index, true);
                if(screen instanceof SettingsScreen settings && settings.isWaitingForButtonInput() && settings.processButton(index))
                {
                    return;
                }
                this.input.handleButtonInput(controller, index, true, false);
            }
        }
        else if(states.getState(index))
        {
            states.setState(index, false);
            this.input.handleButtonInput(controller, index, false, false);
        }
    }

    /**
     * Allows a controller to be polled while the main thread is waiting due to FPS limit. This
     * overrides the wait behaviour of Minecraft and is off by default. Do not call this method, it
     * is internal only.
     */
    public static void queueInputsWait()
    {
        Minecraft mc = Minecraft.getInstance();
        int fps = mc.level != null || mc.screen == null && mc.getOverlay() == null ? mc.getWindow().getFramerateLimit() : 60;
        int captureCount = 4; // The amount of times to capture controller input while waiting
        for(int i = 0; i < captureCount; i++)
        {
            RenderSystem.limitDisplayFPS(fps * captureCount);
            InputProcessor.instance().gatherAndQueueControllerInput();
        }
    }

    public ControllerInput getInput()
    {
        return this.input;
    }
}
