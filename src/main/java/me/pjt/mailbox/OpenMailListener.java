/**
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

package me.pjt.mailbox;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class OpenMailListener implements Listener {
    private final OpenMail plugin;

    public OpenMailListener(OpenMail plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (this.plugin.akcia.get(player) == null) {
            return;
        }

        if (this.plugin.disable_in_creative && event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            player.sendMessage("You cannot use this while in creative mode");
            this.plugin.akcia.remove(player);
            return;
        }

        String result = this.plugin.createMailBox(player, (String) this.plugin.akcia.get(player), block);
        this.plugin.akcia.remove(player);
        player.sendMessage(result);
    }
}
