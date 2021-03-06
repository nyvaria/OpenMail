/**
 * OpenMail - a bukkit plugin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.pjt.openmail;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OpenMail extends JavaPlugin {
    private final OpenMailListener MailBoxPlayerListener = new OpenMailListener(this);
    private final HashMap<Player, Boolean> debugees = new HashMap<Player, Boolean>();
    public final HashMap<Player, String> akcia = new HashMap<Player, String>();
    public final ArrayList<Record> cooldown = new ArrayList<Record>();
    public static String db_file = "db_file.db";
    public String mysql_database = "";
    public String mysql_user = "";
    public String mysql_pass = "";
    public boolean use_mysql = false;
    public int cooldown_limit = 20;
    public double delivery_fee = 5.0D;
    public double creating_fee = 5.0D;
    public double fee_per_1000m = 0.0D;
    public double foreign_fee = 0.0D;
    public double postman_profit = 0.0D;
    public boolean only_op = false;
    public boolean disable_in_creative = false;
    private static final Logger log = Logger.getLogger("Minecraft");
    public boolean economy_on = false;
    public static Economy economy = null;
    public boolean is_running = true;

    public boolean isRunning() {
        return this.is_running;
    }

    @Override
    public void onEnable() {
        File file = getDataFolder();
        db_file = getDataFolder() + "//" + db_file;

        if (!file.exists()) {
            file.mkdirs();
        }

        File settings = new File(file, "settings.txt");
        if (!settings.exists()) {
            try {
                settings.createNewFile();
                FileWriter fstream = new FileWriter(settings);
                BufferedWriter out = new BufferedWriter(fstream);

                out.write("cooldown_limit = 50\r\n");
                out.write("delivery_fee = 5.0\r\n");
                out.write("creating_fee = 0.0\r\n");
                out.write("fee_per_1000m = 4.0\r\n");
                out.write("foreign_fee = 10.0\r\n");
                out.write("postman_profit = 0\r\n");
                out.write("only_op = false\r\n");
                out.write("disable_in_creative = false\r\n");
                out.write("use_mysql = false\r\n");
                out.write("mysql_database = jdbc:mysql://localhost:3306/openmail\r\n");
                out.write("mysql_user = root\r\n");
                out.write("mysql_pass = password");
                out.close();
            } catch (IOException ex) {
                Logger.getLogger(OpenMail.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        Thread thread1 = new Thread(new Timer(this), "mailbox_timer");
        thread1.start();

        prepareSettings();

        this.economy_on = setupEconomy();
        if (!this.economy_on) {
            log.info("[MailBox] Economy plugin not found.");
        } else {
            log.log(Level.INFO, "[MailBox] Hooked into {0}", economy.getName());
        }

        prepareTables();

        //checkVersion();

        getServer().getPluginManager().registerEvents(new OpenMailListener(this), this);

        PluginDescriptionFile pdfFile = getDescription();
        log.log(Level.INFO, "{0} version {1} is enabled! (MySQL is {2}.)", new Object[]
                {
                        pdfFile.getName(), pdfFile.getVersion(), this.use_mysql ? "enabled" : "disabled"
                });
    }

    private Boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider rsp = getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        economy = (Economy) rsp.getProvider();
        return (economy != null);
    }

    public boolean checkpermissions(Player player, String string) {
        return (player.hasPermission(string)) || (player.isOp());
    }

    public void prepareSettings() {
        File file = getDataFolder();
        File settings = new File(file, "settings.txt");
        try {
            Scanner sc = new Scanner(settings);
            this.cooldown_limit = Integer.parseInt(sc.nextLine().split(" = ")[1]);
            this.delivery_fee = Double.parseDouble(sc.nextLine().split(" = ")[1]);
            this.creating_fee = Double.parseDouble(sc.nextLine().split(" = ")[1]);
            this.fee_per_1000m = Double.parseDouble(sc.nextLine().split(" = ")[1]);
            this.foreign_fee = Double.parseDouble(sc.nextLine().split(" = ")[1]);
            this.postman_profit = Double.parseDouble(sc.nextLine().split(" = ")[1]);
            this.only_op = sc.nextLine().split(" = ")[1].equals("true");
            this.disable_in_creative = sc.nextLine().split(" = ")[1].equals("true");
            this.use_mysql = sc.nextLine().split(" = ")[1].equals("true");
            this.mysql_database = sc.nextLine().split(" = ")[1];
            this.mysql_user = sc.nextLine().split(" = ")[1];
            this.mysql_pass = sc.nextLine().split(" = ")[1];
        } catch (Exception e) {
            log.warning("Problem with config file. Check file: plugin/MailBox/settings.txt");
        }
    }

    @Override
    public void onDisable() {
        this.is_running = false;
        log.log(Level.INFO, "Plugin MailBox shutting down...");
    }

    public Connection prepareSQL() {
        Connection conn = null;
        try {
            if (this.use_mysql) {
                conn = DriverManager.getConnection(this.mysql_database + "?autoReconnect=true&user=" + this.mysql_user + "&password=" + this.mysql_pass);
            } else {
                Class.forName("org.sqlite.JDBC");
                conn = DriverManager.getConnection("jdbc:sqlite:" + db_file);
            }
        } catch (Exception e) {
            log.warning("Problem with connection to SQL database. Check settings in plugin/MailBox/settings.txt file");
        }
        return conn;
    }

    public void prepareTables() {
        Connection conn = prepareSQL();

        boolean alter = false;
        try {
            Statement stmt = conn.createStatement();

            String QueryString = "CREATE TABLE IF NOT EXISTS mailboxes (playername VARCHAR(255) PRIMARY KEY, coordinates VARCHAR(20), world VARCHAR(30), date BIGINT)";

            stmt.executeUpdate(QueryString);

            QueryString = "CREATE TABLE IF NOT EXISTS logs (sender VARCHAR(255), receiver VARCHAR(255), material VARCHAR(50), amount SMALLINT, date BIGINT)";

            stmt.executeUpdate(QueryString);

            alter = true;
            QueryString = "ALTER TABLE mailboxes ADD createdby VARCHAR(255)";
            stmt.executeUpdate(QueryString);

            conn.close();
        } catch (SQLException e) {
            if (!alter) {
                log.log(Level.WARNING, "Problem with SQL. Check config file /plugin/MailBox/settings.txt");
            }
        }
    }

    public String removeMailBox(Player player, String owner) {
        Connection conn = prepareSQL();
        try {
            Statement stmt = conn.createStatement();
            String replace = owner.replace('\'', 'x');

            boolean remove_self = true;
            if (!player.getName().equals(owner)) {
                remove_self = false;
            }

            String QueryString = "SELECT * FROM mailboxes WHERE LOWER(playername) = '"
                    + owner.toLowerCase() + "'";

            ResultSet rs = stmt.executeQuery(QueryString);

            boolean remove = false;
            if (!rs.next()) {
                if (remove_self) {
                    return "You don't have a openmail yet! Use §c/openmail create§f to create it.";
                }
                return "Player " + owner + " don't have a openmail!";
            }

            if (remove_self) {
                remove = true;
            }
            if (checkpermissions(player, "openmail.admin.removeany")) {
                remove = true;
            }
            if ((checkpermissions(player, "openmail.postman.removeother")) && (rs.getString("createdby").equals(player.getName()))) {
                remove = true;
            }

            if (!remove) {
                return "§cYou don't have permission for remove this openmail!";
            }

            String DeleteQuery = "DELETE FROM mailboxes WHERE LOWER(playername) = '" + owner.toLowerCase() + "'";

            stmt.execute(DeleteQuery);

            log.log(Level.INFO, "MailBox: {0} has removed {1}''s openmail.", new Object[]
                    {
                            player.getName(), owner
                    });

            if (remove_self) {
                return "§aMailbox removed successfully.";
            }
            Player pl = Bukkit.getServer().getPlayer(owner);
            if (pl != null) {
                pl.sendMessage("§cYour openmail was removed by " + player.getName() + "!");
            }
            return "§a" + owner + "'s openmail removed successfully.";
        } catch (SQLException e) {
            log.log(Level.WARNING, "Problem with SQL. Check config file /plugin/MailBox/settings.txt");
        }

        return "§4Error!";
    }

    public String createMailBox(Player player, String owner, Block block) {
        Connection conn = prepareSQL();
        String replace = owner.replace('\'', 'x');

        boolean self_creating = true;
        Player pl = null;

        if (!owner.equals(player.getName())) {
            self_creating = false;
            pl = Bukkit.getServer().getPlayer(owner);
            if (pl == null) {
                return "Player " + owner + " must be online to have a openmail created!";
            }
            owner = pl.getName();
        }
        try {
            Statement stmt = conn.createStatement();

            String QueryString = "SELECT COUNT(*) AS pocet FROM mailboxes WHERE LOWER(playername) = '"
                    + owner.toLowerCase() + "'";

            ResultSet rs = stmt.executeQuery(QueryString);
            rs.next();

            if (rs.getInt("pocet") > 0) {
                conn.close();
                if (self_creating) {
                    return "You already have a openmail! Use §c/openmail remove§f to remove it.";
                }
                return pl.getName() + " already has a openmail!";
            }

            if (block.getType() != Material.CHEST) {
                return "§cBlock is not a chest!";
            }

            String loc = block.getX() + ";" + block.getY() + ";" + block.getZ();

            String coordinatesQuery = "SELECT COUNT(*) AS pocet FROM mailboxes WHERE coordinates = '"
                    + loc + "'";

            ResultSet rs2 = stmt.executeQuery(coordinatesQuery);
            rs2.next();

            if (rs2.getInt("pocet") > 0) {
                conn.close();
                return "§cOn this position is already created openmail. Choose another one.";
            }

            if (this.economy_on) {
                if (!economy.has(player, this.creating_fee)) {
                    player.sendMessage("§cYou do not have enough money for create a openmail!");
                    return "Fee is §b" + economy.format(this.creating_fee) + "§f.";
                }
                economy.depositPlayer(player, -this.creating_fee);
            }

            String InsertQuery = "INSERT INTO mailboxes (playername, createdby, coordinates, world, date) VALUES ('"
                    + owner + "', '"
                    + player.getName() + "', '"
                    + loc + "', '"
                    + player.getWorld().getName() + "', "
                    + System.currentTimeMillis() + ")";

            stmt.execute(InsertQuery);
            log.log(Level.INFO, "MailBox: {0} has created the openmail for {1}. [{2}]", new Object[]
                    {
                            player.getName(), owner, loc
                    });

            if (self_creating) {
                return "§aMailbox created successfully.";
            }
            pl.sendMessage("§aYour openmail was created successfully by " + player.getName() + "!");
            return "§a" + pl.getName() + "'s openmail created successfully.";
        } catch (SQLException e) {
            log.log(Level.WARNING, "Problem with SQL. Check config file /plugin/MailBox/settings.txt");
        }

        return "§4Error!";
    }

    public void viewLog(Player player, int page) {
        Connection conn = prepareSQL();
        try {
            Statement stmt = conn.createStatement();

            int limit = 5;
            int from = page * limit;
            String query = "SELECT * FROM logs ORDER BY date DESC LIMIT " + from + ", " + limit;

            ResultSet rs = stmt.executeQuery(query);
            ArrayList ar = new ArrayList();

            if (!rs.next()) {
                player.sendMessage("§cNo records on this page!");
                return;
            }

            player.sendMessage("§cPage: " + (page + 1));
            do {
                String line = "§a" + rs.getString("sender") + "§f --> §b"
                        + rs.getString("receiver") + "§f :  " + rs.getString("material") + " §c" + rs.getInt("amount");
                ar.add(line);
            }
            while (rs.next());
            for (int i = ar.size() - 1; i >= 0; i--) {
                player.sendMessage((String) ar.get(i));
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "Problem with SQL. Check config file /plugin/MailBox/settings.txt");

            player.sendMessage("§4Error!");
        }
    }

    public String check(Player sender, String receiver) {
        receiver = receiver.replace('\'', 'x');

        Connection conn = prepareSQL();

        Server server = Bukkit.getServer();
        try {
            Statement stmt = conn.createStatement();

            String QueryString = "SELECT * FROM mailboxes WHERE LOWER(playername) = '" + receiver.toLowerCase() + "'";

            ResultSet rs = stmt.executeQuery(QueryString);
            boolean naslo = rs.next();

            if (!naslo) {
                return "§cPlayer " + receiver + " doesn't have a openmail!";
            }
            receiver = rs.getString("playername");
            String world = rs.getString("world");
            String[] suradnice = rs.getString("coordinates").split(";");
            int x = Integer.parseInt(suradnice[0]);
            int y = Integer.parseInt(suradnice[1]);
            int z = Integer.parseInt(suradnice[2]);

            Location l = new Location(server.getWorld(world), x, y, z);

            if (l.getBlock().getType() != Material.CHEST) {
                return "§c" + receiver + "'s openmail is damaged!";
            }

            double fee;
            double distance = 0.0D;
            double distance_fee = 0.0D;

            DecimalFormat df = new DecimalFormat("0.00");
            boolean foreign = false;

            if (!sender.getWorld().equals(l.getWorld())) {
                fee = this.delivery_fee + this.foreign_fee;
                foreign = true;
            } else {
                distance = sender.getLocation().distance(l);
                distance_fee = distance / 1000.0D * this.fee_per_1000m;
                fee = this.delivery_fee + distance_fee;
            }

            if (this.economy_on) {
                sender.sendMessage("Fee for sending package to " + receiver + " is §b" + economy.format(fee) + "§f.");
                if (foreign) {
                    sender.sendMessage("§b" + df.format(this.delivery_fee) + "§f is fixed fee + §b" + df.format(this.foreign_fee) + "§f is fee for sending package to another world.");
                } else {
                    sender.sendMessage("§b" + df.format(this.delivery_fee) + "§f is fixed fee + §b" + df.format(distance_fee) + "§f is fee for distance. (" + (int) distance + " m)");
                }
            } else {
                sender.sendMessage("economy is off!");
            }
            return "";
        } catch (SQLException e) {
            log.log(Level.WARNING, "Problem with SQL. Check config file /plugin/MailBox/settings.txt");
        }

        return "§4Error!";
    }

    public String sendPackage(Player sender, String receiver) {
        receiver = receiver.replace('\'', 'x');

        if ((!checkpermissions(sender, "openmail.sendtoself")) && (sender.getName().toLowerCase().equals(receiver.toLowerCase()))) {
            return "§cYou can't send package to yourself!";
        }

        int cas;
        try {
            for (Record aCooldown : this.cooldown) {
                if (((Record) aCooldown).getPl().equalsIgnoreCase(sender.getName())) {
                    cas = ((Record) aCooldown).getTime();
                    return "§fYou have to wait §b" + cas + "§f seconds for sending new package.";
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Problem with cooldown thread!");
        }

        Connection conn = prepareSQL();

        Server server = Bukkit.getServer();
        try {
            Statement stmt = conn.createStatement();

            String QueryString = "SELECT * FROM mailboxes WHERE LOWER(playername) = '" + receiver.toLowerCase() + "'";

            ResultSet rs = stmt.executeQuery(QueryString);
            boolean naslo = rs.next();

            if (!naslo) {
                return "§cPlayer " + receiver + " doesn't have a openmail!";
            }
            receiver = rs.getString("playername");
            String postman = rs.getString("createdby");

            String world = rs.getString("world");
            String[] suradnice = rs.getString("coordinates").split(";");
            int x = Integer.parseInt(suradnice[0]);
            int y = Integer.parseInt(suradnice[1]);
            int z = Integer.parseInt(suradnice[2]);

            Location l = new Location(server.getWorld(world), x, y, z);

            double distance = 0.0D;
            double distance_fee = 0.0D;
            double fee;

            DecimalFormat df = new DecimalFormat("0.00");
            boolean foreign = false;

            if (!sender.getWorld().equals(l.getWorld())) {
                fee = this.delivery_fee + this.foreign_fee;
                foreign = true;
            } else {
                distance = sender.getLocation().distance(l);
                distance_fee = distance / 1000.0D * this.fee_per_1000m;
                fee = this.delivery_fee + distance_fee;
            }

            if (this.economy_on) {
                if (!economy.has(sender, fee)) {
                    sender.sendMessage("§cYou don't have enough money for send a package!");
                    sender.sendMessage("Fee is §b" + economy.format(fee) + "§f.");
                    if (foreign) {
                        sender.sendMessage("§b" + df.format(this.delivery_fee) + "§f is fixed fee + §b" + df.format(this.foreign_fee) + "§f is fee for sending package to another world.");
                    } else {
                        sender.sendMessage("§b" + df.format(this.delivery_fee) + "§f is fixed fee + §b" + df.format(distance_fee) + "§f is fee for distance. (" + (int) distance + " m)");
                    }
                    return "";
                }
                economy.depositPlayer(sender, -fee);
            }

            Chest chest = (Chest) l.getBlock().getState();

            ItemStack _package = sender.getInventory().getItemInHand();

            if (_package.getType() == Material.AIR) {
                if (this.economy_on) {
                    economy.depositPlayer(sender, fee);
                }
                return "§cYou have nothing in your hand!";
            }

            ItemStack package_copy = new ItemStack(_package.getType(), _package.getAmount());
            package_copy.setDurability(_package.getDurability());

            HashMap nevoslo = chest.getInventory().addItem(_package);

            if (nevoslo.size() > 0) {
                chest.getInventory().removeItem(new ItemStack(_package.getType(), _package.getAmount() - _package.getAmount()));
                sender.setItemInHand(package_copy);
                Player pl = server.getPlayer(receiver);
                if (pl != null) {
                    pl.sendMessage("§cYour openmail is full!");
                }
                if (this.economy_on) {
                    return "§c" + receiver + "'s openmail is full!";
                    //economy.depositPlayer(sender.getName(), fee);
                }
                return "§c" + receiver + "'s openmail is full!";
            }

            sender.getInventory().clear(sender.getInventory().getHeldItemSlot());
            Player pl = server.getPlayer(receiver);

            if (pl != null) {
                pl.sendMessage("§aYou have new package in your openmail!");
            }

            if ((this.economy_on) && (!postman.equalsIgnoreCase(receiver))) {
                economy.depositPlayer(postman, fee * this.postman_profit);
            }

            if (!checkpermissions(sender, "openmail.nocooldown")) {
                this.cooldown.add(new Record(sender.getName(), this.cooldown_limit));
            }
            log.log(Level.INFO, "MailBox: {0} has sent the package to {1}. [{2}:{3}]", new Object[]
                    {
                            sender.getName(), receiver, _package.getType(), package_copy.getAmount()
                    });

            String InsertQuery = "INSERT INTO logs (sender, receiver, material, amount, date) VALUES ('"
                    + sender.getName() + "', '"
                    + receiver + "', '"
                    + package_copy.getType() + "', "
                    + package_copy.getAmount() + ", "
                    + System.currentTimeMillis() + ")";

            stmt.execute(InsertQuery);

            return "§aPackage was successfully received.";
        } catch (SQLException e) {
            log.log(Level.WARNING, "Problem with SQL. Check config file /plugin/MailBox/settings.txt");
        }
        return "§4Error!";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if ((!command.getName().equalsIgnoreCase("mail")) && (!command.getName().equalsIgnoreCase("send"))) {
            return false;
        }

        if (!(sender instanceof Player)) {
            if ((args.length == 1) && (args[0].equalsIgnoreCase("reload"))) {
                prepareSettings();
                log.log(Level.INFO, "Settings reloaded");
                return true;
            }
            log.log(Level.INFO, "You can't use this command in console!");
            return true;
        }

        Player player = (Player) sender;

        if ((command.getName().equalsIgnoreCase("send")) && (args.length == 0)) {
            player.sendMessage("For sending §c/send <player_name>");
            return true;
        }

        if ((args.length == 0) || (args[0].equals("help"))) {
            player.sendMessage("§OpenMail Plugin help:");
            player.sendMessage("§c/mail fees §f- Check the fees.");
            player.sendMessage("§c/mail help §f- Plugin help.");
            player.sendMessage("§c/mail createbox §f- Create a openmail.");
            player.sendMessage("§c/mail removebox §f- Remove a openmail.");
            player.sendMessage("§c/v check <player_name> §f- Check the delivery fee for sending the package to <player_name>.");
            player.sendMessage("§c/mail send <player_name> §f- Send a package to a player.");

            //TODO: remove op references
            if ((checkpermissions(player, "mail.postman.createother")) || (player.isOp())) {
                player.sendMessage("§c/mail create <player_name> §f- Create a openmail for someone else.");
            }

            if ((checkpermissions(player, "openmail.postman.removeother")) || (checkpermissions(player, "openmail.admin.removeany")) || (player.isOp())) {
                player.sendMessage("§c/mail remove <player_name> §f- Remove <player_name>'s openmail.");
            }

            if ((checkpermissions(player, "openmail.admin.viewlog")) || (player.isOp())) {
                player.sendMessage("§c/mail view [page] §f- View a log.");
            }
            return true;
        }

        if (args[0].equals("create")) {
            if ((!checkpermissions(player, "openmail.user.create")) && (!checkpermissions(player, "openmail.postman.createother")) && (this.only_op)) {
                player.sendMessage("§cYou don't have a permission to create a openmail!");
                return true;
            }

            String createfor = player.getName();

            if ((checkpermissions(player, "openmail.postman.createother")) && (args.length == 2)) {
                createfor = args[1];
                player.sendMessage("§2Please left click chest to create " + createfor + "'s openmail.");
            } else {
                player.sendMessage("§2Please left click your chest to create your openmail.");
            }

            if (this.akcia.get(player) != null) {
                this.akcia.remove(player);
            }
            this.akcia.put(player, createfor);
            return true;
        }

        if (args[0].equals("remove")) {
            if ((!checkpermissions(player, "openmail.user.remove")) && (!checkpermissions(player, "openmail.postman.removeother")) && (!checkpermissions(player, "openmail.admin.removeany")) && (this.only_op)) {
                player.sendMessage("§cYou don't have a permission to remove a openmail!");
                return true;
            }

            String owner = player.getName();
            if (args.length == 2) {
                owner = args[1];
            }
            String r = removeMailBox(player, owner);
            player.sendMessage(r);
            return true;
        }

        if ((args[0].equals("send")) || (command.getName().equalsIgnoreCase("send"))) {
            if ((!checkpermissions(player, "openmail.user.send")) && (this.only_op)) {
                player.sendMessage("§cYou don't have a permission to send a package!");
                return true;
            }

            String playername;

            if (command.getName().equalsIgnoreCase("send")) {
                if (args.length == 0) {
                    player.sendMessage("For send a package write: §c/send <player_name>");
                    return true;
                }
                playername = args[0];
            } else {
                if (args.length == 1) {
                    player.sendMessage("For send a package write: §c/mail send <player_name>");
                    return true;
                }
                playername = args[1];
            }

            String r = sendPackage(player, playername);
            player.sendMessage(r);
            return true;
        }

        if (args[0].equals("check")) {
            String playername;
            if (args.length == 1) {
                player.sendMessage("To check a fee for delivery a package, write: §c/mail check <player_name>");
                return true;
            }
            playername = args[1];
            String r = check(player, playername);
            player.sendMessage(r);
            return true;
        }

        if ((args[0].equals("view")) || (args[0].equals("viewlog"))) {
            if (!checkpermissions(player, "openmail.admin.viewlog")) {
                player.sendMessage("§cYou don't have a permission to view a log!");
                return true;
            }

            int page = 0;

            if (args.length == 2) {
                try {
                    page = Integer.parseInt(args[1]);
                    page--;
                } catch (Exception e) {
                    player.sendMessage("§cWrong page number!");
                }
            }

            viewLog(player, page);
        }

        if (args[0].equals("fees")) {
            if (!this.economy_on) {
                player.sendMessage("§ceconomy is off!");
            } else {
                player.sendMessage("The fee for creating the openmail is §b" + economy.format(this.creating_fee) + "§f.");
                player.sendMessage("Fixed fee for delivery is §b" + economy.format(this.delivery_fee) + "§f");
                player.sendMessage("+  §b" + economy.format(this.fee_per_1000m) + "§f per 1000 meters. (In the same World)");
                player.sendMessage("OR §b" + economy.format(this.foreign_fee) + "§f when you send package to another World.");
            }
        }

        return true;
    }

    public boolean isDebugging(Player player) {
        return this.debugees.containsKey(player) && (Boolean) this.debugees.get(player);
    }

    public void setDebugging(Player player, boolean value) {
        this.debugees.put(player, value);
    }
}