package com.kylernarvaez.tagmod.TagCommand;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.BlockPos;
import java.util.EnumSet;
import net.minecraft.server.network.ServerPlayerEntity.*;

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
                                teleportPlayer(context.getSource().getPlayer());
                                return 1;
                            })
            );
        });
    }

    private static void teleportPlayer(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        double x = (Math.random() * 2000) - 1000;
        double z = (Math.random() * 2000) - 1000;

        BlockPos pos = new BlockPos((int)x, 0, (int)z);
        int y = world.getTopPosition(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                pos
        ).getY();

        player.teleport(
                world,
                x,
                y+2,
                z,
                EnumSet.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z, PositionFlag.Y_ROT, PositionFlag.X_ROT),
                player.getYaw(),
                player.getPitch(),
                false
        );
        player.sendMessage(Text.literal("Teleported to: " + (int)x + " " + (y+1) + " " + (int)z), false);
    }

}
