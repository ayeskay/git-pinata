import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class BlackjackClient {
    private static long clockOffset = 0;
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            BlackjackService service = (BlackjackService) registry.lookup("BlackjackService");
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter your player name: ");
            String playerName = scanner.nextLine().trim();
            String joinResponse = service.joinGame(playerName);
            System.out.println(joinResponse);
            
            if (joinResponse.startsWith("ERROR")) return;

            synchronizeClock(service);

            while (true) {
                GameState gameState = service.getGameState();
                Player.Status myStatus = gameState.getPlayerStati().get(playerName);

                // FIX: This new structure separates "Action Mode" from "Observer Mode".
                if (myStatus == Player.Status.WAITING_FOR_BET || myStatus == Player.Status.PLAYING_TURN) {
                    // ACTION MODE: Draw the screen once and wait for blocking input.
                    // This completely prevents the "erased input" glitch.
                    clearConsole();
                    displayGameState(gameState, playerName);

                    if (myStatus == Player.Status.WAITING_FOR_BET) {
                        System.out.print(">>> Place your bet: ");
                        try {
                            double bet = Double.parseDouble(scanner.nextLine());
                            service.placeBet(playerName, bet);
                        } catch (NumberFormatException e) {
                            service.placeBet(playerName, 0); // Send an invalid bet to keep turn
                        }
                    } else { // PLAYING_TURN
                        System.out.print(">>> Your action (h)it or (s)tand: ");
                        String choice = scanner.nextLine();
                        if ("h".equalsIgnoreCase(choice)) {
                            service.hit(playerName);
                        } else if ("s".equalsIgnoreCase(choice)) {
                            service.stand(playerName);
                        }
                    }
                } else {
                    // OBSERVER MODE: Refresh the screen every second to see live updates.
                    clearConsole();
                    displayGameState(gameState, playerName);
                    Thread.sleep(1000); 
                }
            }
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private static void synchronizeClock(BlackjackService service) throws Exception {
        System.out.println("\nAttempting clock synchronization with server...");
        long startTime = System.currentTimeMillis();
        long serverTime = service.getServerTime();
        long endTime = System.currentTimeMillis();
        long rtt = endTime - startTime;
        long estimatedServerTime = serverTime + (rtt / 2);
        clockOffset = estimatedServerTime - endTime;
        System.out.println("Round Trip Time (RTT): " + rtt + " ms");
        System.out.println("Calculated Clock Offset: " + clockOffset + " ms");
        System.out.println("Clocks are now synchronized. Press Enter to continue...");
        new Scanner(System.in).nextLine();
    }

    private static long getSynchronizedTime() {
        return System.currentTimeMillis() + clockOffset;
    }

    private static void displayGameState(GameState state, String myName) {
        int dealerScore = getHandValue(state.getDealerHand());
        String localTimeStr = TIME_FORMATTER.format(Instant.ofEpochMilli(System.currentTimeMillis()));
        String syncTimeStr = TIME_FORMATTER.format(Instant.ofEpochMilli(getSynchronizedTime()));

        System.out.println("========================= BLACKJACK =========================");
        System.out.printf("Local Time: %s | Synchronized Time: %s\n", localTimeStr, syncTimeStr);
        System.out.println("-----------------------------------------------------------");
        
        System.out.printf("DEALER [Score: %d]\n", dealerScore);
        System.out.println("  Hand: " + state.getDealerHand());
        System.out.println("-----------------------------------------------------------");

        System.out.println("PLAYERS");
        List<String> playerNames = new ArrayList<>(state.getPlayerMoney().keySet());
        Collections.sort(playerNames);

        for (String name : playerNames) {
            String turnIndicator = name.equals(state.getCurrentPlayerTurn()) ? "==> " : "    ";
            String youIndicator = name.equals(myName) ? " (You)" : "";
            int playerScore = getHandValue(state.getPlayerHands().get(name));
            System.out.printf("%s%-15s | Money: $%-8.2f | Bet: $%-7.2f\n",
                turnIndicator, name + youIndicator,
                state.getPlayerMoney().get(name),
                state.getPlayerBets().get(name));
            System.out.printf("    Status: %-18s | Hand [Score: %d]: %s\n\n",
                state.getPlayerStati().get(name),
                playerScore,
                state.getPlayerHands().get(name));
        }
        
        String finalMessage = state.getGameMessage();
        long endTime = state.getTurnEndTime();
        String activePlayer = state.getCurrentPlayerTurn();
        
        // FIX: Display a static message for the active player, and a live countdown for observers.
        if (activePlayer != null && endTime > 0) {
            if (activePlayer.equals(myName)) {
                finalMessage += " (You have 30 seconds to act)";
            } else {
                long remainingMillis = endTime - getSynchronizedTime();
                long remainingSeconds = Math.max(0, remainingMillis / 1000);
                finalMessage += String.format(" (Time Left: %ds)", remainingSeconds);
            }
        }
        
        System.out.println("===========================================================");
        System.out.println("MESSAGE: " + finalMessage);
        System.out.println("===========================================================");
    }

    private static int getHandValue(List<Card> hand) {
        int value = 0;
        int aceCount = 0;
        if (hand == null) return 0;
        for (Card card : hand) { value += card.getValue(); if (card.getValue() == 11) { aceCount++; } }
        while (value > 21 && aceCount > 0) { value -= 10; aceCount--; }
        return value;
    }

    public final static void clearConsole() {
        for(int i = 0; i < 50; i++) System.out.println();
    }
}
