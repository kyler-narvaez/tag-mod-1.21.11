package com.kylernarvaez.tagmod.TagCommand;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.BlockPos;
import java.util.EnumSet;

import net.minecraft.text.Text;

//Command Args
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.chunk.ChunkStatus;

import static net.minecraft.server.command.CommandManager.argument;

import java.util.HashMap;
import java.util.Map;


public class StartGame {

    private static BlockPos arenaCenter;
    private static int arenaRadius;

    public static void register() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("tagstart")
                            .then(argument("radius", IntegerArgumentType.integer(5))
                                    .then(argument("minutes", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                int radius = IntegerArgumentType.getInteger(context, "radius");
                                                int minutes = IntegerArgumentType.getInteger(context, "minutes");

                                                ServerPlayerEntity itPlayer = context.getSource().getPlayer();
                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("Starting Game with radius " + radius + " and time to seek " + minutes + " (minutes)"),
                                                        false
                                                );

                                                //teleportPlayer(player, radius, minutes);
                                                startTagGame(itPlayer, radius, minutes);
                                                return 1;
                                            })
                                    )
                            )
                            .executes(context -> {
                                ServerPlayerEntity itPlayer = context.getSource().getPlayer();
                                //teleportPlayer(player, 1000, 20); // default radius & attempts
                                startTagGame(itPlayer, 20, 5);
                                context.getSource().sendFeedback(
                                        () -> Text.literal("Starting Game with default radius 20 and time 5 minutes"),
                                        false
                                );
                                return 1;
                            })


            );
        });
    }

    private static void startTagGame(ServerPlayerEntity itPlayer, int radius, int minutes) {
        ServerWorld world = itPlayer.getEntityWorld();

        // Teleport the "it" player
        BlockPos itPos = teleportItPlayer(itPlayer, radius);
        arenaCenter = itPos;
        arenaRadius = radius;

        //Set world border
        world.getWorldBorder().setCenter(arenaCenter.getX() + 0.5, arenaCenter.getZ() + 0.5);

        world.getWorldBorder().setSize(arenaRadius * 2);

        // Teleport other players near the "it" player
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (other != itPlayer) {
                teleportNear(other, itPos, radius - 1);
            }
        }
    }

    private static void teleportNear(ServerPlayerEntity player, BlockPos center, int radius) {
        ServerWorld world = player.getEntityWorld();

        double angle = Math.random() * 2 * Math.PI;
        double dist = Math.random() * radius;
        int x = (int)(center.getX() + Math.cos(angle) * dist);
        int z = (int)(center.getZ() + Math.sin(angle) * dist);

        int y = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos(x, 0, z)).getY();

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
        player.sendMessage(Text.literal("You are in the tag arena!"), false);
    }


    private static BlockPos teleportItPlayer(ServerPlayerEntity player, int radius) {
        ServerWorld world = player.getEntityWorld();

        int attempts = 0;
        boolean safe = false;

        int x = 0, y = 0, z = 0;

        while (!safe && attempts < 20) {
            attempts++;

            if (attempts != 1) {
                player.sendMessage(Text.literal("Unsafe: " + x + " " + (y+1) + " " + z + " (Trying Again)"), false);
            }

            x = (int)((Math.random() * 2 - 1) * radius);
            z = (int)((Math.random() * 2 - 1) * radius);

            //world.getChunk(x >> 4, z >> 4);
            world.getChunkManager().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
            BlockPos pos = new BlockPos(x, 0, z);
            y = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    pos
            ).getY();

            BlockPos checkPos = new BlockPos(x, y, z);
            BlockPos abovePos = checkPos.up();

            var state = world.getBlockState(checkPos);
            safe = true; //!world.isAir(checkPos) && world.isAir(abovePos) && state.getFluidState().isEmpty();
        }


//        player.teleport(
//                world,
//                x+0.5,
//                y+1,
//                z+0.5,
//                EnumSet.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z, PositionFlag.Y_ROT, PositionFlag.X_ROT),
//                player.getYaw(),
//                player.getPitch(),
//                false
//        );

        player.teleport(x+0.5, y+1, z+0.5, false);
        player.sendMessage(Text.literal("Teleported to: " + x + " " + (y+1) + " " + z), false);
        return new BlockPos(x, y, z);
    }

}
