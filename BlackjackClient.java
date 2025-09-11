import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class BlackjackClient {
    private static long clockOffset = 0;
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static int myPriority = -1;
    private static List<String> playerRing = new ArrayList<>();
    private static String previousDealerName = null;
    private static boolean justElectedAsDealer = false;

    public static void main(String[] args) {
        String playerName = null;
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            BlackjackService service = (BlackjackService) registry.lookup("BlackjackService");
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter your player name: ");
            playerName = scanner.nextLine().trim();
            String joinResponse = service.joinGame(playerName);
            System.out.println(joinResponse);

            if (joinResponse.startsWith("ERROR")) return;

            startHeartbeatThread(service, playerName);
            synchronizeClock(service);

            while (true) {
                GameState gameState = service.getGameState();

                if (myPriority == -1 && gameState.getPlayerPriorities().containsKey(playerName)) {
                    myPriority = gameState.getPlayerPriorities().get(playerName);
                }
                playerRing = gameState.getPlayerPriorities().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                
                String currentDealerName = gameState.getCurrentDealerName();
                if (currentDealerName != null && !currentDealerName.equals(previousDealerName) && currentDealerName.equals(playerName)) {
                    justElectedAsDealer = true;
                }
                previousDealerName = currentDealerName;

                ElectionMessage electionMsg = gameState.getElectionMessage();
                if (electionMsg != null) {
                    handleElection(service, electionMsg, playerName, gameState);
                    Thread.sleep(1000);
                    continue;
                }

                if (gameState.getCurrentDealerName() == null) {
                    clearConsole();
                    displayGameState(gameState, playerName);
                    System.out.println(">>> Waiting for the server to initiate a dealer election...");
                    Thread.sleep(1000);
                    continue;
                }
                
                clearConsole();
                displayGameState(gameState, playerName);

                if (justElectedAsDealer) {
                    System.out.println("\n>>> YOU HAVE BEEN ELECTED THE NEW DEALER <<<");
                    justElectedAsDealer = false;
                    Thread.sleep(3000);
                    continue;
                }

                Player.Status myStatus = gameState.getPlayerStati().get(playerName);

                if (myStatus == Player.Status.WAITING_FOR_BET || myStatus == Player.Status.PLAYING_TURN) {
                    if (myStatus == Player.Status.WAITING_FOR_BET) {
                        System.out.print(">>> Place your bet: ");
                        try {
                            double bet = Double.parseDouble(scanner.nextLine());
                            service.placeBet(playerName, bet);
                        } catch (NumberFormatException e) {
                            service.placeBet(playerName, 0);
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
                    Thread.sleep(1000);
                }
            }
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            System.err.println("Disconnected from server.");
        }
    }
    
    private static void startHeartbeatThread(BlackjackService service, String playerName) {
        Thread heartbeatThread = new Thread(() -> {
            while (true) {
                try {
                    service.heartbeat(playerName);
                    Thread.sleep(5000); // Send heartbeat every 5 seconds
                } catch (RemoteException e) {
                    System.err.println("Connection to server lost.");
                    System.exit(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private static void handleElection(BlackjackService service, ElectionMessage msg, String myName, GameState gameState) throws Exception {
        clearConsole();
        System.out.println("==================== ELECTION IN PROGRESS ====================");
        System.out.println("Election message received.");
        System.out.println("  - Originator: " + msg.getOriginatorName());
        System.out.println("  - Current Candidate Priority: " + msg.getCandidateId());
        System.out.println("  - Message intended for: " + msg.getNextRecipientName());

        if (!myName.equals(msg.getNextRecipientName())) {
            System.out.println("\nWaiting for " + msg.getNextRecipientName() + " to process...");
            return;
        }

        System.out.println("\nThis message is for me. My priority is " + myPriority);

        if (msg.getOriginatorName().equals(myName)) {
            int winnerPriority = msg.getCandidateId();
            String winnerName = gameState.getPlayerPriorities().entrySet().stream()
                .filter(entry -> entry.getValue() == winnerPriority)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

            System.out.println("Election message completed the ring. The highest priority is " + winnerPriority);
            if (winnerName != null) {
                System.out.println(">>> Announcing " + winnerName + " as the new dealer!");
                service.claimDealership(winnerName);
            }
        } else {
            int newCandidateId = (myPriority > msg.getCandidateId()) ? myPriority : msg.getCandidateId();
            if (newCandidateId == myPriority) {
                 System.out.println("My priority is higher. Updating candidate to " + myPriority);
            } else {
                System.out.println("My priority is not higher. Forwarding message as is.");
            }
            
            String nextPlayer = getNextPlayerInRing(myName);
            System.out.println("Forwarding message to next player: " + nextPlayer);
            ElectionMessage forwardedMsg = new ElectionMessage(newCandidateId, msg.getOriginatorName(), nextPlayer);
            service.passElectionMessage(forwardedMsg);
        }
    }

    private static String getNextPlayerInRing(String currentPlayerName) {
        int currentIndex = playerRing.indexOf(currentPlayerName);
        if (currentIndex == -1 || playerRing.isEmpty()) {
            return currentPlayerName;
        }
        int nextIndex = (currentIndex + 1) % playerRing.size();
        return playerRing.get(nextIndex);
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

        String dealerName = state.getCurrentDealerName();
        String dealerDisplay = (dealerName != null) ? "DEALER (" + dealerName + ")" : "DEALER";

        System.out.println("========================= BLACKJACK =========================");
        System.out.printf("Local Time: %s | Synchronized Time: %s\n", localTimeStr, syncTimeStr);
        System.out.println("-----------------------------------------------------------");

        System.out.printf("%s [Score: %d]\n", dealerDisplay, dealerScore);
        System.out.println("  Hand: " + state.getDealerHand());
        System.out.println("-----------------------------------------------------------");

        System.out.println("PLAYERS");
        List<String> playerNames = new ArrayList<>(state.getPlayerMoney().keySet());
        Collections.sort(playerNames);

        for (String name : playerNames) {
            String turnIndicator = name.equals(state.getCurrentPlayerTurn()) ? "==> " : "    ";
            String youIndicator = name.equals(myName) ? " (You)" : "";
            String dealerIndicator = name.equals(dealerName) ? " (Dealer)" : "";
            int playerScore = getHandValue(state.getPlayerHands().get(name));
            System.out.printf("%s%-15s | Money: $%-8.2f | Bet: $%-7.2f\n",
                turnIndicator, name + youIndicator + dealerIndicator,
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
