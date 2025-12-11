package com.kylernarvaez.tagmod.TagCommand;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public class StartGame {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("tagstart")
                            .executes(context -> {
                                context.getSource().sendFeedback(
                                        () -> Text.literal("Starting Game. . ."),
                                        false
                                );
                                return 1;
                            })
            );
        });
    }
}
