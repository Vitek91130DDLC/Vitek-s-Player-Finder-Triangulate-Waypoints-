package com.example.triangulatewaypoint;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.waypoint.set.WaypointSet;

public class TriangulateWaypointMod implements ClientModInitializer {
    private static KeyMapping triangulateKey;
    private static RecordedPoint firstPoint = null;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("triangulate-waypoint", "triangulate-waypoint"));
        triangulateKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.triangulate-waypoint.triangulate",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (triangulateKey.consumeClick()) {
                onKeyPress(client);
            }
        });
    }

    private void onKeyPress(Minecraft client) {
        if (client.player == null || client.level == null)
            return;

        // Слишком крутой взгляд не даёт горизонтального направления
        if (Math.abs(client.player.getXRot()) > 89.0F) {
            client.player.displayClientMessage(
                    Component.literal("§cНельзя триангулировать: взгляд слишком крутой (pitch около ±90°)"), false);
            return;
        }

        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        float yaw = client.player.getYRot();

        if (firstPoint == null) {
            firstPoint = new RecordedPoint(x, y, z, yaw);
            client.player.displayClientMessage(Component.literal(
                    "§aПервая точка записана. Переместитесь, посмотрите в другом направлении и нажмите клавишу ещё раз."),
                    false);
        } else {
            RecordedPoint p1 = firstPoint;
            double x2 = x, y2 = y, z2 = z;
            float yaw2 = yaw;

            double yaw1Rad = Math.toRadians(p1.yaw);
            double yaw2Rad = Math.toRadians(yaw2);

            // Векторы направлений в плоскости XZ
            double vx1 = -Math.sin(yaw1Rad);
            double vz1 = Math.cos(yaw1Rad);
            double vx2 = -Math.sin(yaw2Rad);
            double vz2 = Math.cos(yaw2Rad);

            double det = vx2 * vz1 - vx1 * vz2;
            if (Math.abs(det) < 1e-6) {
                client.player.displayClientMessage(
                        Component.literal("§cЛучи параллельны (или почти), триангуляция невозможна."), false);
                firstPoint = null;
                return;
            }

            double dx = x2 - p1.x;
            double dz = z2 - p1.z;

            // t для первого луча
            double t = (dx * (-vz2) - (-vx2) * dz) / det;

            double ix = p1.x + t * vx1;
            double iz = p1.z + t * vz1;

            int wayX = (int) Math.round(ix);
            int wayY = (int) Math.round(y2);
            int wayZ = (int) Math.round(iz);

            try {
                MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
                if (minimapSession != null) {
                    MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
                    if (currentWorld != null) {
                        WaypointSet waypointSet = currentWorld.getCurrentWaypointSet();
                        if (waypointSet != null) {
                            Waypoint wp = new Waypoint(wayX, wayY, wayZ, "Intersection", "X", 0);
                            waypointSet.add(wp);
                            client.player.displayClientMessage(
                                    Component.literal("§aВейпоинт установлен: " + wayX + ", " + wayY + ", " + wayZ),
                                    false);
                        } else {
                            client.player.displayClientMessage(
                                    Component.literal("§cНе удалось получить текущий набор вейпоинтов."), false);
                        }
                    } else {
                        client.player.displayClientMessage(
                                Component.literal("§cНе удалось получить текущий мир Xaero."), false);
                    }
                } else {
                    client.player.displayClientMessage(
                            Component.literal(
                                    "§cНе удалось получить доступ к сессии Xaero's Minimap. Установлен ли мод?"),
                            false);
                }
            } catch (Exception e) {
                client.player.displayClientMessage(
                        Component.literal("§cОшибка при создании вейпоинта: " + e.getMessage()), false);
            }

            firstPoint = null;
        }
    }

    private static class RecordedPoint {
        double x, y, z;
        float yaw;

        RecordedPoint(double x, double y, double z, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }
}