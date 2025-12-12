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

        int maxRadius = 1000;
        int attempts = 0;
        boolean safe = false;

        int x = 0, y = 0, z = 0;

        while (!safe && attempts < 20) {
            attempts++;

            x = (int)((Math.random() - 0.5) * 2 * maxRadius);
            z = (int)((Math.random() - 0.5) * 2 * maxRadius);

            world.getChunk(x >> 4, z >> 4);

            BlockPos pos = new BlockPos((int)x, 0, (int)z);
            y = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    pos
            ).getY();

            BlockPos checkPos = new BlockPos(x, y, z);

            var state = world.getBlockState(checkPos);
            safe = state.isSolidBlock(world, checkPos) && state.getFluidState().isEmpty();
        }


        player.teleport(
                world,
                x+0.5,
                y+1,
                z+0.5,
                EnumSet.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z, PositionFlag.Y_ROT, PositionFlag.X_ROT),
                player.getYaw(),
                player.getPitch(),
                false
        );
        player.sendMessage(Text.literal("Teleported to: " + (int)x + " " + (y+1) + " " + (int)z), false);
    }

}
