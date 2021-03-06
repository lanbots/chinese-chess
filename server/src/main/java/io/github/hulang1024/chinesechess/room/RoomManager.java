package io.github.hulang1024.chinesechess.room;

import io.github.hulang1024.chinesechess.chat.Channel;
import io.github.hulang1024.chinesechess.chat.ChannelManager;
import io.github.hulang1024.chinesechess.chat.ChannelType;
import io.github.hulang1024.chinesechess.play.GameState;
import io.github.hulang1024.chinesechess.room.ws.*;
import io.github.hulang1024.chinesechess.spectator.SpectatorManager;
import io.github.hulang1024.chinesechess.user.*;
import io.github.hulang1024.chinesechess.ws.ServerMessage;
import io.github.hulang1024.chinesechess.ws.WSMessageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RoomManager {
    //@Resource
    //private RedisTemplate<String, Object> redisTemplate;

    /** 房间id -> 房间 */
    private static Map<Long, Room> roomMap = new ConcurrentHashMap<>();

    /** 用户id -> 加入的房间 */
    private static Map<Long, Room> userJoinedRoomMap = new ConcurrentHashMap<>();

    @Autowired
    private ChannelManager channelManager;
    @Autowired
    private UserManager userManager;
    @Autowired
    private SpectatorManager spectatorManager;
    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private WSMessageService wsMessageService;

    public List<Room> searchRooms() {
        return roomMap.values().stream()
            .sorted((a, b) -> {
                int ret = a.getUserCount() - b.getUserCount();
                if (ret == 0) {
                    return b.getCreateAt().compareTo(a.getCreateAt());
                }
                return ret;
            })
            .collect(Collectors.toList());
    }

    public Collection<Room> getRooms() {
        return roomMap.values();
    }

    public Room getRoom(long id) {
        return roomMap.get(id);
    }

    public Room getJoinedRoom(User user) {
        return userJoinedRoomMap.get(user.getId());
    }

    public Room getJoinedRoom(long userId) {
        return userJoinedRoomMap.get(userId);
    }

    /**
     * 创建房间
     * @param room
     * @return 创建的房间
     */
    public Room createRoom(Room room) {
        User creator = UserUtils.get();

        if (getJoinedRoom(creator) != null) {
            return null;
        }

        Room createdRoom = new Room(channelManager, userManager);
        createdRoom.setId(nextRoomId());
        if (StringUtils.isNotBlank(room.getName())) {
            createdRoom.setName(room.getName());
        } else {
            createdRoom.setName(createdRoom.getId().toString());
        }
        createdRoom.setPassword(room.getPassword());
        createdRoom.setCreateBy(creator.getId());
        createdRoom.setCreateAt(LocalDateTime.now());
        createdRoom.setOwner(creator);

        // 为房间创建聊天频道
        Channel channel = new Channel();
        channel.setName(createdRoom.getId().toString());
        channel.setType(ChannelType.ROOM);
        channelManager.createChannel(channel);

        createdRoom.setChannel(channel);

        roomMap.put(createdRoom.getId(), createdRoom);

        userActivityService.broadcast(UserActivity.LOBBY, new LobbyRoomCreateServerMsg(createdRoom), creator);

        // 创建者默认进入房间
        JoinRoomParam roomJoinParam = new JoinRoomParam();
        roomJoinParam.setPassword(room.getPassword());
        joinRoom(createdRoom, creator, roomJoinParam);

        return createdRoom;
    }

    /**
     * 加入房间
     * @param roomId
     * @param userId
     * @return
     */
    public JoinRoomResult joinRoom(long roomId, long userId, JoinRoomParam joinRoomParam) {
        Room room = getRoom(roomId);
        if (room == null) {
            JoinRoomResult result = new JoinRoomResult();
            result.setCode(7);
            return result;
        }

        User user = userManager.getLoggedInUser(userId);

        return joinRoom(room, user, joinRoomParam);
    }

    public JoinRoomResult joinRoom(Room room, User user, JoinRoomParam joinRoomParam) {
        JoinRoomResult result = new JoinRoomResult();

        Room joinedRoom = getJoinedRoom(user);
        if (joinedRoom != null) {
            // 早就在其它房间
            GameState gameState = joinedRoom.getGame().getState();
            if (gameState == GameState.READY) {
                // 不在游戏中现在就退出
                partRoom(joinedRoom, user);
            } else {
                result.setCode(joinedRoom.getId().equals(room.getId()) ? 4 : 5);
                return result;
            }
        }

        Room spectatingRoom = spectatorManager.getSpectatingRoom(user);
        if (spectatingRoom != null) {
            // 在观看其它房间，退出
            spectatorManager.leaveRoom(user, spectatingRoom);
        }

        result.setRoom(room);

        if (room.isFull()) {
            result.setCode(3);
            return result;
        }

        // 验证密码，如果有密码
        if (room.isLocked()) {
            if (joinRoomParam == null || !room.getPassword().equals(joinRoomParam.getPassword())) {
                result.setCode(6);
                return result;
            }
        }

        userJoinedRoomMap.put(user.getId(), room);

        room.joinUser(user);

        RoomUserJoinServerMsg joinMsg = new RoomUserJoinServerMsg();
        joinMsg.setRoom(room);
        joinMsg.setUser(user);
        broadcast(room, joinMsg, user);

        userActivityService.broadcast(UserActivity.LOBBY, new LobbyRoomUpdateServerMsg(room), user);

        return result;
    }

    /**
     * 离开房间
     * @param roomId
     * @param userId
     * @return 0=成功，2=未加入该房间
     */
    public int partRoom(long roomId, long userId) {
        User user = userManager.getLoggedInUser(userId);
        Room room = getJoinedRoom(userId);
        // 未加入该房间
        if (room == null || roomId != room.getId()) {
            return 2;
        }

        return partRoom(room, user);
    }


    public int partRoom(Room room, User user) {
        room.partUser(user);
        userJoinedRoomMap.remove(user.getId());

        // 离开之后，房间内还有用户
        if (room.getUserCount() == 1) {
            User otherUser = room.getOneUser();
            // 但是该用户是离线状态
            if (!userManager.isOnline(otherUser)) {
                // 那么也将此离线用户移除房间
                partRoom(room, otherUser);
                // 导致解散房间
                if (room.getStatus() == RoomStatus.DISMISSED) {
                    return 0;
                }
            }
        }

        if (room.getUserCount() == 0) {
            // 如果全部离开了，解散房间
            dismissRoom(room);
        } else {
            userActivityService.broadcast(UserActivity.LOBBY, new LobbyRoomUpdateServerMsg(room));
        }

        // 如果有在线用户，发送离开消息
        RoomUserLeftServerMsg leftMsg = new RoomUserLeftServerMsg();
        leftMsg.setUid(user.getId());
        if (room.getOnlineUserCount() > 0) {
            broadcast(room, leftMsg);
        } else {
            // 观众一定要收到
            spectatorManager.broadcast(room, leftMsg);
        }

        return 0;
    }

    public boolean updateRoomInfo(Long roomId, RoomUpdateParam param) {
        Room room = getRoom(roomId);
        if (StringUtils.isNotBlank(param.getName())) {
            room.setName(param.getName());
        }
        if (StringUtils.isNotBlank(param.getPassword())) {
            room.setPassword(param.getPassword());
        }

        userActivityService.broadcast(UserActivity.LOBBY, new LobbyRoomUpdateServerMsg(room));
        return true;
    }

    public void dismissRoom(Room room) {
        room.setStatus(RoomStatus.DISMISSED);
        channelManager.removeChannel(room.getChannel());
        roomMap.remove(room.getId());
        userActivityService.broadcast(UserActivity.LOBBY, new LobbyRoomRemoveServerMsg(room));
    }

    public void broadcast(Room room, ServerMessage message) {
        broadcast(room, message, null);
    }

    public void broadcast(Room room, ServerMessage message, User exclude) {
        room.getUsers().forEach(user -> {
            if (user.equals(exclude)) {
                return;
            }
            wsMessageService.send(message, user);
        });
        spectatorManager.broadcast(room, message);
    }

    private static long nextRoomId = 1000;
    private long nextRoomId() {
        return ++nextRoomId;
        //return redisTemplate.opsForValue().increment("room:next_id", 1);
    }
}
