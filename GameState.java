import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A serializable "data transfer object" (DTO) that encapsulates a complete
 * snapshot of the game at a single point in time. The server creates an instance
 * of this class and sends it to clients.
 *
 * This design is crucial for ensuring that all players see the same information,
 * including each other's hands and balances.
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    // Public game information visible to all players.
    private final List<Card> dealerHand;
    private final Map<String, List<Card>> playerHands;
    private final Map<String, Double> playerMoney;
    private final Map<String, Double> playerBets;
    private final Map<String, Player.Status> playerStati;
    private final String gameMessage;
    // Identifies whose turn it is, enabling the turn-based system.
    private final String currentPlayerTurn;

    public GameState(List<Card> dealerHand, Map<String, List<Card>> playerHands,
                     Map<String, Double> playerMoney, Map<String, Double> playerBets,
                     Map<String, Player.Status> playerStati, String gameMessage, String currentPlayerTurn) {
        this.dealerHand = dealerHand;
        this.playerHands = playerHands;
        this.playerMoney = playerMoney;
        this.playerBets = playerBets;
        this.playerStati = playerStati;
        this.gameMessage = gameMessage;
        this.currentPlayerTurn = currentPlayerTurn;
    }

    // Standard getters for all fields.
    public List<Card> getDealerHand() { return dealerHand; }
    public Map<String, List<Card>> getPlayerHands() { return playerHands; }
    public Map<String, Double> getPlayerMoney() { return playerMoney; }
    public Map<String, Double> getPlayerBets() { return playerBets; }
    public Map<String, Player.Status> getPlayerStati() { return playerStati; }
    public String getGameMessage() { return gameMessage; }
    public String getCurrentPlayerTurn() { return currentPlayerTurn; }
}
