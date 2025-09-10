import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Player implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Status { WAITING_FOR_BET, WAITING_FOR_TURN, PLAYING_TURN, STANDING, BUSTED }

    private final String name;
    private double money;
    private double currentBet;
    private List<Card> hand;
    private Status status;

    public Player(String name) {
        this.name = name;
        this.money = 1000.0;
        this.hand = new ArrayList<>();
        this.status = Status.WAITING_FOR_BET;
    }

    public String getName() { return name; }
    public double getMoney() { return money; }
    public void setMoney(double money) { this.money = money; }
    public double getCurrentBet() { return currentBet; }

    public void placeBet(double bet) {
        if (this.money >= bet && bet > 0) {
            this.money -= bet;
            this.currentBet = bet;
            this.status = Status.WAITING_FOR_TURN;
        }
    }

    public List<Card> getHand() { return hand; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getHandValue() {
        int value = 0;
        int aceCount = 0;
        for (Card card : hand) {
            value += card.getValue();
            if (card.getValue() == 11) { aceCount++; }
        }
        while (value > 21 && aceCount > 0) {
            value -= 10;
            aceCount--;
        }
        return value;
    }

    public void resetForNewRound() {
        this.hand.clear();
        this.currentBet = 0;
        this.status = Status.WAITING_FOR_BET;
    }
}
