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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BlackjackServer extends UnicastRemoteObject implements BlackjackService {
    private enum GamePhase { BETTING, PLAYING, DEALER_TURN, PAYOUT }

    private List<Card> deck;
    private List<Card> dealerHand = new ArrayList<>();
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private GamePhase currentPhase = GamePhase.BETTING;
    private List<String> playerTurnOrder = new ArrayList<>();
    private int currentPlayerIndex = -1;
    private String message = "New round started. Please place your bets!";

    private final ScheduledExecutorService turnTimerExecutor = Executors.newSingleThreadScheduledExecutor();
    private Future<?> turnTimerFuture;
    private long turnEndTime = 0;
    private int turnId = 0;

    protected BlackjackServer() throws RemoteException {
        super();
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
    public long getServerTime() throws RemoteException {
        return System.currentTimeMillis();
    }

    @Override
    public synchronized String joinGame(String playerName) throws RemoteException {
        logThreadLifecycle("Thread Birth", "joinGame", playerName);
        try {
            if (players.containsKey(playerName)) {
                return "ERROR: Player name is already taken.";
            }
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
            player.placeBet(amount);
            if (areAllBetsIn()) {
                dealInitialCards();
            }
        } finally {
            logThreadLifecycle("Thread Death", "placeBet", playerName);
        }
    }

    private boolean areAllBetsIn() {
        if (players.isEmpty()) return false;
        return players.values().stream().noneMatch(p -> p.getStatus() == Player.Status.WAITING_FOR_BET);
    }
    
    @Override
    public synchronized void hit(String playerName) throws RemoteException {
        logThreadLifecycle("Thread Birth", "hit", playerName);
        try {
            if (!isPlayerTurn(playerName)) return;
            cancelTurnTimer();
            this.turnId++;
            Player player = players.get(playerName);
            if(deck.isEmpty()) createNewDeck();
            player.getHand().add(deck.remove(0));
            if (player.getHandValue() > 21) {
                player.setStatus(Player.Status.BUSTED);
                message = playerName + " busted!";
                advanceTurn();
            } else {
                startTurnTimerFor(playerName);
            }
        } finally {
            logThreadLifecycle("Thread Death", "hit", playerName);
        }
    }

    @Override
    public synchronized void stand(String playerName) throws RemoteException {
        logThreadLifecycle("Thread Birth", "stand", playerName);
        try {
            if (!isPlayerTurn(playerName)) return;
            cancelTurnTimer();
            this.turnId++;
            Player player = players.get(playerName);
            player.setStatus(Player.Status.STANDING);
            message = playerName + " stands.";
            advanceTurn();
        } finally {
            logThreadLifecycle("Thread Death", "stand", playerName);
        }
    }
    
    private void dealInitialCards() {
        currentPhase = GamePhase.PLAYING;
        playerTurnOrder = players.values().stream()
            .filter(p -> p.getCurrentBet() > 0)
            .map(Player::getName)
            .collect(Collectors.toList());
        
        if(playerTurnOrder.isEmpty()) { startNewRound(); return; }

        for (int i = 0; i < 2; i++) {
            for (String name : playerTurnOrder) {
                if (deck.isEmpty()) createNewDeck();
                players.get(name).getHand().add(deck.remove(0));
            }
            if (deck.isEmpty()) createNewDeck();
            dealerHand.add(deck.remove(0));
        }

        currentPlayerIndex = 0;
        String firstPlayer = playerTurnOrder.get(currentPlayerIndex);
        players.get(firstPlayer).setStatus(Player.Status.PLAYING_TURN);
        message = "It's " + firstPlayer + "'s turn.";
        startTurnTimerFor(firstPlayer);
    }
    
    private void advanceTurn() {
        if (currentPlayerIndex < playerTurnOrder.size()) {
            String currentPlayerName = playerTurnOrder.get(currentPlayerIndex);
            Player currentPlayer = players.get(currentPlayerName);
            if (currentPlayer != null && currentPlayer.getStatus() == Player.Status.PLAYING_TURN) {
                currentPlayer.setStatus(Player.Status.WAITING_FOR_TURN);
            }
        }
        
        currentPlayerIndex++;

        if (currentPlayerIndex >= playerTurnOrder.size()) {
            turnEndTime = 0;
            currentPhase = GamePhase.DEALER_TURN;
            dealerPlays();
        } else {
            String nextPlayerName = playerTurnOrder.get(currentPlayerIndex);
            players.get(nextPlayerName).setStatus(Player.Status.PLAYING_TURN);
            message = "It's " + nextPlayerName + "'s turn.";
            startTurnTimerFor(nextPlayerName);
        }
    }

    private void startTurnTimerFor(String playerName) {
        cancelTurnTimer();
        this.turnId++;
        final int currentTurnId = this.turnId;
        turnEndTime = System.currentTimeMillis() + 30000;
        Runnable forceStandTask = () -> {
            synchronized(BlackjackServer.this) {
                if (isPlayerTurn(playerName) && BlackjackServer.this.turnId == currentTurnId) {
                    System.out.println("\n[SERVER] Player '" + playerName + "' ran out of time. Forcing stand.");
                    try { stand(playerName); } catch (RemoteException e) { e.printStackTrace(); }
                }
            }
        };
        turnTimerFuture = turnTimerExecutor.schedule(forceStandTask, 30, TimeUnit.SECONDS);
    }
    
    private void cancelTurnTimer() {
        turnEndTime = 0;
        if (turnTimerFuture != null && !turnTimerFuture.isDone()) {
            turnTimerFuture.cancel(false);
        }
    }

    private void dealerPlays() {
        message = "Dealer is playing...";
        while (getHandValue(dealerHand) < 17) {
            if (deck.isEmpty()) createNewDeck();
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
        
        // FIX: Wait for 8 seconds on the results screen before starting a new round.
        new Thread(() -> {
            try {
                Thread.sleep(8000);
                synchronized(BlackjackServer.this) { startNewRound(); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }).start();
    }
    
    private void startNewRound() {
        message = "New round started. Please place your bets!";
        currentPhase = GamePhase.BETTING;
        dealerHand.clear();
        playerTurnOrder.clear();
        currentPlayerIndex = -1;
        turnEndTime = 0;
        
        for(Player player : players.values()){ player.resetForNewRound(); }
        
        if (deck == null || deck.size() < 20) { createNewDeck(); }
    }
    
    private void createNewDeck() {
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
        
        List<Card> visibleDealerHand = new ArrayList<>(dealerHand);
        if (currentPhase == GamePhase.PLAYING && dealerHand.size() > 1) {
            visibleDealerHand.clear();
            visibleDealerHand.add(dealerHand.get(0));
            visibleDealerHand.add(new Card("?", "Hidden", 0));
        }
        
        String currentTurnPlayer = (currentPhase == GamePhase.PLAYING && currentPlayerIndex < playerTurnOrder.size())
            ? playerTurnOrder.get(currentPlayerIndex) : null;
            
        return new GameState(visibleDealerHand, playerHands, playerMoney, playerBets, playerStati, message, currentTurnPlayer, turnEndTime);
    }

    private boolean isPlayerTurn(String playerName) {
        if (currentPhase != GamePhase.PLAYING || currentPlayerIndex >= playerTurnOrder.size() || playerName == null) return false;
        return playerName.equals(playerTurnOrder.get(currentPlayerIndex));
    }

    private int getHandValue(List<Card> hand) {
        int value = 0;
        int aceCount = 0;
        for (Card card : hand) { value += card.getValue(); if (card.getValue() == 11) aceCount++; }
        while (value > 21 && aceCount > 0) { value -= 10; aceCount--; }
        return value;
    }

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("BlackjackService", new BlackjackServer());
        } catch (Exception e) { e.printStackTrace(); }
    }
}
