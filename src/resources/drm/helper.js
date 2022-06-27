// Source: https://stackoverflow.com/a/53872705
function currentFrameAbsolutePosition() {
    let currentWindow = window;
    let currentParentWindow;
    let positions = [];
    let rect;
    while(currentWindow !== window.top) {
        currentParentWindow = currentWindow.parent;
        for(let idx = 0; idx < currentParentWindow.frames.length; idx++)
            if(currentParentWindow.frames[idx] === currentWindow) {
                for(let frameElement of currentParentWindow.document.getElementsByTagName('iframe')) {
                    if(frameElement.contentWindow === currentWindow) {
                        rect = frameElement.getBoundingClientRect();
                        positions.push({
                            x: rect.x,
                            y: rect.y
                        });
                    }
                }
                currentWindow = currentParentWindow;
                break;
            }
    }
    return positions.reduce((accumulator, currentValue) => {
        return {
            x: accumulator.x + currentValue.x,
            y: accumulator.y + currentValue.y
        };
    }, {
        x: 0,
        y: 0
    });
}