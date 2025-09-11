import java.io.Serializable;

public class ElectionMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int candidateId; // Highest priority ID seen so far
    private final String originatorName; // Who started the election
    private final String nextRecipientName; // Who should process this message next

    public ElectionMessage(int candidateId, String originatorName, String nextRecipientName) {
        this.candidateId = candidateId;
        this.originatorName = originatorName;
        this.nextRecipientName = nextRecipientName;
    }

    public int getCandidateId() {
        return candidateId;
    }

    public String getOriginatorName() {
        return originatorName;
    }

    public String getNextRecipientName() {
        return nextRecipientName;
    }
}
