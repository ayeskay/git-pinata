import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BlackjackService extends Remote {
    long getServerTime() throws RemoteException;
    String joinGame(String playerName) throws RemoteException;
    GameState getGameState() throws RemoteException;
    void placeBet(String playerName, double amount) throws RemoteException;
    void hit(String playerName) throws RemoteException;
    void stand(String playerName) throws RemoteException;
}
