/**
 *  Copyright (C) 2002-2016   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.networking;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Game.LogoutReason;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.FreeColServer.ServerState;
import net.sf.freecol.server.control.ChangeSet;
import net.sf.freecol.server.control.ChangeSet.See;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;

import org.w3c.dom.Element;


/**
 * The message sent when logging out.
 */
public class LogoutMessage extends AttributeMessage {

    public static final String TAG = "logout";
    private static final String PLAYER_TAG = "player";
    private static final String REASON_TAG = "reason";


    /**
     * Create a new {@code LogoutMessage}.
     *
     * Note: The logout reason is non-i18n for now as it is just logged.
     *
     * @param player The {@code Player} that has logged out.
     * @param reason A reason for logging out.
     */
    public LogoutMessage(Player player, LogoutReason reason) {
        super(TAG, PLAYER_TAG, player.getId(),
              REASON_TAG, String.valueOf(reason));
    }

    /**
     * Create a new {@code LogoutMessage} from a supplied element.
     *
     * @param game The {@code Game} this message belongs to.
     * @param element The {@code Element} to use to create the message.
     */
    public LogoutMessage(Game game, Element element) {
        super(TAG, PLAYER_TAG, getStringAttribute(element, PLAYER_TAG),
              REASON_TAG, getStringAttribute(element, REASON_TAG));
    }


    // Public interface

    /**
     * Get the player logging out.
     *
     * @param game A {@code Game} to find the player in.
     * @return The player found.
     */
    public Player getPlayer(Game game) {
        return game.getFreeColGameObject(getAttribute(PLAYER_TAG), Player.class);
    }

    /**
     * Get the reason for logging out.
     *
     * @return The {@code LogoutReason}.
     */
    public LogoutReason getReason() {
        return Enum.valueOf(LogoutReason.class, getAttribute(REASON_TAG));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        if (serverPlayer == null) return null;
        logger.info("Handling logout by " + serverPlayer.getName());

        LogoutReason reason = getReason();
        ChangeSet cs = null;
        switch (freeColServer.getServerState()) {
        case PRE_GAME: case LOAD_GAME:
            break;
        case IN_GAME:
            if (freeColServer.getSinglePlayer()) {
                ; // Allow quit if specified
            } else if (serverPlayer.isAdmin() && reason == LogoutReason.QUIT) {
                // If the multiplayer admin quits, its all over
                freeColServer.endGame();
            } else {
                if (freeColServer.getGame().getCurrentPlayer() == serverPlayer) {
                    cs = freeColServer.getInGameController()
                        .endTurn(serverPlayer);
                }
                // FIXME: Turn serverPlayer into AI?
            }
            break;
        case END_GAME:
            return null;
        }

        // Inform the client
        if (cs == null) cs = new ChangeSet();
        cs.add(See.only(serverPlayer), ChangeSet.ChangePriority.CHANGE_NORMAL,
               new LogoutMessage(serverPlayer, reason));

        // Update the metaserver
        freeColServer.updateMetaServer(false);

        return cs;
    }
}
