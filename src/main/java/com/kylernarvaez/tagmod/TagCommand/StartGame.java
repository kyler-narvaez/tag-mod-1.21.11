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
import static net.minecraft.server.command.CommandManager.argument;

import java.util.HashMap;
import java.util.Map;


public class StartGame {

    private static BlockPos arenaCenter;
    private static int arenaRadius;

    public static void register() {
        

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (arenaCenter == null) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!isInsideBoundary(p, arenaCenter, arenaRadius)) {
                    // Push back inside (teleport to nearest point inside radius)
                    double dx = p.getX() - arenaCenter.getX();
                    double dz = p.getZ() - arenaCenter.getZ();
                    double distance = Math.sqrt(dx*dx + dz*dz);

                    if (distance > arenaRadius) {
                        double pushDistance = distance - arenaRadius;
                        double ratio = pushDistance / distance;
                        double newX = p.getX() - dx * ratio;
                        double newZ = p.getZ() - dz * ratio;

                        ServerWorld world = (ServerWorld) p.getEntityWorld();
                        int safeY = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos(newX, 0, newZ)).getY();
                        p.teleport(
                                world,
                                (int)newX+0.5,
                                safeY+1,
                                (int)newZ+0.5,
                                EnumSet.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z, PositionFlag.Y_ROT, PositionFlag.X_ROT),
                                p.getYaw(),
                                p.getPitch(),
                                false
                        );
                    }
                }
            }
        });

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

    private static boolean isInsideBoundary(ServerPlayerEntity player, BlockPos center, int radius) {
        double dx = player.getX() - center.getX();
        double dz = player.getZ() - center.getZ();
        return (dx*dx + dz*dz) <= (radius * radius);
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

            x = (int)((Math.random() - 0.5) * 2 * radius);
            z = (int)((Math.random() - 0.5) * 2 * radius);

            world.getChunk(x >> 4, z >> 4);

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
        player.sendMessage(Text.literal("Teleported to: " + x + " " + (y+1) + " " + z), false);
        return new BlockPos(x, y, z);
    }

}
