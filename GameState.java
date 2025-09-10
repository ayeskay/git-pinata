import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Card> dealerHand;
    private final Map<String, List<Card>> playerHands;
    private final Map<String, Double> playerMoney;
    private final Map<String, Double> playerBets;
    private final Map<String, Player.Status> playerStati;
    private final String gameMessage;
    private final String currentPlayerTurn;
    private final long turnEndTime;

    public GameState(List<Card> dealerHand, Map<String, List<Card>> playerHands,
                     Map<String, Double> playerMoney, Map<String, Double> playerBets,
                     Map<String, Player.Status> playerStati, String gameMessage, String currentPlayerTurn,
                     long turnEndTime) {
        this.dealerHand = dealerHand;
        this.playerHands = playerHands;
        this.playerMoney = playerMoney;
        this.playerBets = playerBets;
        this.playerStati = playerStati;
        this.gameMessage = gameMessage;
        this.currentPlayerTurn = currentPlayerTurn;
        this.turnEndTime = turnEndTime;
    }

    public List<Card> getDealerHand() { return dealerHand; }
    public Map<String, List<Card>> getPlayerHands() { return playerHands; }
    public Map<String, Double> getPlayerMoney() { return playerMoney; }
    public Map<String, Double> getPlayerBets() { return playerBets; }
    public String getGameMessage() { return gameMessage; }
    public Map<String, Player.Status> getPlayerStati() { return playerStati; }
    public String getCurrentPlayerTurn() { return currentPlayerTurn; }
    public long getTurnEndTime() { return turnEndTime; }
}
