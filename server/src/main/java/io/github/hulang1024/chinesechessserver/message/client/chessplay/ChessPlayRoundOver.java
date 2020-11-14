package io.github.hulang1024.chinesechessserver.message.client.chessplay;

import io.github.hulang1024.chinesechessserver.message.ClientMessage;
import io.github.hulang1024.chinesechessserver.message.client.MessageType;
import lombok.Data;


@Data
@MessageType("chessplay.round_over")
public class ChessPlayRoundOver extends ClientMessage {
}