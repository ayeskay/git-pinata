import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BlackjackServer extends UnicastRemoteObject implements BlackjackService {
    private enum GamePhase { BETTING, PLAYING, DEALER_TURN, PAYOUT }

    private List<Card> deck;
    private List<Card> dealerHand;
    private final Map<String, Player> players;
    private GamePhase currentPhase;
    private List<String> playerTurnOrder;
    private int currentPlayerIndex;
    private String message;

    public BlackjackServer() throws RemoteException {
        super();
        players = new ConcurrentHashMap<>();
        dealerHand = new ArrayList<>();
        playerTurnOrder = new ArrayList<>();
        startNewRound();
        System.out.println("Blackjack Server is ready.");
    }

    private void logThreadLifecycle(String event, String methodName, String playerName) {
        try {
            String clientHost = RemoteServer.getClientHost();
            System.out.printf("[%s] Client '%s' at %s -> %s() on Thread: %s%n",
                event, playerName, clientHost, methodName, Thread.currentThread().getName());
        } catch (Exception e) {
             System.out.printf("[%s] Local call -> %s() on Thread: %s%n",
                event, methodName, Thread.currentThread().getName());
        }
    }
    
    @Override
    public synchronized String joinGame(String playerName) throws RemoteException {
        logThreadLifecycle("Thread Birth", "joinGame", playerName);
        try {
            // ** THE FIX IS HERE **
            // If a player with this name already exists, we assume they are reconnecting.
            // This prevents a "ghost" session from stalling the game if the client restarts.
            if (players.containsKey(playerName)) {
                System.out.printf("--- Player '%s' has reconnected. ---\n", playerName);
                return "Welcome back, " + playerName + "!";
            }
            
            // If the player is new, create a new Player object for them.
            players.put(playerName, new Player(playerName));
            System.out.printf("--- Player '%s' has joined the game. ---\n", playerName);
            return "Welcome to Blackjack, " + playerName + "!";
        } finally {
            logThreadLifecycle("Thread Death", "joinGame", playerName);
        }
    }

    @Override
    public synchronized void placeBet(String playerName, double amount) throws RemoteException {
        logThreadLifecycle("Thread Birth", "placeBet", playerName);
        try {
            Player player = players.get(playerName);
            if (player == null || currentPhase != GamePhase.BETTING) return;
            if (player.getMoney() >= amount && amount > 0) {
                player.placeBet(amount);
                player.setStatus(Player.Status.WAITING);
            }
            
            if (allPlayersReady()) {
                // This is a critical transition
                dealInitialCards();
            }
        } finally {
            logThreadLifecycle("Thread Death", "placeBet", playerName);
        }
    }
    
    @Override
    public synchronized void hit(String playerName) throws RemoteException {
        logThreadLifecycle("Thread Birth", "hit", playerName);
        try {
            if (currentPhase != GamePhase.PLAYING || !isPlayerTurn(playerName)) return;
            
            Player player = players.get(playerName);
            player.getHand().add(deck.remove(0));
            
            if (player.getHandValue() > 21) {
                player.setStatus(Player.Status.BUSTED);
                message = playerName + " busted!";
                advanceTurn();
            }
        } finally {
            logThreadLifecycle("Thread Death", "hit", playerName);
        }
    }

    @Override
    public synchronized void stand(String playerName) throws RemoteException {
        logThreadLifecycle("Thread Birth", "stand", playerName);
        try {
            if (currentPhase != GamePhase.PLAYING || !isPlayerTurn(playerName)) return;
            
            players.get(playerName).setStatus(Player.Status.STANDING);
            message = playerName + " stands.";
            advanceTurn();
        } finally {
            logThreadLifecycle("Thread Death", "stand", playerName);
        }
    }
    
    private boolean allPlayersReady() {
        if (players.isEmpty()) {
            return false;
        }
        boolean isAnyoneStillBetting = players.values().stream()
                .anyMatch(p -> p.getStatus() == Player.Status.BETTING);
        if (isAnyoneStillBetting) {
            return false;
        }
        boolean hasAtLeastOneBet = players.values().stream()
                .anyMatch(p -> p.getCurrentBet() > 0);
        
        if(hasAtLeastOneBet) {
             System.out.println("[SERVER LOG] allPlayersReady is TRUE. Proceeding to deal.");
        }

        return hasAtLeastOneBet;
    }

    private void dealInitialCards() {
        System.out.println("[SERVER LOG] In dealInitialCards(). Setting phase to PLAYING.");
        currentPhase = GamePhase.PLAYING;
        playerTurnOrder = players.values().stream()
            .filter(p -> p.getCurrentBet() > 0)
            .map(Player::getName)
            .collect(Collectors.toList());
        
        if(playerTurnOrder.isEmpty()){
            message = "No bets placed. Starting new round...";
            startNewRound();
            return;
        }

        System.out.println("[SERVER LOG] Player turn order: " + playerTurnOrder);

        for (int i = 0; i < 2; i++) {
            for (String playerName : playerTurnOrder) {
                players.get(playerName).getHand().add(deck.remove(0));
            }
            dealerHand.add(deck.remove(0));
        }
        for (String playerName : playerTurnOrder) {
            players.get(playerName).setStatus(Player.Status.PLAYING);
        }
        currentPlayerIndex = 0;
        updateTurnAndMessage();
    }
    
    private void updateTurnAndMessage() {
        if (currentPlayerIndex >= playerTurnOrder.size()) {
            System.out.println("[SERVER LOG] All players have played. Moving to DEALER_TURN.");
            currentPhase = GamePhase.DEALER_TURN;
            dealerPlays();
        } else {
            String currentName = playerTurnOrder.get(currentPlayerIndex);
            message = "It's " + currentName + "'s turn. Hit or Stand?";
            System.out.println("[SERVER LOG] Turn updated. It is now " + currentName + "'s turn.");
        }
    }

    private void advanceTurn() {
        currentPlayerIndex++;
        System.out.println("[SERVER LOG] Advancing turn. New player index: " + currentPlayerIndex);
        updateTurnAndMessage();
    }

    @Override
    public synchronized GameState getGameState() throws RemoteException {
        Map<String, List<Card>> playerHands = new ConcurrentHashMap<>();
        Map<String, Double> playerMoney = new ConcurrentHashMap<>();
        Map<String, Double> playerBets = new ConcurrentHashMap<>();
        Map<String, Player.Status> playerStati = new ConcurrentHashMap<>();
        
        for(Player p : players.values()){
            playerHands.put(p.getName(), new ArrayList<>(p.getHand()));
            playerMoney.put(p.getName(), p.getMoney());
            playerBets.put(p.getName(), p.getCurrentBet());
            playerStati.put(p.getName(), p.getStatus());
        }
        
        List<Card> visibleDealerHand = new ArrayList<>();
        if (currentPhase == GamePhase.PLAYING && !dealerHand.isEmpty()) {
            visibleDealerHand.add(dealerHand.get(0));
            visibleDealerHand.add(new Card("?", "Hidden", 0));
        } else {
            visibleDealerHand.addAll(dealerHand);
        }
        
        String currentTurnPlayer = (currentPhase == GamePhase.PLAYING && currentPlayerIndex < playerTurnOrder.size())
            ? playerTurnOrder.get(currentPlayerIndex) : null;
            
        return new GameState(visibleDealerHand, playerHands, playerMoney, playerBets, playerStati, message, currentTurnPlayer);
    }
    
    private void startNewRound() {
        currentPhase = GamePhase.BETTING;
        message = "New round started. Please place your bets!";
        dealerHand.clear();
        playerTurnOrder.clear();
        currentPlayerIndex = -1;
        for(Player player : players.values()){
            player.clearHandAndBet();
            player.setStatus(Player.Status.BETTING);
        }
        deck = new ArrayList<>();
        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King", "Ace"};
        int[] values = {2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 10, 10, 11};
        for (String suit : suits) {
            for (int i = 0; i < ranks.length; i++) {
                deck.add(new Card(suit, ranks[i], values[i]));
            }
        }
        Collections.shuffle(deck);
        System.out.println("--- New Round Started ---");
    }
    
    private void dealerPlays() {
        message = "Dealer is playing...";
        while (getHandValue(dealerHand) < 17) {
            dealerHand.add(deck.remove(0));
        }
        resolveBets();
    }
    private void resolveBets() {
        currentPhase = GamePhase.PAYOUT;
        int dealerValue = getHandValue(dealerHand);
        StringBuilder resultMessage = new StringBuilder("Dealer has " + dealerValue + ". ");
        for (String playerName : playerTurnOrder) {
            Player player = players.get(playerName);
            int playerValue = player.getHandValue();
            if (player.getStatus() == Player.Status.BUSTED) {
                resultMessage.append(String.format("%s busted. ", playerName));
            } else if (dealerValue > 21 || playerValue > dealerValue) {
                player.setMoney(player.getMoney() + player.getCurrentBet() * 2);
                resultMessage.append(String.format("%s wins! ", playerName));
            } else if (playerValue == dealerValue) {
                player.setMoney(player.getMoney() + player.getCurrentBet());
                resultMessage.append(String.format("%s pushes. ", playerName));
            } else {
                 resultMessage.append(String.format("%s loses. ", playerName));
            }
        }
        message = resultMessage.toString();
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                synchronized(BlackjackServer.this) {
                    startNewRound();
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }).start();
    }
    private boolean isPlayerTurn(String playerName) {
        if (playerTurnOrder.isEmpty() || currentPlayerIndex >= playerTurnOrder.size()) return false;
        return playerTurnOrder.get(currentPlayerIndex).equals(playerName);
    }
    
    private int getHandValue(List<Card> hand) {
        int value = 0;
        int aceCount = 0;
        for (Card card : hand) {
            value += card.getValue();
            if (card.getValue() == 11) aceCount++;
        }
        while (value > 21 && aceCount > 0) {
            value -= 10;
            aceCount--;
        }
        return value;
    }

    public static void main(String[] args) {
        try {
            BlackjackServer server = new BlackjackServer();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("BlackjackService", server);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}