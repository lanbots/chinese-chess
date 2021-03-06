package io.github.hulang1024.chinesechess.spectator;

import io.github.hulang1024.chinesechess.chat.ChannelManager;
import io.github.hulang1024.chinesechess.chat.InfoMessage;
import io.github.hulang1024.chinesechess.play.GameState;
import io.github.hulang1024.chinesechess.room.Room;
import io.github.hulang1024.chinesechess.room.RoomManager;
import io.github.hulang1024.chinesechess.spectator.ws.SpectatorJoinServerMsg;
import io.github.hulang1024.chinesechess.spectator.ws.SpectatorLeftServerMsg;
import io.github.hulang1024.chinesechess.user.User;
import io.github.hulang1024.chinesechess.user.UserManager;
import io.github.hulang1024.chinesechess.ws.ServerMessage;
import io.github.hulang1024.chinesechess.ws.WSMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SpectatorManager {
    private static Map<Long, Room> spectatorRoomMap = new ConcurrentHashMap<>();

    @Autowired
    private UserManager userManager;
    @Autowired
    private RoomManager roomManager;
    @Autowired
    private ChannelManager channelManager;
    @Autowired
    private WSMessageService wsMessageService;

    public Room getSpectatingRoom(User user) {
        return spectatorRoomMap.get(user.getId());
    }

    public List<User> getSpectators(Room room) {
        return room.getSpectators();
    }


    public SpectateResponse spectateRoom(long roomId, long spectatorId) {
        return spectate(null, roomId, spectatorId);
    }

    public SpectateResponse spectateUser(long targetUserId, long spectatorId) {
        User targetUser = userManager.getLoggedInUser(targetUserId);
        if (targetUser == null) {
            SpectateResponse response = new SpectateResponse();
            response.setCode(2);
            return response;
        }
        return spectate(targetUser, null, spectatorId);
    }

    private SpectateResponse spectate(User targetUserId, Long roomId, long spectatorId) {
        SpectateResponse response = new SpectateResponse();

        User spectator = userManager.getLoggedInUser(spectatorId);
        if (spectator == null) {
            response.setCode(1);
            return response;
        }

        // 房间不存在或者目标用户是否不在任何游戏房间中
        Room room = targetUserId != null ? roomManager.getJoinedRoom(targetUserId) : roomManager.getRoom(roomId);
        if (room == null) {
            response.setCode(3);
            return response;
        }

        Room joinedRoom = roomManager.getJoinedRoom(spectator);
        if (joinedRoom != null) {
            // 早已经在其它房间
            GameState gameState = joinedRoom.getGame().getState();
            if (gameState == GameState.READY) {
                // 不在游戏中现在就退出
                roomManager.partRoom(joinedRoom, spectator);
            } else {
                response.setCode(5);
                return response;
            }
        }

        // 已观看其它房间，退出
        Room prevSpectatingRoom = getSpectatingRoom(spectator);
        if (prevSpectatingRoom != null) {
            leaveRoom(spectator, prevSpectatingRoom);
        }

        room.getSpectators().add(spectator);
        channelManager.joinChannel(room.getChannel(), spectator);

        spectatorRoomMap.put(spectator.getId(), room);

        response.setStates(room.getGame().buildGamePlayStatesResponse());
        if (targetUserId != null) {
            response.setTargetUserId(targetUserId.getId());
        }

        // 发送给房间玩家用户观众信息
        SpectatorJoinServerMsg joinMsg = new SpectatorJoinServerMsg();
        joinMsg.setUser(spectator);
        joinMsg.setSpectatorCount(room.getSpectators().size());
        roomManager.broadcast(room, joinMsg, spectator);

        // 玩家可能都离线/掉线
        if (room.getOnlineUserCount() > 0) {
            channelManager.broadcast(room.getChannel(),
                new InfoMessage(spectator.getNickname() + " 加入观看"), spectator);
        }

        return response;
    }



    public void leaveRoom(long roomId, long userId) {
        User user = userManager.getLoggedInUser(userId);
        Room room = roomManager.getRoom(roomId);
        leaveRoom(user, room);
    }

    public void leaveRoom(User spectator, Room room) {
        room.getSpectators().remove(spectator);
        room.getChannel().removeUser(spectator);
        spectatorRoomMap.remove(spectator.getId());

        SpectatorLeftServerMsg leftMsg = new SpectatorLeftServerMsg();
        leftMsg.setUid(spectator.getId());
        leftMsg.setSpectatorCount(room.getSpectators().size());
        roomManager.broadcast(room, leftMsg);

        channelManager.broadcast(room.getChannel(),
            new InfoMessage(spectator.getNickname() + " 离开观看"));
    }

    public void broadcast(Room room, ServerMessage message) {
        room.getSpectators().forEach(user -> {
            wsMessageService.send(message, user);
        });
    }
}
