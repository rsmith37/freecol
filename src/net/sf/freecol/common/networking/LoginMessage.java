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

import java.util.stream.Collectors;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.networking.AddPlayerMessage;
import net.sf.freecol.common.networking.ErrorMessage;
import net.sf.freecol.common.networking.SetAIMessage;
import static net.sf.freecol.common.util.CollectionUtils.*;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.FreeColServer.GameState;
import net.sf.freecol.server.control.ChangeSet;
import net.sf.freecol.server.model.ServerPlayer;

import org.w3c.dom.Element;


/**
 * The message sent when logging in.
 */
public class LoginMessage extends DOMMessage {

    public static final String TAG = "login";
    private static final String CURRENT_PLAYER_TAG = "currentPlayer";
    private static final String SINGLE_PLAYER_TAG = "singlePlayer";
    private static final String START_GAME_TAG = "startGame";
    private static final String USER_NAME_TAG = "userName";
    private static final String VERSION_TAG = "version";
    
    /** The user name. */
    private final String userName;

    /** The client FreeCol version. */
    private final String version;

    /** Whether to start the game. */
    private final boolean startGame;

    /** Is this a single player game. */
    private final boolean singlePlayer;

    /** Is the client the current player. */
    private final boolean currentPlayer;

    /** The game. */
    private final Game game;

        
    /**
     * Create a new {@code LoginMessage} with the supplied parameters.
     *
     * @param userName The name of the user logging in.
     * @param version The version of FreeCol at the client.
     * @param startGame Whether to start the game.
     * @param singlePlayer True in single player games.
     * @param currentPlayer True if this player is the current player.
     * @param game The entire game.
     */
    public LoginMessage(String userName, String version,
                        boolean startGame, boolean singlePlayer,
                        boolean currentPlayer, Game game) {
        super(TAG);

        this.userName = userName;
        this.version = version;
        this.startGame = startGame;
        this.singlePlayer = singlePlayer;
        this.currentPlayer = currentPlayer;
        this.game = game;
    }

    /**
     * Create a new simple {@code LoginMessage} request.
     *
     * @param userName The name of the user logging in.
     * @param start Start the game at once.
     * @param version The version of FreeCol at the client.
     */
    public LoginMessage(String userName, boolean start, String version) {
        this(userName, version, start, false, false, null);
    }

    /**
     * Create a new {@code LoginMessage} from a supplied element.
     *
     * @param game A {@code Game} (not used).
     * @param e The {@code Element} to use to create the message.
     */
    public LoginMessage(Game game, Element e) {
        this(getStringAttribute(e, USER_NAME_TAG),
             getStringAttribute(e, VERSION_TAG),
             getBooleanAttribute(e, START_GAME_TAG, false),
             getBooleanAttribute(e, SINGLE_PLAYER_TAG, true),
             getBooleanAttribute(e, CURRENT_PLAYER_TAG, false),
             getChild(game, e, 0, Game.class));
    }


    // Public interface

    public String getUserName() {
        return this.userName;
    }

    public String getVersion() {
        return this.version;
    }

    public boolean getStartGame() {
        return this.startGame;
    }

    public boolean getSinglePlayer() {
        return this.singlePlayer;
    }

    public boolean getCurrentPlayer() {
        return this.currentPlayer;
    }

    public Game getGame() {
        return this.game;
    }

    /**
     * Get the player (if any) with the current name in a given game.
     *
     * @param game The {@code Game} to look up.
     * @return The {@code ServerPlayer} found.
     */
    public ServerPlayer getPlayerByName(Game game) {
        return (ServerPlayer)game.getPlayerByName(this.userName);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ChangeSet serverHandler(FreeColServer freeColServer,
                                   ServerPlayer serverPlayer) {
        // Note: At this point serverPlayer is just a stub, with only
        // the connection infomation being valid.
        
        // FIXME: Do not allow more than one (human) player to connect
        // to a single player game. This would be easy if we used a
        // dummy connection for single player games.

        if (this.userName == null || this.userName.isEmpty()) {
            return ChangeSet.clientError((ServerPlayer)null, StringTemplate
                .template("server.missingUserName"));
        } else if (this.version == null || this.version.isEmpty()) {
            return ChangeSet.clientError((ServerPlayer)null, StringTemplate
                .template("server.missingVersion"));
        } else if (!this.version.equals(FreeCol.getVersion())) {
            return ChangeSet.clientError((ServerPlayer)null, StringTemplate
                .template("server.wrongFreeColVersion")
                .addName("%clientVersion%", this.version)
                .addName("%serverVersion%", FreeCol.getVersion()));
        }

        Connection conn = serverPlayer.getConnection();
        Game game;
        boolean isCurrentPlayer = false;

        switch (freeColServer.getGameState()) {
        case STARTING_GAME:
            // Wait until the game has been created.
            // FIXME: is this still needed?
            int timeOut = 20000;
            while ((game = freeColServer.getGame()) == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                if ((timeOut -= 1000) <= 0) {
                    return ChangeSet.clientError((ServerPlayer)null,
                        StringTemplate.template("server.timeOut"));
                }
            }

            Nation nation = game.getVacantNation();
            if (nation == null) {
                return ChangeSet.clientError((ServerPlayer)null, StringTemplate
                    .template("server.maximumPlayers"));
            } else if (game.playerNameInUse(this.userName)) {
                return ChangeSet.clientError((ServerPlayer)null, StringTemplate
                    .template("server.userNameInUse")
                    .addName("%name%", this.userName));
            }

            // Complete initialization...
            serverPlayer.initialize(game, game.getLivePlayerList().isEmpty(),
                                    nation);
            // ... but override player name.
            serverPlayer.setName(this.userName);

            // Add the new player and inform all other players
            game.addPlayer(serverPlayer);
            freeColServer.sendToAll(new AddPlayerMessage(serverPlayer),
                                    serverPlayer);

            // Ensure there is a current player.
            if (game.getCurrentPlayer() == null) {
                game.setCurrentPlayer(serverPlayer);
            }

            // Ready now to handle pre-game messages.
            conn.setMessageHandler(freeColServer.getPreGameInputHandler());
            freeColServer.getServer().addConnection(conn);
            freeColServer.updateMetaServer(false);
            return ChangeSet.simpleChange(serverPlayer,
                new LoginMessage(this.userName, this.version, this.startGame,
                                 freeColServer.getSinglePlayer(),
                                 game.getCurrentPlayer() == serverPlayer,
                                 game));

        case IN_GAME:
            if (FreeColServer.MAP_EDITOR_NAME.equals(this.userName)) {
                // Trying to start a map, see BR#2976 -> IR#217
                return ChangeSet.clientError((ServerPlayer)null, StringTemplate
                    .template("error.mapEditorGame"));
            }
            // Restoring from existing game.
            game = freeColServer.getGame();
            ServerPlayer present = getPlayerByName(game);
            if (present == null) {
                return ChangeSet.clientError((ServerPlayer)null, StringTemplate
                    .template("server.userNameNotPresent")
                    .addName("%name%", userName)
                    .addName("%names%",
                        transform(game.getLiveEuropeanPlayers(),
                                  alwaysTrue(), Player::getName,
                            Collectors.joining(", "))));
            } else if (present.isConnected() && !present.isAI()) {
                return ChangeSet.clientError((ServerPlayer)null, StringTemplate
                    .template("server.userNameInUse")
                    .addName("%name%", this.userName));
            }

            present.setConnection(conn);
            present.setConnected(true);
            if (present.isAI()) {
                present.setAI(false);
                freeColServer.sendToAll(new SetAIMessage(present, false),
                                        present);
            }

            // Ensure there is a current player.
            if (game.getCurrentPlayer() == null) game.setCurrentPlayer(present);

            // Ready to handle in-game messages.
            conn.setMessageHandler(freeColServer.getInGameInputHandler());
            freeColServer.getServer().addConnection(conn);
            freeColServer.updateMetaServer(false);
            return ChangeSet.simpleChange(present,
                new LoginMessage(this.userName, this.version, true,
                                 freeColServer.getSinglePlayer(),
                                 game.getCurrentPlayer() == present, game));

        case ENDING_GAME: default:
            break;
        }
        return null; // Bogus, do nothing
    }

    /**
     * Convert this LoginMessage to XML.
     *
     * @return The XML representation of this message.
     */
    @Override
    public Element toXMLElement() {
        Player player = (this.game == null || this.userName == null) ? null
            : this.game.getPlayerByName(this.userName);
        return new DOMMessage(TAG,
            USER_NAME_TAG, this.userName,
            VERSION_TAG, this.version,
            START_GAME_TAG, Boolean.toString(this.startGame),
            SINGLE_PLAYER_TAG, Boolean.toString(this.singlePlayer),
            CURRENT_PLAYER_TAG, Boolean.toString(this.currentPlayer))
            .add(this.game, player)
            .toXMLElement();
    }
}
