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

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Timer implements Runnable {
    private OpenMail plugin;

    public Timer(OpenMail plugin) {
        this.plugin = plugin;
    }

    public void checkCooldown() {
        try {
            ArrayList h = this.plugin.cooldown;

            for (int i = 0; i < h.size(); i++) {
                Record r = (Record) h.get(i);
                if (r.getTime() <= 1) {
                    h.remove(i);
                    i--;
                } else {
                    r.setTime(r.getTime() - 1);
                }
            }
        } catch (Exception e) {
            System.out.println("Problem with cooldown thread!");
        }
        try {
            TimeUnit.MILLISECONDS.sleep(1000L);
        } catch (InterruptedException localInterruptedException) {
            localInterruptedException.printStackTrace();
        }
    }

    public void run() {
        System.out.println("[MailBox] New thread running.");
        while (this.plugin.isRunning()) {
            checkCooldown();
        }
    }
}