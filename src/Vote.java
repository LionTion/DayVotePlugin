import java.util.UUID;
import org.bukkit.entity.Player;

public class Vote {
    UUID[] allowedPlayers;
    boolean[] votesPlayers;
    double votePerc;
    int scheduledTask;

    public Vote(Player[] allowedPlayers, double votePerc, int scheduledTask) {
        this.votePerc = votePerc;
        this.scheduledTask = scheduledTask;
        this.allowedPlayers = new UUID[allowedPlayers.length];
        votesPlayers = new boolean[this.allowedPlayers.length];
        for (int i = 0; i < this.allowedPlayers.length; ++i) {
            this.allowedPlayers[i] = allowedPlayers[i].getUniqueId();
            votesPlayers[i] = false;
        }
    }

    public VoteStatus hasVoted(Player player) {
        return hasVoted(player.getUniqueId());
    }

    public VoteStatus hasVoted(UUID uuid) {
        for (int i = 0; i < this.allowedPlayers.length; ++i) {
            if (this.allowedPlayers[i].equals(uuid)) {
                return votesPlayers[i] ? VoteStatus.VOTED : VoteStatus.NOT_VOTED;
            }
        }
        return VoteStatus.NOT_ALLOWED;
    }

    public int[] vote(Player player) {
        return vote(player.getUniqueId());
    }

    public int[] vote(UUID uuid) {
        int votes = 0;
        for (int i = 0; i < this.allowedPlayers.length; ++i) {
            if (this.allowedPlayers[i].equals(uuid)) {
                votesPlayers[i] = true;
            }
            if (votesPlayers[i]) {
                ++votes;
            }
        }
        return new int[] { votes, this.votesPlayers.length };
    }
}
