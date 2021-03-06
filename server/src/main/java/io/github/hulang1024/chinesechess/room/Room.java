package io.github.hulang1024.chinesechess.room;

import com.alibaba.fastjson.annotation.JSONField;
import io.github.hulang1024.chinesechess.chat.Channel;
import io.github.hulang1024.chinesechess.chat.ChannelManager;
import io.github.hulang1024.chinesechess.play.Game;
import io.github.hulang1024.chinesechess.play.GameState;
import io.github.hulang1024.chinesechess.play.rule.ChessHost;
import io.github.hulang1024.chinesechess.user.User;
import io.github.hulang1024.chinesechess.user.UserManager;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Data
public class Room {
    private Long id;

    private String name;

    @JSONField(serialize = false)
    private String password;

    private Long createBy;

    private Long channelId;

    private LocalDateTime createAt;

    @JSONField(serialize = false)
    private LocalDateTime updateAt;

    @JSONField(serialize = false)
    private Game game;

    private User owner;

    @JSONField(serialize = false)
    private Channel channel;

    private User redChessUser;

    private User blackChessUser;

    private boolean redReadied;

    private boolean blackReadied;

    @JSONField(serialize = false)
    private List<User> spectators = new ArrayList<>();

    @JSONField(serialize = false)
    private RoomStatus status = RoomStatus.OPEN;

    @JSONField(serialize = false)
    private ChannelManager channelManager;

    @JSONField(serialize = false)
    private UserManager userManager;

    /**
     * 房间内所有用户都离线则记录房间离线时间，否则为null
     */
    @JSONField(serialize = false)
    private LocalDateTime offlineAt;

    private int roundCount = 0;

    public Room(ChannelManager channelManager, UserManager userManager) {
        game = new Game(this);
        this.channelManager = channelManager;
        this.userManager = userManager;
    }

    public void joinUser(User user) {
        if (redChessUser == null) {
            redChessUser = user;
            redReadied = true;
        } else if (blackChessUser == null) {
            blackChessUser = user;
            blackReadied = true;
        }

        // 第二个加进来的默认未准备
        if (getUserCount() == 2) {
            updateUserReadyState(user, false);
        }

        channelManager.joinChannel(channel, user);

        status = getUserCount() < 2 ? RoomStatus.OPEN : RoomStatus.BEGINNING;
        game.setState(GameState.READY);
        offlineAt = null;
    }

    public void partUser(User user) {
        channelManager.leaveChannel(channel, user);
        status = RoomStatus.OPEN;

        if (getChessHost(user) == ChessHost.RED) {
            redChessUser = null;
            redReadied = false;
        }
        if (getChessHost(user) == ChessHost.BLACK) {
            blackChessUser = null;
            blackReadied = false;
        }

        game.setState(GameState.READY);
        roundCount = 0;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
        this.channelId = channel.getId();
    }

    public ChessHost getChessHost(User user) {
        if (user.equals(this.redChessUser)) {
            return ChessHost.RED;
        }
        if (user.equals(this.blackChessUser)) {
            return ChessHost.BLACK;
        }
        return null;
    }

    public boolean getUserReadied(User user) {
        if (user.equals(redChessUser)) {
            return redReadied;
        }
        if (user.equals(blackChessUser)) {
            return blackReadied;
        }
        return false;
    }

    public void updateUserReadyState(User user, boolean readied) {
        if (user.equals(redChessUser)) {
            redReadied = readied;
        }
        if (user.equals(blackChessUser)) {
            blackReadied = readied;
        }
    }

    @JSONField(serialize = false)
    public int getOnlineUserCount() {
        int count = 0;
        if (this.redChessUser != null && userManager.isOnline(redChessUser)) {
            count++;
        }
        if (this.blackChessUser != null && userManager.isOnline(blackChessUser)) {
            count++;
        }
        return count;
    }

    public boolean isFull() {
        return getUserCount() == 2;
    }

    public int getUserCount() {
        int count = 0;
        if (this.redChessUser != null) {
            count++;
        }
        if (this.blackChessUser != null) {
            count++;
        }
        return count;
    }

    public User getOneUser() {
        if (this.redChessUser != null) {
            return this.redChessUser;
        }
        if (this.blackChessUser != null) {
            return this.blackChessUser;
        }
        return null;
    }

    public User getOtherUser(User user) {
        if (user.equals(redChessUser)) {
            return blackChessUser;
        }
        if (user.equals(blackChessUser)) {
            return redChessUser;
        }
        return null;
    }

    public List<User> getUsers() {
        List<User> users = new ArrayList<>();
        if (redChessUser != null) {
            users.add(redChessUser);
        }
        if (blackChessUser != null) {
            users.add(blackChessUser);
        }
        return users;
    }

    public boolean getRedOnline() {
        if (this.redChessUser != null) {
            return userManager.isOnline(redChessUser);
        }
        return false;
    }

    public boolean getBlackOnline() {
        if (this.blackChessUser != null) {
            return userManager.isOnline(blackChessUser);
        }
        return false;
    }

    public int getSpectatorCount() {
        return spectators.size();
    }

    public boolean isLocked() {
        return password != null;
    }

    @JSONField(name = "status")
    public int getStatusCode() {
        return status.getCode();
    }

    @JSONField(name = "gameStatus")
    public int getGameStateCode() {
        return game.getState() == null ? 0 : game.getState().getCode();
    }
}
