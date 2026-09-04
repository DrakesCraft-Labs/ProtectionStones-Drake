package dev.espi.protectionstones.commands;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSPlayer;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Regenera el terreno y chunks contenidos dentro de una proteccion territorial
 * utilizando el motor de WorldEdit / FAWE y opcionalmente elimina el reclamo.
 */
public class ArgAdminRegen {

    public static boolean argumentAdminRegen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("protectionstones.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para ejecutar este comando.");
            return true;
        }

        // Sintaxis: /ps admin regen [id_region] [mundo (opcional)] [--delete|--unclaim]
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /ps admin regen [id_region] [mundo] [--delete]");
            return true;
        }

        String regionId = args[2];
        World world = null;
        boolean deleteAfter = false;

        for (int i = 3; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--delete") || args[i].equalsIgnoreCase("--unclaim")) {
                deleteAfter = true;
            } else if (world == null) {
                world = Bukkit.getWorld(args[i]);
            }
        }

        if (world == null) {
            if (sender instanceof Player p) {
                world = p.getWorld();
            } else {
                sender.sendMessage(ChatColor.RED + "Debes especificar el mundo desde la consola: /ps admin regen [id_region] [mundo] [--delete]");
                return true;
            }
        }

        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (rm == null || !rm.hasRegion(regionId)) {
            sender.sendMessage(ChatColor.RED + "No se encontro la region '" + regionId + "' en el mundo '" + world.getName() + "'.");
            return true;
        }

        ProtectedRegion wg = rm.getRegion(regionId);
        PSRegion ps = PSRegion.fromWGRegion(world, wg);

        boolean success = regenerateRegion(world, wg, sender);
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "[PS] Terreno de la region " + regionId + " regenerado con exito.");
            if (deleteAfter && ps != null) {
                ps.deleteRegion(true);
                sender.sendMessage(ChatColor.YELLOW + "[PS] Region " + regionId + " eliminada y desprotegida.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "[PS] Error al intentar regenerar la region " + regionId + ".");
        }

        return true;
    }

    public static boolean argumentAdminRegenPlayer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("protectionstones.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para ejecutar este comando.");
            return true;
        }

        // Sintaxis: /ps admin regenplayer [jugador] [--delete|--unclaim]
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /ps admin regenplayer [jugador] [--delete]");
            return true;
        }

        String targetName = args[2];
        boolean deleteAfter = false;
        for (int i = 3; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--delete") || args[i].equalsIgnoreCase("--unclaim")) {
                deleteAfter = true;
            }
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUUID = target.getUniqueId();
        PSPlayer psPlayer = PSPlayer.fromUUID(targetUUID);

        if (psPlayer == null) {
            sender.sendMessage(ChatColor.RED + "No se encontro informacion territorial para el jugador " + targetName);
            return true;
        }

        List<PSRegion> regions = psPlayer.getPSRegionsCrossWorld(null, false);
        if (regions == null || regions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "El jugador " + targetName + " no posee ninguna proteccion activa.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "[PS] Regenerando " + regions.size() + " protecciones de " + targetName + "...");
        int count = 0;

        for (PSRegion r : new ArrayList<>(regions)) {
            World w = r.getWorld();
            ProtectedRegion wg = r.getWGRegion();
            if (w != null && wg != null) {
                if (regenerateRegion(w, wg, sender)) {
                    count++;
                    if (deleteAfter) {
                        r.deleteRegion(true);
                    }
                }
            }
        }

        sender.sendMessage(ChatColor.GREEN + "[PS] Proceso completado: " + count + " regiones regeneradas" + (deleteAfter ? " y eliminadas." : "."));
        return true;
    }

    public static boolean regenerateRegion(World world, ProtectedRegion wg, CommandSender feedback) {
        try {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
            BlockVector3 min = wg.getMinimumPoint();
            BlockVector3 max = wg.getMaximumPoint();
            CuboidRegion cuboid = new CuboidRegion(weWorld, min, max);

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                weWorld.regenerate(cuboid, editSession);
                editSession.flushSession();
            }
            return true;
        } catch (Throwable t) {
            ProtectionStones.getInstance().getLogger().severe("Error regenerando region " + wg.getId() + ": " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }
}
