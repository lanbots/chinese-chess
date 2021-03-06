import Overlay from "../../overlay/Overlay";

export default class ChessEatOverlay extends Overlay {
    constructor() {
        super(true);

        this.visible = false;
        this.setSize(200, 50);

        let text = new egret.TextField();
        text.size = 30;
        text.width = 200;
        text.height = 50;
        text.text = '吃!';
        text.stroke = 3;
        text.strokeColor = 0x000000;
        text.verticalAlign = egret.VerticalAlign.MIDDLE;
        text.textAlign = egret.HorizontalAlign.CENTER;
        this.addChild(text);
    }

    show() {
        this.parent.setChildIndex(this, 1000);
        this.visible = true;
        setTimeout(() => {
            this.visible = false;
        }, 1000);
    }
}