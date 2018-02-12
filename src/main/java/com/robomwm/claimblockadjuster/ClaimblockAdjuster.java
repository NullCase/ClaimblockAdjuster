package com.robomwm.claimblockadjuster;

import com.google.common.io.Files;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Created on 2/11/2018.
 *
 * @author RoboMWM
 */
public class ClaimblockAdjuster extends JavaPlugin
{
    private Economy economy;
    public void onEnable()
    {

    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (args.length < 2)
            return false;
        try
        {
            adjustPlayers(Double.valueOf(args[0]), Double.valueOf(args[0]));
            sender.sendMessage("See logs for details. Severe entries indicate issues with loading or saving a file.");
        }
        catch (Throwable rock)
        {
            return false;
        }
        return false;
    }


    private void adjustPlayers(double multiplier, double moneyValue)
    {
        int i = 0;
        for (OfflinePlayer player : getServer().getOfflinePlayers())
        {
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    PsuedoPlayerData psuedoPlayerData = getAndBackupTotalBlocks(player.getUniqueId());
                    if (psuedoPlayerData == null)
                    {
                        getLogger().severe("Skipping " + player.getUniqueId() + "(" + player.getName() + ")");
                        return;
                    }

                    double remainder;
                    double accrued = psuedoPlayerData.getAccruedBlocks() * multiplier;
                    double bonus = psuedoPlayerData.getBonusBlocks() * multiplier;
                    remainder = (accrued - (long)accrued) + (bonus - (long)bonus);
                    double money = remainder * moneyValue;
                    getLogger().info("Calculations for " + player.getUniqueId().toString() + "(" + player.getName() + "): "
                            + "Previous: " + psuedoPlayerData.getAccruedBlocks() + "," + psuedoPlayerData.getBonusBlocks()
                            + " Now: " + accrued + "," + bonus + " remainder: " + remainder + " money: " + money);
                    savePlayerData(player.getUniqueId(), (int)accrued, (int)bonus);
                    getEconomy().depositPlayer(player, money);
                }
            }.runTaskLater(this, i++);
        }
    }

    private Economy getEconomy()
    {
        if (economy != null)
            return economy;
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return null;
        }
        economy = rsp.getProvider();
        return economy;
    }

    private synchronized PsuedoPlayerData getAndBackupTotalBlocks(UUID playerID)
    {
        File playerFile = new File("plugins" + File.separator + "GriefPreventionData" + File.separator + "PlayerData" + File.separator + playerID.toString());

        if (!playerFile.exists())
        {
            this.getLogger().warning(playerID.toString() + " does not have a PlayerData file, skipping.");
            return null;
        }

        File backupFile = new File("plugins" + File.separator + "GriefPreventionData" + File.separator + "PlayerData" + File.separator + "_backup" + playerID.toString());

        if (backupFile.exists())
        {
            this.getLogger().severe("There's already a backup of " + playerID.toString());
            return null;
        }

        try
        {
            if (!backupFile.createNewFile())
            {
                this.getLogger().severe("Could not create backup file for " + playerID.toString());
                return null;
            }
        }
        catch(Throwable rock)
        {
            this.getLogger().severe("Could not create backup file for " + playerID.toString());
            rock.printStackTrace();
            return null;
        }

        try
        {
            Files.copy(playerFile, backupFile);
        }
        catch (Throwable rock)
        {
            this.getLogger().severe("Could not copy a backup for " + playerFile.toString());
            rock.printStackTrace();
            return null;
        }

        int accruedBlocks = 0;
        int bonusBlocks = 0;


                try
                {
                    //read the file content and immediately close it
                    List<String> lines = Files.readLines(playerFile, Charset.forName("UTF-8"));
                    Iterator<String> iterator = lines.iterator();


                    iterator.next();
                    //first line is last login timestamp //RoboMWM - not using this anymore
//
//    				//convert that to a date and store it
//    				DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
//    				try
//    				{
//    					playerData.setLastLogin(dateFormat.parse(lastLoginTimestampString));
//    				}
//    				catch(ParseException parseException)
//    				{
//    					GriefPrevention.AddLogEntry("Unable to load last login for \"" + playerFile.getName() + "\".");
//    					playerData.setLastLogin(null);
//    				}

                    //second line is accrued claim blocks
                    String accruedBlocksString = iterator.next();

                    //convert that to a number and store it
                    accruedBlocks = Integer.parseInt(accruedBlocksString);

                    //third line is any bonus claim blocks granted by administrators
                    String bonusBlocksString = iterator.next();

                    //convert that to a number and store it
                    bonusBlocks = Integer.parseInt(bonusBlocksString);

                    //fourth line is a double-semicolon-delimited list of claims, which is currently ignored
                    //String claimsString = inStream.readLine();
                    //iterator.next();
                }

                //if there's any problem with the file's content, retry up to 5 times with 5 milliseconds between
                catch(Exception e)
                {
                    e.printStackTrace();
                    return null;
                }
        return new PsuedoPlayerData(playerID, accruedBlocks, bonusBlocks);
    }

    boolean savePlayerData(UUID playerID, int newAccrued, int newBonus)
    {
        StringBuilder fileContent = new StringBuilder();
        try
        {
            //first line is last login timestamp //RoboMWM - no longer storing/using
            //if(playerData.getLastLogin() == null) playerData.setLastLogin(new Date());
            //DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
            //fileContent.append(dateFormat.format(playerData.getLastLogin()));
            fileContent.append("\n");

            //second line is accrued claim blocks
            fileContent.append(String.valueOf(newAccrued));
            fileContent.append("\n");

            //third line is bonus claim blocks
            fileContent.append(String.valueOf(newBonus));
            fileContent.append("\n");

            //fourth line is blank
            fileContent.append("\n");

            //write data to file
            File playerFile = new File("plugins" + File.separator + "GriefPreventionData" + File.separator + "PlayerData" + File.separator + playerID.toString());
            Files.write(fileContent.toString().getBytes("UTF-8"), playerFile);
        }

        //if any problem, log it
        catch(Exception e)
        {
            getLogger().severe("Could not save new values for " + playerID.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }
}

class PsuedoPlayerData
{
    private UUID uuid;
    private int accruedBlocks;
    private int bonusBlocks;

    PsuedoPlayerData(UUID uuid, int accrued, int bonus)
    {
        this.uuid = uuid;
        this.accruedBlocks = accrued;
        this.bonusBlocks = bonus;
    }

    public int getAccruedBlocks()
    {
        return accruedBlocks;
    }

    public int getBonusBlocks()
    {
        return bonusBlocks;
    }

    public UUID getUuid()
    {
        return uuid;
    }
}