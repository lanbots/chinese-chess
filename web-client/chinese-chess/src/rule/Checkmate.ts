import Chess from "./Chess";
import ChessK from "./chess/ChessK";
import ChessPos from "./ChessPos";
import ChessHost from "./chess_host";
import Game from "./Game";

export default class Checkmate {
    private game: Game;
    private redK: Chess;
    private blackK: Chess;

    constructor(game: Game, viewChessHost: ChessHost) {
        this.game = game;

        let chessboard = this.game.getChessboard();
        let redK: Chess;
        let blackK: Chess;
        const topKPos = new ChessPos(9, 4);
        const bottomKPos = new ChessPos(0, 4);
        if (viewChessHost == ChessHost.RED) {
            redK = chessboard.chessAt(topKPos);
            blackK = chessboard.chessAt(bottomKPos);
        } else {
            redK = chessboard.chessAt(bottomKPos);
            blackK = chessboard.chessAt(topKPos);
        }
        this.redK = redK;
        this.blackK = blackK;
    }

    /**
     * 检查指定棋方此刻是否被将军
     * @param chessHost 
     */
    check(checkHost: ChessHost): boolean {
        let checkKPos = (checkHost == ChessHost.RED ? this.redK : this.blackK).getPos();
        
        // 有可能上一步就被吃了，检查在不在
        let chessK = this.game.getChessboard().chessAt(checkKPos);
        if (chessK == null || !chessK.is(ChessK)) {
            return false;
        }

        for (let chess of this.game.getChessboard().getChessList()
            .filter(chess => chess.getHost() != checkHost)) {
            // 排除将碰面
            if (chess.is(ChessK)) {
                continue;
            }
            // 是否可吃对方将军
            if (chess.canGoTo(checkKPos, this.game)) {
                return true;
            }
        }

        return false;
    }
}