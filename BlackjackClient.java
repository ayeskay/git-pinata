import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class BlackjackClient {
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            BlackjackService service = (BlackjackService) registry.lookup("BlackjackService");
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter your player name: ");
            String playerName = scanner.nextLine();
            String joinResponse = service.joinGame(playerName);
            System.out.println(joinResponse);
            
            if (joinResponse.startsWith("ERROR")) {
                return;
            }

            // The main game loop.
            while (true) {
                GameState gameState = service.getGameState();
                Player.Status myStatus = gameState.getPlayerStati().get(playerName);
                String currentTurnPlayer = gameState.getCurrentPlayerTurn();

                // ### ACTION LOGIC ###
                // If it's time for this client's player to act...
                if (myStatus == Player.Status.BETTING) {
                    clearConsole();
                    displayGameState(gameState, playerName);
                    System.out.print(">>> Place your bet: ");
                    try {
                        double bet = Double.parseDouble(scanner.nextLine());
                        service.placeBet(playerName, bet);
                        // After betting, loop will continue in observer mode.
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid bet amount. Pausing for 2 seconds...");
                        Thread.sleep(2000);
                    }
                } else if (playerName != null && playerName.equals(currentTurnPlayer)) {
                    // It's our turn to hit or stand. Enter a dedicated handler.
                    handlePlayerTurn(service, scanner, playerName);
                } else {
                    // ### OBSERVER LOGIC ###
                    // If we are just waiting, display the state once, then wait silently
                    // for a change to avoid flashing the screen repeatedly.
                    clearConsole();
                    displayGameState(gameState, playerName);

                    // Poll silently until the state changes
                    while (true) {
                        Thread.sleep(1000); // Check for an update every second
                        GameState newState = service.getGameState();
                        // If the turn moves or the round ends, the message will change.
                        // This is our cue to break and re-render the screen.
                        if (!newState.getGameMessage().equals(gameState.getGameMessage())) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * A dedicated loop to handle a player's turn (hitting or standing).
     * This loop continues until the player stands or busts.
     */
    private static void handlePlayerTurn(BlackjackService service, Scanner scanner, String playerName) throws Exception {
        while (true) {
            GameState turnState = service.getGameState();
            clearConsole();
            displayGameState(turnState, playerName);

            // Check if our turn was ended by the server (e.g., a Blackjack)
            if (!playerName.equals(turnState.getCurrentPlayerTurn())) {
                break;
            }

            System.out.print(">>> Your action (h)it or (s)tand: ");
            String choice = scanner.nextLine();

            if ("h".equalsIgnoreCase(choice)) {
                service.hit(playerName);
                // Check if the hit resulted in a bust
                GameState newState = service.getGameState();
                if (newState.getPlayerStati().get(playerName) == Player.Status.BUSTED) {
                    clearConsole();
                    displayGameState(newState, playerName);
                    System.out.println(">>> You BUSTED! Waiting for round to end...");
                    Thread.sleep(2500);
                    break; // Exit turn loop
                }
            } else if ("s".equalsIgnoreCase(choice)) {
                service.stand(playerName);
                break; // Exit turn loop
            }
        }
    }

    /**
     * Calculates the value of a hand, properly handling the value of Aces.
     */
    private static int getHandValue(List<Card> hand) {
        int value = 0;
        int aceCount = 0;
        if (hand == null) return 0;
        for (Card card : hand) {
            value += card.getValue();
            if (card.getValue() == 11) { // Card is an Ace
                aceCount++;
            }
        }
        while (value > 21 && aceCount > 0) {
            value -= 10;
            aceCount--;
        }
        return value;
    }

    /**
     * Renders the entire game state to the console.
     */
    private static void displayGameState(GameState state, String myName) {
        int dealerScore = getHandValue(state.getDealerHand());

        System.out.println("========================= BLACKJACK =========================");
        
        System.out.printf("DEALER [Score: %s]\n", 
            (state.getDealerHand().stream().anyMatch(c -> c.getValue() == 0)) ? "?" : dealerScore);
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
                turnIndicator,
                name + youIndicator,
                state.getPlayerMoney().get(name),
                state.getPlayerBets().get(name)
            );
            System.out.printf("    Status: %-12s | Hand [Score: %d]: %s\n\n",
                state.getPlayerStati().get(name),
                playerScore,
                state.getPlayerHands().get(name)
            );
        }
        
        System.out.println("===========================================================");
        System.out.println("MESSAGE: " + state.getGameMessage());
        System.out.println("===========================================================");
    }

    /**
     * A simple utility to clear the console screen for a cleaner display.
     */
    public final static void clearConsole() {
        for(int i = 0; i < 50; i++) System.out.println();
    }
}