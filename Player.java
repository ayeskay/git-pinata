import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a player in the Blackjack game. This object holds all the state
 * for a single player, including their name, money, hand, and current status.
 * It is Serializable so it can be sent between the client and server if needed,
 * although in this design, only its data is sent via the GameState object.
 */
public class Player implements Serializable {
    private static final long serialVersionUID = 1L;
    // Enum representing the possible states a player can be in during a round.
    public enum Status { WAITING, BETTING, PLAYING, STANDING, BUSTED }

    private final String name;
    private double money;
    private double currentBet;
    private List<Card> hand;
    private Status status;

    public Player(String name) {
        this.name = name;
        // As per requirements, each player starts with $1000.
        this.money = 1000.0;
        this.hand = new ArrayList<>();
        // A new player joining is immediately in the BETTING phase for the current/next round.
        this.status = Status.BETTING;
    }

    // Getters and setters
    public String getName() { return name; }
    public double getMoney() { return money; }
    public void setMoney(double money) { this.money = money; }
    public double getCurrentBet() { return currentBet; }
    public void placeBet(double bet) {
        if (bet > 0 && this.money >= bet) {
            this.money -= bet;
            this.currentBet = bet;
        }
    }
    public List<Card> getHand() { return hand; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    /**
     * Calculates the value of the player's current hand.
     * @return The integer value of the hand.
     */
    public int getHandValue() {
        int value = 0;
        int aceCount = 0;
        for (Card card : hand) {
            value += card.getValue();
            if (card.getValue() == 11) { // Card is an Ace
                aceCount++;
            }
        }
        while (value > 21 && aceCount > 0) {
            value -= 10; // Treat Ace as 1 instead of 11
            aceCount--;
        }
        return value;
    }

    /**
     * Resets the player's hand and bet for a new round.
     */
    public void clearHandAndBet() {
        this.hand.clear();
        this.currentBet = 0;
    }
}
