import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The remote interface for the Blackjack game. This defines the methods
 * that a client can call on the server. It uses Java RMI (Remote Method Invocation).
 */
public interface BlackjackService extends Remote {
    // A player joins the game
    String joinGame(String playerName) throws RemoteException;

    // Get the entire state of the game
    GameState getGameState() throws RemoteException;

    // Player actions that modify the game state
    void placeBet(String playerName, double amount) throws RemoteException;
    void hit(String playerName) throws RemoteException;
    void stand(String playerName) throws RemoteException;
}
