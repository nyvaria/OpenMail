package me.iaccidentally.mailbox;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;


public class MailBoxPlayerListener implements Listener
{
	private final MailBoxPlugin plugin;

	public MailBoxPlayerListener(MailBoxPlugin plugin)
	{
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		if (event.getAction() != Action.LEFT_CLICK_BLOCK)
		{
			return;
		}
		Player player = event.getPlayer();
		Block block = event.getClickedBlock();

		if (this.plugin.akcia.get(player) == null)
		{
			return;
		}
		if (this.plugin.disable_in_creative == true && event.getPlayer().getGameMode() == GameMode.CREATIVE)
		{
			player.sendMessage("You cannot use this while in creative mode");
			this.plugin.akcia.remove(player);
			return;
		}
			String result = this.plugin.createMailBox(player, (String)this.plugin.akcia.get(player), block);
			this.plugin.akcia.remove(player);
			player.sendMessage(result);
	}
}