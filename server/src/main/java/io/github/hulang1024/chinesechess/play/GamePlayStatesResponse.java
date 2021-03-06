package io.github.hulang1024.chinesechess.play;

import io.github.hulang1024.chinesechess.room.Room;
import lombok.Data;

import java.util.List;
import java.util.Stack;

@Data
public class GamePlayStatesResponse {
    private Room room;
    private List<Chess> chesses;
    private Integer activeChessHost;
    private Stack<ChessAction> actionStack;
    
    @Data
    public static class Chess {
        private int row;
        private int col;
        private int host;
        private char type;
    }
}
