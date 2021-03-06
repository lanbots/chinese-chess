import Chess from "../Chess";
import ChessPos from "../ChessPos";
import ChessHost from "../chess_host";
import Game from "../Game";

/**
 * 抽象的棋子
 */
export default abstract class AbstractChess implements Chess {
    /** 当前位置 */
    protected pos: ChessPos;
    /** 所属棋方 */
    protected host: ChessHost;

    constructor(pos: ChessPos, host: ChessHost) {
        this.pos = pos;
        this.host = host;
    }

    canGoTo(destPos: ChessPos, game: Game) {
        return false;
    }

    setPos(pos: ChessPos) {
        this.pos = pos;
    }

    getPos() {
        return this.pos;
    }

    getHost() {
        return this.host;
    }

    is(chessClass: Function) {
        return this instanceof chessClass;
    }
}
